# RAG ETL PDF 文件路径问题修复 - MinIO 文件下载

## 问题发现

**时间**: 2025-12-13 17:06

**症状**:
- ETL 任务创建成功
- 但 Extract 阶段显示"总页数=0"
- 未提取到任何电路图

## 根本原因

**Python 服务尝试直接打开 MinIO 对象路径作为本地文件**：

```python
# extract.py:70
pdf_document = fitz.open(pdf_path)  # 尝试打开本地文件路径
```

但 `pdf_path` 实际是 MinIO 的对象存储路径：
```
rag/b5ccd82c65884f0c8144c7554d576e76/documents/d6f1297fbe5245a3ba33194b9d75edf6/2024-ch3-FETs-Enhance.pdf
```

这不是本地文件系统路径，`fitz.open()` 无法打开，导致页数为 0。

## 架构问题分析

### 当前流程（错误）

1. 前端上传 PDF → MinIO 存储
2. Java 后端发送 MinIO 对象路径到 Python 服务
3. Python 服务尝试直接用 `fitz.open(minio_path)` 打开 ❌
4. 失败：MinIO 路径不是本地文件系统路径

### 两种解决方案对比

#### 方案 A：Java 下载后发送本地路径
- Java 从 MinIO 下载到本地临时目录
- 发送本地文件路径给 Python
- Python 直接处理本地文件
- **缺点**：Java 和 Python 都需要临时存储空间，增加资源消耗

#### 方案 B：Python 自己下载（✅ 采用）
- Java 只发送 MinIO 对象名称
- Python 服务内部从 MinIO 下载到临时目录
- 处理完成后清理临时文件
- **优点**：简单高效，Java 无需处理文件，Python 统一管理临时文件

## 解决方案：Python 内部下载 MinIO 文件

### 修改内容

修改 [orchestrator.py:44-186](../../../python/drawsee-rag-python/app/services/etl/orchestrator.py#L44-L186)，在 ETL 流水线开始时添加 MinIO 文件下载逻辑：

```python
async def run_etl_pipeline(
    self,
    task_id: str,
    pdf_path: str,  # 可以是本地路径或 MinIO 对象名称
    document_id: str,
    knowledge_base_id: str,
    class_id: str,
) -> bool:
    logger.info(f"开始执行ETL流水线: task_id={task_id}, document_id={document_id}")

    # 如果 pdf_path 不是本地文件路径，则从 MinIO 下载
    import os
    import tempfile
    from app.services.storage.minio_client import minio_client

    local_pdf_path = pdf_path
    temp_file = None

    # 检查是否需要从 MinIO 下载（不以 / 开头的路径认为是 MinIO 对象名称）
    if not pdf_path.startswith('/'):
        logger.info(f"从 MinIO 下载文件: {pdf_path}")
        try:
            # 下载到临时文件
            temp_file = tempfile.NamedTemporaryFile(suffix='.pdf', delete=False)
            minio_client.client.fget_object(
                bucket_name=minio_client.bucket_name,
                object_name=pdf_path,
                file_path=temp_file.name
            )
            local_pdf_path = temp_file.name
            logger.info(f"文件下载成功: {local_pdf_path}")
        except Exception as e:
            logger.error(f"从 MinIO 下载文件失败: {e}")
            if temp_file:
                temp_file.close()
                if os.path.exists(temp_file.name):
                    os.unlink(temp_file.name)
            await circuit_repo.update_etl_status(
                task_id,
                ETLStatus.FAILED,
                failure_reason=f"从 MinIO 下载文件失败: {str(e)}"
            )
            return False

    try:
        # ==================== Extract阶段 ====================
        logger.info(f"[ETL] Extract阶段开始: task_id={task_id}")
        await circuit_repo.update_etl_status(task_id, ETLStatus.EXTRACTING)

        extract_start = time.time()
        extract_result = await extract_service.extract_and_store(
            pdf_path=local_pdf_path,  # 使用本地路径
            document_id=document_id,
            knowledge_base_id=knowledge_base_id,
        )
        # ... Transform 和 Load 阶段 ...

        return True

    except Exception as e:
        logger.error(f"[ETL] 流水线执行失败: task_id={task_id}, error={e}")
        await circuit_repo.update_etl_status(
            task_id,
            ETLStatus.FAILED,
            failure_reason=str(e),
            error_details={"exception": str(e)},
        )
        return False

    finally:
        # 清理临时文件
        if temp_file and os.path.exists(temp_file.name):
            try:
                os.unlink(temp_file.name)
                logger.info(f"临时文件已清理: {temp_file.name}")
            except Exception as e:
                logger.warning(f"清理临时文件失败: {e}")
```

### 关键设计点

1. **智能路径检测**：
   ```python
   if not pdf_path.startswith('/'):  # 不以 / 开头 → MinIO 对象名称
       # 从 MinIO 下载
   else:
       # 直接使用本地路径（兼容性）
   ```

2. **临时文件管理**：
   - 使用 `tempfile.NamedTemporaryFile(delete=False)` 创建临时文件
   - 下载完成后保留文件（`delete=False`）供处理使用
   - 在 `finally` 块中确保清理

3. **错误处理**：
   - 下载失败时立即更新 ETL 任务状态为 FAILED
   - 记录详细的失败原因
   - 清理已创建的临时文件

4. **资源清理保证**：
   - 使用 `finally` 块确保临时文件一定被清理
   - 即使处理失败或异常，临时文件也会被删除
   - 避免磁盘空间泄漏

## 测试验证

### 预期流程

1. ✅ 前端上传 PDF → MinIO (object_name: `rag/kb_id/documents/doc_id/filename.pdf`)
2. ✅ 前端调用 Java `/api/rag/documents/ingest`，传递 MinIO object_name
3. ✅ Java 调用 Python `/api/v1/documents/ingest`，传递 MinIO object_name
4. ✅ Python ETL orchestrator 检测到非本地路径
5. ✅ 从 MinIO 下载文件到临时目录 `/tmp/tmp12345.pdf`
6. ✅ Extract 阶段使用本地临时文件进行处理
7. ✅ Transform 和 Load 阶段正常执行
8. ✅ 流水线完成后清理临时文件

### 预期日志

**Python 服务（成功场景）**:
```
INFO - 开始执行ETL流水线: task_id=xxx, document_id=xxx
INFO - 从 MinIO 下载文件: rag/kb_id/documents/doc_id/filename.pdf
INFO - 文件下载成功: /tmp/tmp12345.pdf
INFO - [ETL] Extract阶段开始: task_id=xxx
INFO - 开始提取PDF电路图: /tmp/tmp12345.pdf, 总页数=34
INFO - PDF电路图提取完成: 共提取 15 张电路图
INFO - [ETL] Transform阶段开始: task_id=xxx
...
INFO - [ETL] 流水线执行完成: task_id=xxx, 总耗时=120.5s
INFO - 临时文件已清理: /tmp/tmp12345.pdf
```

**Python 服务（下载失败场景）**:
```
INFO - 从 MinIO 下载文件: rag/kb_id/documents/doc_id/filename.pdf
ERROR - 从 MinIO 下载文件失败: S3 error: Object not found
INFO - ETL任务状态已更新: xxx -> failed
```

## 技术要点

### 1. MinIO Python SDK 文件下载

```python
from minio import Minio

client = Minio(
    endpoint="117.72.9.87:9046",
    access_key="xxx",
    secret_key="xxx",
    secure=False
)

# 下载对象到本地文件
client.fget_object(
    bucket_name="drawsee",
    object_name="rag/kb_id/documents/doc_id/file.pdf",
    file_path="/tmp/local_file.pdf"
)
```

### 2. Python 临时文件最佳实践

```python
import tempfile
import os

# 创建临时文件（不自动删除）
temp_file = tempfile.NamedTemporaryFile(suffix='.pdf', delete=False)
temp_path = temp_file.name

try:
    # 使用临时文件
    process_file(temp_path)
finally:
    # 确保清理
    if os.path.exists(temp_path):
        os.unlink(temp_path)
```

**为什么使用 `delete=False`？**
- `delete=True`（默认）：文件关闭时自动删除
- `delete=False`：文件关闭后保留，手动控制删除时机
- ETL 流水线需要文件在多个阶段之间保持存在

### 3. 异常安全的资源清理

```python
temp_file = None
try:
    temp_file = tempfile.NamedTemporaryFile(suffix='.pdf', delete=False)
    # 处理文件
    process(temp_file.name)
except Exception as e:
    logger.error(f"处理失败: {e}")
    raise
finally:
    # finally 块确保无论是否异常都会执行
    if temp_file and os.path.exists(temp_file.name):
        try:
            os.unlink(temp_file.name)
            logger.info("临时文件已清理")
        except Exception as e:
            logger.warning(f"清理失败: {e}")  # 不抛出异常
```

### 4. 路径类型检测策略

**检测 MinIO 对象名称 vs 本地路径**：

| 路径示例 | 类型 | 检测方法 |
|---------|------|---------|
| `rag/kb/docs/file.pdf` | MinIO 对象名称 | 不以 `/` 开头 |
| `/tmp/file.pdf` | 绝对本地路径 | 以 `/` 开头 |
| `/home/user/file.pdf` | 绝对本地路径 | 以 `/` 开头 |

```python
def is_minio_path(path: str) -> bool:
    """判断是否为 MinIO 对象名称"""
    return not path.startswith('/')
```

### 5. 磁盘空间管理

**临时文件清理的重要性**：
- PDF 文件通常较大（3-10 MB）
- ETL 任务可能并发执行多个
- 不清理会快速填满 `/tmp` 分区

**最佳实践**：
- 使用 `finally` 块确保清理
- 记录清理日志便于调试
- 清理失败只记录警告，不影响任务状态

## 前端调用示例

### 当前调用方式（需要调整）

前端上传完成后，需要获取 MinIO 对象名称（不是 URL）：

```javascript
// 上传文件到 MinIO（通过 Java 后端）
const uploadResponse = await uploadFile(file);

// 获取 MinIO 对象名称
const minioObjectName = uploadResponse.storage_object;  // 例如: rag/kb_id/documents/doc_id/file.pdf

// 触发 ETL 入库
await fetch('/api/rag/documents/ingest', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({
        document_id: docId,
        knowledge_base_id: kbId,
        class_id: classId,
        user_id: userId,
        pdf_path: minioObjectName  // 传递 MinIO 对象名称，不是 URL
    })
});
```

## 相关文件

### Python RAG 服务
- [orchestrator.py:44-186](../../../python/drawsee-rag-python/app/services/etl/orchestrator.py#L44-L186) - ETL 流水线主流程，添加 MinIO 下载逻辑
- [extract.py:70](../../../python/drawsee-rag-python/app/services/etl/extract.py#L70) - PDF 电路图提取，使用本地文件路径
- [minio_client.py](../../../python/drawsee-rag-python/app/services/storage/minio_client.py) - MinIO 客户端封装

### Java 后端
- [RagController.java:36-94](../src/main/java/cn/yifan/drawsee/controller/RagController.java#L36-L94) - RAG 文档入库 API
- [PythonRagService.java:145-181](../src/main/java/cn/yifan/drawsee/service/base/PythonRagService.java#L145-L181) - Python 服务调用封装

### 数据库
- `knowledge_document` 表的 `storage_object` 字段存储 MinIO 对象名称

## 状态

✅ **修复已完成**
- Python orchestrator 已添加 MinIO 文件下载逻辑
- 支持智能路径检测（MinIO 对象名称 vs 本地路径）
- 实现了异常安全的临时文件清理
- Python 服务已自动重新加载（Process 389183）

**下一步**: 用户重新测试 PDF 文档上传，验证完整的 ETL 流程。

## 经验教训

1. **对象存储 vs 本地文件系统**：
   - 对象存储路径不能直接作为本地文件路径使用
   - 需要先下载到本地，再进行文件操作

2. **微服务文件处理模式**：
   - **推荐**：接收对象存储路径，服务内部下载
   - **不推荐**：服务间传递本地文件路径（路径可能无效）

3. **临时文件管理**：
   - 使用 `tempfile` 模块创建临时文件
   - `finally` 块确保资源清理
   - 避免磁盘空间泄漏

4. **路径约定**：
   - 使用路径前缀区分不同类型（MinIO 对象名称不以 `/` 开头）
   - 兼容本地路径（以 `/` 开头）
   - 便于未来扩展（支持 S3、阿里云 OSS 等）

5. **异常处理层次**：
   - 下载失败：立即更新任务状态，不继续处理
   - 清理失败：仅记录警告，不影响任务最终状态

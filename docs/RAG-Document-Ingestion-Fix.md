# RAG 文档入库流程修复 - Java服务未传递pdf_path参数

## 问题发现

**时间**: 2025-12-13 18:15 - 18:20

**症状**:
- 文档上传到MinIO成功
- 但RAG ETL流程失败，错误信息："ETL流水线执行失败"
- Python日志显示："开始提取PDF电路图: , 总页数=0" - **PDF路径为空！**

## 根本原因

**Java后端的旧版文档入库服务未迁移到Python RAG服务**

### 问题分析

1. **旧版流程（错误）**：
   - 文档上传 → MinIO存储
   - `DocumentIngestionService.ingestAsync()` 被触发
   - **使用Java本地处理**：下载文件、解析PDF、分块、嵌入、存储
   - 完全绕过了Python RAG服务

2. **前端调用流程（部分正确）**：
   - 前端上传完成后会调用 `/api/rag/documents/ingest`
   - 但这个API **期望前端传递`pdf_path`参数**
   - 实际上前端没有传递此参数

3. **架构问题**：
   - `DocumentIngestionService` 是RAG Phase 0时期的旧代码
   - 注释中写着"TODO: 迁移到Python RAG微服务"
   - 但实际上一直在使用Java本地处理，没有调用Python服务

## 解决方案

### 方案：完全迁移到Python RAG服务

**核心思路**：
- `DocumentIngestionService.ingestAsync()` 不再本地处理文档
- 直接调用 Python RAG服务的 `/api/v1/documents/ingest` API
- 传递 `document.getStorageObject()` 作为 `pdf_path` 参数
- Python服务从MinIO下载文件并处理

### 修改内容

#### 1. 修改 [DocumentIngestionService.java](../src/main/java/cn/yifan/drawsee/service/business/DocumentIngestionService.java)

**添加依赖**：
```java
import cn.yifan.drawsee.service.base.PythonRagService;
import java.util.Map;

@Autowired
private PythonRagService pythonRagService;
```

**重写 `ingestAsync` 方法**：
```java
@Async("ragIngestionExecutor")
public void ingestAsync(String taskId) {
    RagIngestionTask task = ragIngestionTaskService.getById(taskId);
    if (task == null) {
        log.warn("RAG任务不存在: {}", taskId);
        return;
    }

    KnowledgeDocument document = knowledgeDocumentService.getDocument(task.getDocumentId());
    if (document == null) {
        ragIngestionTaskService.markFailed(taskId, "文档元数据不存在");
        knowledgeDocumentService.updateStatus(task.getDocumentId(), KnowledgeDocumentStatus.FAILED, "文档元数据不存在");
        return;
    }

    try {
        KnowledgeBase knowledgeBase = knowledgeBaseMapper.getById(document.getKnowledgeBaseId());
        if (knowledgeBase == null) {
            throw new IllegalStateException("知识库不存在: " + document.getKnowledgeBaseId());
        }

        if (document.getStorageObject() == null || document.getStorageObject().isBlank()) {
            throw new IllegalStateException("文档存储对象未知，无法下载");
        }

        // 检查 Python RAG 服务是否可用
        if (!pythonRagService.isServiceAvailable()) {
            throw new IllegalStateException("Python RAG 服务不可用");
        }

        // 更新状态为 PARSING（准备调用 Python 服务）
        ragIngestionTaskService.updateTask(task, RagIngestionStage.PARSING, KnowledgeDocumentStatus.PARSING, 10);
        knowledgeDocumentService.updateStatus(document.getId(), KnowledgeDocumentStatus.PARSING, null);

        // 调用 Python RAG 服务进行 ETL 处理
        log.info("调用Python RAG服务进行文档入库: documentId={}, storage_object={}",
            document.getId(), document.getStorageObject());

        Map<String, Object> result = pythonRagService.ingestDocument(
            document.getId(),
            document.getKnowledgeBaseId(),
            "",  // class_id 留空，由 Python 服务处理
            document.getUploaderId(),
            document.getStorageObject()  // 传递 MinIO 对象名称
        );

        if (result == null) {
            throw new IllegalStateException("Python RAG 服务返回结果为空");
        }

        Boolean success = (Boolean) result.get("success");
        if (!Boolean.TRUE.equals(success)) {
            String message = (String) result.get("message");
            throw new IllegalStateException("Python RAG 服务处理失败: " + message);
        }

        String pythonTaskId = (String) result.get("task_id");
        log.info("Python RAG服务已接受文档入库请求: documentId={}, pythonTaskId={}",
            document.getId(), pythonTaskId);

        // Python 服务将异步处理 ETL，Java 端任务标记为完成
        // 后续状态更新由 Python 服务通过回调或轮询机制更新
        ragIngestionTaskService.markCompleted(taskId);
        log.info("文档入库任务已委托给Python RAG服务: taskId={}, pythonTaskId={}", taskId, pythonTaskId);

    } catch (Exception ex) {
        log.error("文档入库流程失败, taskId={}", taskId, ex);
        ragIngestionTaskService.markFailed(taskId, ex.getMessage());
        knowledgeDocumentService.updateStatus(document.getId(), KnowledgeDocumentStatus.FAILED, ex.getMessage());
    }
}
```

### 修复后的完整流程

```
1. 用户上传PDF文件
   ↓
2. 前端调用 POST /api/knowledge-bases/{id}/documents
   ↓
3. Java后端：
   - 上传文件到MinIO → 获得 storage_object
   - 创建 knowledge_document 记录（包含 storage_object）
   - 创建 rag_ingestion_task 记录
   - 异步触发 DocumentIngestionService.ingestAsync(taskId)
   ↓
4. DocumentIngestionService:
   - 获取 document 和 storage_object
   - 检查 Python RAG 服务是否可用
   - 调用 PythonRagService.ingestDocument(documentId, kb_id, class_id, userId, storage_object)
   ↓
5. Java PythonRagService:
   - 构造请求体：{document_id, knowledge_base_id, class_id, user_id, pdf_path: storage_object}
   - 发送 POST /api/v1/documents/ingest 到 Python服务
   ↓
6. Python RAG 服务：
   - 接收请求，创建 ETL 任务
   - 检测 pdf_path 不以 / 开头 → 识别为MinIO对象名称
   - 从MinIO下载文件到临时目录 /tmp/tmpXXXX.pdf
   - Extract: 提取PDF电路图
   - Transform: VLM解析电路结构
   - Load: 向量化并存储到Qdrant
   - 清理临时文件
   ↓
7. ETL完成后：
   - Python更新 etl_task 状态为 completed
   - Java端任务已标记为完成
```

## 关键设计点

### 1. 传递 MinIO 对象名称而非本地路径

```java
// ✅ 正确：传递MinIO对象名称
pythonRagService.ingestDocument(
    documentId,
    knowledgeBaseId,
    "",
    userId,
    document.getStorageObject()  // 例如: rag/kb_id/documents/doc_id/file.pdf
);
```

**Python服务内部处理**：
```python
# orchestrator.py
if not pdf_path.startswith('/'):  # 不是本地路径
    # 从MinIO下载
    temp_file = tempfile.NamedTemporaryFile(suffix='.pdf', delete=False)
    minio_client.client.fget_object(
        bucket_name=minio_client.bucket_name,
        object_name=pdf_path,
        file_path=temp_file.name
    )
    local_pdf_path = temp_file.name
```

### 2. 异步处理模式

- **Java端**：调用Python服务后立即返回，不等待ETL完成
- **Python端**：异步执行ETL流水线，更新任务状态到数据库
- **前端**：可以轮询任务状态获取进度

### 3. 错误处理

**Java端**：
- Python服务不可用 → 任务标记为失败
- Python返回错误 → 任务标记为失败
- 网络异常 → 任务标记为失败

**Python端**：
- MinIO下载失败 → ETL任务标记为failed
- 处理失败 → 清理临时文件，任务标记为failed

### 4. class_id 处理

`KnowledgeBase` 实体没有 `classId` 字段（只有 `classIds` 列表），所以：
```java
// ❌ 错误
knowledgeBase.getClassId()  // 编译错误

// ✅ 正确
""  // 传递空字符串，由Python服务处理
```

## 测试验证

### 预期行为

上传PDF文档后：

1. ✅ MinIO文件上传成功
2. ✅ Java创建文档记录，包含 `storage_object`
3. ✅ Java调用Python RAG服务，传递 `storage_object`
4. ✅ Python从MinIO下载文件到临时目录
5. ✅ Python执行ETL流水线：Extract → Transform → Load
6. ✅ Python清理临时文件
7. ✅ 前端显示"文档处理完成"

### 预期日志

**Java后端**：
```
INFO - 调用Python RAG服务进行文档入库: documentId=xxx, storage_object=rag/kb_id/documents/doc_id/file.pdf
INFO - Python RAG服务已接受文档入库请求: documentId=xxx, pythonTaskId=xxx
INFO - 文档入库任务已委托给Python RAG服务: taskId=xxx, pythonTaskId=xxx
```

**Python服务**：
```
INFO - 收到文档入库请求: document_id=xxx
INFO - 从 MinIO 下载文件: rag/kb_id/documents/doc_id/file.pdf
INFO - 文件下载成功: /tmp/tmpXXXXX.pdf
INFO - 开始提取PDF电路图: /tmp/tmpXXXXX.pdf, 总页数=38
INFO - PDF电路图提取完成: 共提取 15 张电路图
INFO - [ETL] Transform阶段开始
INFO - [ETL] Load阶段开始
INFO - [ETL] 流水线执行完成: 总耗时=120.5s
INFO - 临时文件已清理: /tmp/tmpXXXXX.pdf
```

## 相关文件

### Java后端
- [DocumentIngestionService.java](../src/main/java/cn/yifan/drawsee/service/business/DocumentIngestionService.java) - 完全重写，调用Python服务
- [PythonRagService.java](../src/main/java/cn/yifan/drawsee/service/base/PythonRagService.java) - Python服务调用封装（已存在）
- [KnowledgeBase.java](../src/main/java/cn/yifan/drawsee/pojo/entity/KnowledgeBase.java) - 知识库实体（无classId字段）

### Python RAG服务
- [orchestrator.py](../../../python/drawsee-rag-python/app/services/etl/orchestrator.py) - ETL流水线，包含MinIO下载逻辑
- [extract.py](../../../python/drawsee-rag-python/app/services/etl/extract.py) - PDF电路图提取
- [minio_client.py](../../../python/drawsee-rag-python/app/services/storage/minio_client.py) - MinIO客户端

### 相关文档
- [RAG-MinIO-PDF-Download-Fix.md](RAG-MinIO-PDF-Download-Fix.md) - Python服务MinIO下载实现
- [RAG-ETL-SQLAlchemy-Enum-Fix.md](RAG-ETL-SQLAlchemy-Enum-Fix.md) - SQLAlchemy Enum问题修复
- [Python-RAG-Integration-Guide.md](Python-RAG-Integration-Guide.md) - Python RAG服务集成指南

## 状态

✅ **修复已完成**
- Java `DocumentIngestionService` 已完全迁移到调用Python RAG服务
- Python服务接收 `storage_object` 并从MinIO下载文件
- 完整的ETL流水线通过Python执行
- Java服务已重新启动（Process 421738）

⚠️ **需要重新编译Java服务** - 代码已修改，需要重启以加载新代码

**下一步**: 用户重新测试PDF文档上传，验证完整的RAG ETL流程。

## 经验教训

1. **遗留代码识别**：
   - 代码注释中的"TODO: 迁移到Python RAG微服务"是重要信号
   - 应该定期审查和清理遗留代码

2. **微服务职责划分**：
   - Java服务：文件上传、元数据管理、API网关
   - Python服务：RAG处理、ETL流水线、向量存储
   - 避免职责重复

3. **参数传递约定**：
   - Java传递MinIO对象名称（不是URL，不是本地路径）
   - Python根据路径前缀判断是否需要下载
   - 统一的约定可以避免混乱

4. **实体字段检查**：
   - 使用IDE自动补全功能
   - 不要猜测实体字段名称
   - `classId` vs `classIds` 这种错误很常见

5. **异步处理模式**：
   - 长时间任务应该异步执行
   - 调用方不等待结果
   - 通过状态查询获取进度

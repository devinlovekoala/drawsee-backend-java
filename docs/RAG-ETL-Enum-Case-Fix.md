# RAG ETL 任务创建失败 - ENUM 大小写不匹配修复

## 问题发现

**时间**: 2025-12-13 16:49

**症状**:
- 前端显示"文档上传成功"
- 但后续没有任何RAG处理效果
- 文档状态停留在 UPLOADED

## 日志分析

### Java 后端日志
```
文档入库触发成功: taskId=null, status=failed
```

虽然 Java 端显示"成功"，但实际上 Python 返回的 status 是 **"failed"**。

### Python RAG 服务日志
```
ERROR - MySQL会话错误: 'pending' is not among the defined enum values.
Enum name: etlstatus.
Possible values: PENDING, EXTRACTING, TRANSFORMIN.., ..., FAILED

ERROR - 创建ETL任务失败 [cd959658-d179-4e40-a84a-f2a311f5c808]:
'pending' is not among the defined enum values.

ERROR - ETL任务创建失败: document_id=704be091cc4b4c35b543fb140b888cde
```

## 根本原因

**Python 代码中的 ENUM 值与 MySQL 数据库定义不匹配**：

### Python 代码（错误）
```python
class ETLStatus(str, enum.Enum):
    """ETL任务状态"""
    PENDING = "pending"          # ❌ 小写
    EXTRACTING = "extracting"    # ❌ 小写
    TRANSFORMING = "transforming"# ❌ 小写
    LOADING = "loading"          # ❌ 小写
    COMPLETED = "completed"      # ❌ 小写
    FAILED = "failed"            # ❌ 小写
```

### MySQL 数据库定义（正确）
```sql
CREATE TABLE etl_task (
    ...
    status ENUM('PENDING', 'EXTRACTING', 'TRANSFORMING', 'LOADING', 'COMPLETED', 'FAILED')
    ...
);
```

当 Python 尝试将小写的 `"pending"` 插入到数据库时，MySQL 拒绝了这个值，因为 ENUM 定义只接受大写的 `"PENDING"`。

## 解决方案

修改 [circuit.py](../../../python/drawsee-rag-python/app/models/circuit.py#L27-L34)，将所有枚举值改为大写：

```python
class ETLStatus(str, enum.Enum):
    """ETL任务状态"""
    PENDING = "PENDING"              # ✅ 大写
    EXTRACTING = "EXTRACTING"        # ✅ 大写
    TRANSFORMING = "TRANSFORMING"    # ✅ 大写
    LOADING = "LOADING"              # ✅ 大写
    COMPLETED = "COMPLETED"          # ✅ 大写
    FAILED = "FAILED"                # ✅ 大写
```

### 自动重新加载

由于 Python 服务使用 `uvicorn --reload` 启动，它会自动检测文件变化并重新加载：

```
WARNING:  WatchFiles detected changes in 'app/models/circuit.py'. Reloading...
INFO:     Shutting down
...
✅ Drawsee RAG Service started successfully
```

**不需要手动重启 Python 服务**！

## 技术要点

### 1. MySQL ENUM 大小写敏感

MySQL 的 ENUM 类型是**大小写敏感**的（在严格模式下）：

```sql
-- 数据库定义
status ENUM('PENDING', 'EXTRACTING', 'LOADING')

-- 合法插入
INSERT INTO table VALUES ('PENDING');  -- ✅ 成功

-- 非法插入（严格模式）
INSERT INTO table VALUES ('pending');  -- ❌ 错误
INSERT INTO table VALUES ('Pending');  -- ❌ 错误
```

### 2. Python Enum 最佳实践

对于与数据库 ENUM 交互的 Python 枚举，应该：

```python
# ✅ 推荐：枚举值与数据库定义完全一致
class Status(str, enum.Enum):
    PENDING = "PENDING"
    ACTIVE = "ACTIVE"

# ❌ 不推荐：枚举值大小写不一致
class Status(str, enum.Enum):
    PENDING = "pending"  # 数据库是 PENDING
```

### 3. SQLAlchemy Enum 映射

SQLAlchemy 的 `Enum` 类型会直接使用 Python 枚举的值：

```python
from sqlalchemy import Column, Enum

class ETLTask(Base):
    status = Column(Enum(ETLStatus), default=ETLStatus.PENDING)

# 当 ETLStatus.PENDING = "pending" 时，插入 "pending"
# 当 ETLStatus.PENDING = "PENDING" 时，插入 "PENDING"
```

### 4. uvicorn --reload 热重载

开发环境使用 `--reload` 可以自动重新加载代码：

```bash
uvicorn app.main:app --reload
```

**监控文件变化**:
- 监控 `.py` 文件的修改
- 自动关闭旧进程
- 重新启动新进程
- 重新初始化所有连接

**生产环境**不应该使用 `--reload`，应该使用进程管理器（如 systemd、supervisord）。

## 测试验证

### 预期行为

修复后，再次上传 PDF 文档应该：

1. ✅ 文件上传到 MinIO
2. ✅ Java 调用 Python RAG 服务 `/api/v1/documents/ingest`
3. ✅ Python 创建 ETL 任务（status = "PENDING"）
4. ✅ ETL 任务状态流转：
   - PENDING → EXTRACTING → TRANSFORMING → LOADING → COMPLETED
5. ✅ 前端显示"文档 RAG 处理完成"

### 预期日志

**Python 服务**:
```
INFO - 收到文档入库请求: document_id=xxx
INFO - ETL任务已创建: xxx
INFO - ETL任务状态已更新: xxx -> EXTRACTING
INFO - 文档解析完成: 共 38 页
INFO - ETL任务状态已更新: xxx -> TRANSFORMING
INFO - 文本分块完成: 共 120 块
INFO - ETL任务状态已更新: xxx -> LOADING
INFO - 向量存储完成: 120 vectors
INFO - ETL任务状态已更新: xxx -> COMPLETED
```

**Java 后端**:
```
INFO - 触发Python文档入库: 文档xxx, 知识库xxx
INFO - 文档入库触发成功: task_id=xxx, status=success
INFO - 文档状态更新: PARSING -> CHUNKING -> COMPLETED
```

## 相关文件

### Python RAG 服务
- [circuit.py](../../../python/drawsee-rag-python/app/models/circuit.py#L27-L34) - ETLStatus 枚举定义
- [mysql_repository.py](../../../python/drawsee-rag-python/app/services/storage/mysql_repository.py#L230-L242) - ETL 任务创建
- [orchestrator.py](../../../python/drawsee-rag-python/app/services/etl/orchestrator.py) - ETL 编排器

### Java 后端
- [PythonRagService.java](../src/main/java/cn/yifan/drawsee/service/base/PythonRagService.java) - Python 服务调用
- [DocumentIngestionService.java](../src/main/java/cn/yifan/drawsee/service/business/DocumentIngestionService.java) - 文档摄取服务

### 数据库表
```sql
-- ETL 任务表
CREATE TABLE etl_task (
    id VARCHAR(64) PRIMARY KEY,
    document_id VARCHAR(64) NOT NULL,
    knowledge_base_id VARCHAR(64) NOT NULL,
    status ENUM('PENDING', 'EXTRACTING', 'TRANSFORMING', 'LOADING', 'COMPLETED', 'FAILED'),
    ...
);
```

## 状态

✅ **问题已修复**
- Python ETLStatus 枚举值已改为大写
- Python 服务已自动重新加载（uvicorn --reload）
- 两个服务都在正常运行

**下一步**: 用户重新测试 PDF 文档上传，验证完整的 RAG ETL 流程。

# RAG 重复入库流程修复 - 删除冗余RagController

## 问题发现

**时间**: 2025-12-13 18:24

**症状**:
- ETL流水线执行失败
- Python日志显示：`开始提取PDF电路图: , 总页数=0` - **PDF路径为空！**

## 根本原因

系统存在**两条文档入库流程**，导致了架构混乱：

### 流程1：DocumentIngestionService（正确）✅

```
文档上传 → MinIO存储 → 创建knowledge_document记录
  ↓
触发 DocumentIngestionService.ingestAsync()
  ↓
调用 PythonRagService.ingestDocument(storage_object)
  ↓
Python ETL自动处理（Extract → Transform → Load）
```

**状态**: 工作正常，Java日志确认正确传递参数：
```
INFO - 调用Python RAG服务进行文档入库: documentId=1a2214c083d74977b365df3ddccbeb44,
       storage_object=rag/b5ccd82c65884f0c8144c7554d576e76/documents/1a2214c083d74977b365df3ddccbeb44/2024-ch2-BJTs.pdf
```

### 流程2：RagController（错误，已删除）❌

```
前端 → POST /api/rag/documents/ingest → RagController
  ↓
期望前端传递 pdf_path 参数
  ↓
但前端没有传递此参数！
  ↓
pdf_path = null → Python收到空字符串 → ETL失败
```

**问题**:
1. `RagController.ingestDocument()` 是RAG Phase 0遗留代码
2. 前端错误地调用了这个过时的API
3. 该API期望前端传递 `pdf_path`，但前端无法知道MinIO对象名称
4. 导致Python服务收到空的pdf_path，无法提取电路图

## 解决方案

### 删除冗余的RagController

**文件已删除**: [RagController.java](../src/main/java/cn/yifan/drawsee/controller/RagController.java)

**删除原因**:
1. `DocumentIngestionService` 已经在文档上传后**自动触发**ETL流程
2. 前端不需要手动调用文档入库API
3. 保留RagController只会造成混乱和错误

**删除的API端点**:
- `POST /api/rag/documents/ingest` - 文档入库（冗余）
- `GET /api/rag/tasks/{taskId}` - 任务状态查询（冗余）
- `GET /api/rag/health` - Python服务健康检查（可通过其他方式实现）

## 修复后的正确流程

### 完整的文档上传和ETL流程

```
1. 用户上传PDF文件
   ↓
2. 前端调用 POST /api/knowledge-bases/{id}/documents
   ↓
3. Java后端 KnowledgeDocumentController:
   - 上传文件到MinIO → 获得 storage_object
   - 创建 knowledge_document 记录（status=UPLOADED）
   - 创建 rag_ingestion_task 记录（status=PENDING）
   - 异步触发 DocumentIngestionService.ingestAsync(taskId)
   - 立即返回给前端（不等待ETL完成）
   ↓
4. DocumentIngestionService.ingestAsync():
   - 获取 document 和 storage_object
   - 检查 Python RAG 服务是否可用
   - 更新状态为 PARSING
   - 调用 PythonRagService.ingestDocument()，传递 storage_object
   - 标记 Java 端任务为 COMPLETED
   ↓
5. PythonRagService（Java端）:
   - 构造请求体：{document_id, knowledge_base_id, class_id, user_id, pdf_path: storage_object}
   - 发送 POST /api/v1/documents/ingest 到 Python服务
   ↓
6. Python RAG 服务（orchestrator.py）:
   - 接收请求，创建 ETL 任务
   - 检测 pdf_path 不以 / 开头 → 识别为MinIO对象名称
   - 从MinIO下载文件到临时目录 /tmp/tmpXXXX.pdf
   - Extract: 提取PDF电路图
   - Transform: VLM解析电路结构
   - Load: 向量化并存储到Qdrant
   - 更新 etl_task 状态为 completed
   - 清理临时文件
   ↓
7. 前端状态轮询:
   - 轮询 GET /api/knowledge-bases/{kb_id}/documents
   - 查看 knowledge_document 的 status 字段
   - 状态变化: UPLOADED → PARSING → COMPLETED
```

## 前端调整

### 无需调整！

前端当前的上传流程已经正确：

```javascript
// 1. 上传文件
const response = await fetch(`/api/knowledge-bases/${knowledgeBaseId}/documents`, {
    method: 'POST',
    body: formData  // multipart/form-data
});

// 2. 获取文档ID
const { document_id } = await response.json();

// 3. 轮询文档状态（如果需要）
const checkStatus = async () => {
    const docs = await fetch(`/api/knowledge-bases/${knowledgeBaseId}/documents`);
    const document = docs.find(d => d.id === document_id);

    if (document.status === 'COMPLETED') {
        console.log('ETL处理完成');
    } else if (document.status === 'FAILED') {
        console.error('ETL处理失败', document.failure_reason);
    } else {
        // UPLOADED, PARSING... 继续轮询
        setTimeout(checkStatus, 2000);
    }
};
checkStatus();
```

**关键点**:
- ✅ 只需调用 `POST /api/knowledge-bases/{id}/documents` 上传文件
- ✅ 后端自动触发ETL流程
- ✅ 通过查询文档列表获取状态更新
- ❌ **不要**调用 `/api/rag/documents/ingest`（已删除）
- ❌ **不要**调用 `/api/rag/tasks/{taskId}`（已删除）

## 数据库状态追踪

### knowledge_document 表的 status 字段

| 状态 | 含义 | 说明 |
|------|------|------|
| `UPLOADED` | 文件已上传到MinIO | 初始状态 |
| `PARSING` | Python正在处理 | ETL流水线运行中 |
| `COMPLETED` | 处理完成 | 电路图已提取、解析、向量化 |
| `FAILED` | 处理失败 | 查看 failure_reason 字段 |

### etl_task 表的 status 字段（Python端）

| 状态 | 含义 |
|------|------|
| `pending` | 等待处理 |
| `extracting` | 正在提取电路图 |
| `transforming` | 正在VLM解析 |
| `loading` | 正在向量化和存储 |
| `completed` | 全部完成 |
| `failed` | 失败 |

## 测试验证

### 预期行为

1. ✅ 前端上传PDF文件
2. ✅ 后端立即返回 `document_id`
3. ✅ DocumentIngestionService 自动触发
4. ✅ Python服务从MinIO下载文件
5. ✅ ETL流水线执行：Extract → Transform → Load
6. ✅ 文档状态更新：UPLOADED → PARSING → COMPLETED
7. ✅ 前端显示"处理完成"

### 预期日志

**Java后端**（DocumentIngestionService）:
```
INFO - 调用Python RAG服务进行文档入库: documentId=xxx, storage_object=rag/kb_id/documents/doc_id/file.pdf
INFO - Python RAG服务已接受文档入库请求: documentId=xxx, pythonTaskId=xxx
INFO - 文档入库任务已委托给Python RAG服务: taskId=xxx, pythonTaskId=xxx
```

**Python服务**:
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

### 已删除
- [RagController.java](../src/main/java/cn/yifan/drawsee/controller/RagController.java) - ❌ 已删除

### Java后端（保留）
- [DocumentIngestionService.java](../src/main/java/cn/yifan/drawsee/service/business/DocumentIngestionService.java) - ✅ 文档入库服务（唯一入口）
- [PythonRagService.java](../src/main/java/cn/yifan/drawsee/service/base/PythonRagService.java) - ✅ Python服务调用封装
- [KnowledgeDocumentController.java](../src/main/java/cn/yifan/drawsee/controller/KnowledgeDocumentController.java) - ✅ 文档上传API

### Python RAG服务
- [orchestrator.py](../../../python/drawsee-rag-python/app/services/etl/orchestrator.py) - ETL流水线编排
- [documents.py](../../../python/drawsee-rag-python/app/api/v1/documents.py) - 文档入库API端点

### 相关文档
- [RAG-Document-Ingestion-Fix.md](RAG-Document-Ingestion-Fix.md) - Java端迁移到Python RAG服务
- [RAG-MinIO-PDF-Download-Fix.md](RAG-MinIO-PDF-Download-Fix.md) - Python服务MinIO下载实现
- [Python-RAG-Integration-Guide.md](Python-RAG-Integration-Guide.md) - Python RAG服务集成指南

## 状态

✅ **修复已完成**
- RagController.java 已删除
- DocumentIngestionService 是唯一的文档入库入口
- 前端无需修改（已经使用正确的API）
- 需要重新编译Java服务以加载代码变更

⚠️ **需要重启Java服务**

**下一步**: 用户重新编译并重启Java服务，然后测试PDF文档上传。

## 经验教训

1. **避免API冗余**：
   - 同一个功能只应有一个API入口
   - 遗留代码应该及时清理，不要保留

2. **前端不应承担业务逻辑**：
   - 前端不应该知道MinIO对象名称
   - 前端不应该手动触发ETL流程
   - 后端应该在文档上传后自动处理

3. **异步处理最佳实践**：
   - 文件上传立即返回，不等待处理完成
   - 后端异步处理耗时任务
   - 前端轮询状态或使用WebSocket实时更新

4. **状态管理**：
   - 使用数据库status字段追踪处理进度
   - 前端通过查询文档列表获取最新状态
   - 避免维护多份状态（Java task + Python task）

5. **清理遗留代码**：
   - Phase 0遗留的代码应该在Phase 1完成时删除
   - 不要用注释标记"已废弃"，直接删除
   - 减少代码维护负担和误用风险

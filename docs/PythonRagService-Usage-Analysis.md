# PythonRagService 方法使用情况分析

**日期**: 2025-12-10
**文件**: `src/main/java/cn/yifan/drawsee/service/base/PythonRagService.java`

---

## 📊 方法使用情况统计

### ✅ 已使用的方法

| 方法名 | 调用位置 | 用途 | 状态 |
|--------|---------|------|------|
| `ragQuery()` | `RagQueryService.java:55`<br>`PdfCircuitAnalysisWorkFlow.java:425`<br>`CircuitAnalysisWorkFlow.java:178` | RAG 混合检索 | ✅ 活跃使用 |
| `isServiceAvailable()` | `RagQueryService.java:46` | 健康检查 | ✅ 活跃使用 |
| `healthCheck()` | 通过 `isServiceAvailable()` 间接调用 | 服务可用性检查 | ✅ 活跃使用 |

### ⚠️ 未使用的方法

| 方法名 | 设计用途 | 为何未使用 | 建议 |
|--------|---------|-----------|------|
| `ingestDocument()` | 触发文档入库 ETL 流程 | 文档上传功能尚未完全集成 | 🔄 待集成 |
| `getTaskStatus()` | 查询 ETL 任务状态 | 异步任务监控功能未实现 | 🔄 待集成 |

---

## 🎯 方法设计初衷

### 1. `ragQuery()` - 已使用 ✅

**功能**: RAG 混合检索（向量 + 结构化）

**当前调用场景**:
- **知识问答场景** (`RagQueryService`): 学生提问时增强 LLM 回答
- **PDF 电路分析** (`PdfCircuitAnalysisWorkFlow`): 分析上传的 PDF 电路图
- **画布电路分析** (`CircuitAnalysisWorkFlow`): 分析用户绘制的电路设计

**使用示例**:
```java
// RagQueryService.java
Map<String, Object> pythonResponse = pythonRagService.ragQuery(
    query,
    List.of(knowledgeBaseId),
    classId,
    userId,
    5  // Top-K
);
```

---

### 2. `ingestDocument()` - 未使用 ⚠️

**功能**: 触发文档入库 ETL 流水线

**设计场景**:
- 教师上传电路图 PDF 文档
- 系统解析文档（Extract）
- 提取 BOM、拓扑、图片切片（Transform）
- 存储到 Qdrant + MySQL + MinIO（Load）

**为何未使用**:
1. **文档上传流程未打通**: Java 端文档上传后，未自动触发 Python ETL
2. **替代方案**: 目前可能通过其他方式手动触发 Python 服务入库
3. **功能优先级**: 检索功能优先于入库功能开发

**推荐集成位置**:

#### 方案 1: 在 `KnowledgeDocumentService` 中集成

```java
@Service
public class KnowledgeDocumentService {

    @Autowired
    private PythonRagService pythonRagService;

    /**
     * 上传并入库知识库文档
     */
    public void uploadDocument(MultipartFile file, String knowledgeBaseId, String classId, Long userId) {
        // 1. 上传文件到 MinIO/OSS
        String pdfPath = uploadToStorage(file);

        // 2. 保存文档元数据到 MySQL
        String documentId = saveDocumentMetadata(file.getOriginalFilename(), knowledgeBaseId, classId);

        // 3. 触发 Python ETL 入库（异步）
        if (pythonRagService.isServiceAvailable()) {
            Map<String, Object> etlResult = pythonRagService.ingestDocument(
                documentId,
                knowledgeBaseId,
                classId,
                userId,
                pdfPath
            );

            String taskId = (String) etlResult.get("task_id");
            log.info("文档入库任务已提交: task_id={}", taskId);

            // 可选：轮询任务状态或使用 WebSocket 通知前端
        }
    }
}
```

#### 方案 2: 在 `DocumentController` 中集成

```java
@RestController
@RequestMapping("/api/documents")
public class DocumentController {

    @Autowired
    private PythonRagService pythonRagService;

    @PostMapping("/upload")
    public ResponseEntity<?> uploadDocument(
        @RequestParam("file") MultipartFile file,
        @RequestParam("knowledgeBaseId") String knowledgeBaseId,
        @RequestParam("classId") String classId
    ) {
        // 上传文件
        String pdfPath = saveFile(file);

        // 触发 ETL
        Map<String, Object> result = pythonRagService.ingestDocument(
            UUID.randomUUID().toString(),
            knowledgeBaseId,
            classId,
            getCurrentUserId(),
            pdfPath
        );

        return ResponseEntity.ok(result);
    }
}
```

---

### 3. `getTaskStatus()` - 未使用 ⚠️

**功能**: 查询 ETL 任务执行状态

**设计场景**:
- 前端上传文档后，显示"处理中..."进度条
- 轮询任务状态：PENDING → PROCESSING → SUCCESS/FAILED
- 实时显示 ETL 进度（已解析 X/Y 页）

**为何未使用**:
- 前端未实现异步任务状态轮询
- 后端未暴露任务状态查询接口

**推荐集成**:

```java
@RestController
@RequestMapping("/api/tasks")
public class TaskController {

    @Autowired
    private PythonRagService pythonRagService;

    /**
     * 查询 ETL 任务状态
     */
    @GetMapping("/{taskId}/status")
    public ResponseEntity<?> getTaskStatus(@PathVariable String taskId) {
        Map<String, Object> status = pythonRagService.getTaskStatus(taskId);

        if (status == null) {
            return ResponseEntity.notFound().build();
        }

        return ResponseEntity.ok(status);
    }
}
```

**前端轮询示例**:
```javascript
// 上传文档后获得 task_id
const taskId = response.data.task_id;

// 轮询任务状态
const pollInterval = setInterval(async () => {
    const status = await fetch(`/api/tasks/${taskId}/status`);
    const data = await status.json();

    if (data.status === "SUCCESS") {
        clearInterval(pollInterval);
        showSuccess("文档入库成功！");
    } else if (data.status === "FAILED") {
        clearInterval(pollInterval);
        showError("文档入库失败: " + data.error);
    } else {
        updateProgress(data.progress);
    }
}, 2000);  // 每 2 秒查询一次
```

---

## 🔄 集成优先级建议

### 高优先级 (P0)
- ✅ `ragQuery()` - 已完成，核心功能

### 中优先级 (P1)
- ⚠️ `ingestDocument()` - **建议近期集成**
  - **业务价值**: 允许教师上传电路图文档，自动入库到知识库
  - **用户体验**: 简化文档管理流程
  - **技术难度**: 低（接口已就绪）

  **集成步骤**:
  1. 在 `KnowledgeDocumentController` 添加上传接口
  2. 调用 `pythonRagService.ingestDocument()`
  3. 返回 `task_id` 给前端
  4. 前端显示"上传中"提示

### 低优先级 (P2)
- ⚠️ `getTaskStatus()` - **可选优化**
  - **业务价值**: 提升用户体验，显示处理进度
  - **用户体验**: 避免用户长时间等待无反馈
  - **技术难度**: 低（轮询或 WebSocket）

  **简化方案**:
  - 初期可不实现轮询，直接异步入库
  - 入库完成后发送邮件/站内信通知

---

## 📝 代码优化建议

### 1. 添加 JavaDoc 注释标记未使用方法

```java
/**
 * 触发文档入库流程（ETL流水线）
 *
 * @apiNote 此方法尚未集成到业务流程中，建议在文档上传功能中调用
 * @see KnowledgeDocumentService#uploadDocument
 */
public Map<String, Object> ingestDocument(...) {
    // ...
}
```

### 2. 添加集成示例到方法注释

```java
/**
 * 触发文档入库流程（ETL流水线）
 *
 * <p>示例用法：
 * <pre>{@code
 * // 在文档上传后触发
 * Map<String, Object> result = pythonRagService.ingestDocument(
 *     documentId,
 *     knowledgeBaseId,
 *     classId,
 *     userId,
 *     "/path/to/document.pdf"
 * );
 * String taskId = (String) result.get("task_id");
 * }</pre>
 *
 * @param documentId 文档ID
 * @param knowledgeBaseId 知识库ID
 * @param classId 班级ID
 * @param userId 用户ID
 * @param pdfPath PDF文件路径
 * @return ETL任务信息，包含 task_id、status 等
 */
```

### 3. 创建任务管理服务（推荐）

```java
@Service
public class DocumentIngestionService {

    @Autowired
    private PythonRagService pythonRagService;

    @Autowired
    private DocumentRepository documentRepository;

    /**
     * 提交文档入库任务
     */
    @Async
    public CompletableFuture<String> submitIngestionTask(
        String documentId,
        String knowledgeBaseId,
        String classId,
        Long userId,
        String pdfPath
    ) {
        // 调用 Python 服务
        Map<String, Object> result = pythonRagService.ingestDocument(
            documentId,
            knowledgeBaseId,
            classId,
            userId,
            pdfPath
        );

        String taskId = (String) result.get("task_id");

        // 保存任务ID到数据库
        documentRepository.updateTaskId(documentId, taskId);

        return CompletableFuture.completedFuture(taskId);
    }

    /**
     * 查询任务状态
     */
    public TaskStatus getIngestionStatus(String taskId) {
        Map<String, Object> status = pythonRagService.getTaskStatus(taskId);
        return TaskStatus.from(status);
    }
}
```

---

## 🎯 总结

### 当前状态
- **已使用方法**: 3/5 (60%)
- **核心功能**: ✅ RAG 检索已完全集成
- **待集成功能**: ⚠️ 文档入库 ETL 流程

### 为何有未使用方法
1. **分阶段开发**: 优先实现检索功能，入库功能延后
2. **接口预留**: 提前设计完整 API，方便后续集成
3. **职责分离**: Python 服务提供能力，Java 服务决定何时调用

### 行动建议
1. ✅ **保留未使用方法**: 它们是完整功能的一部分
2. 🔄 **近期集成 `ingestDocument()`**: 完善文档管理功能
3. 📝 **添加 TODO 注释**: 标记集成位置和优先级
4. 📊 **监控使用率**: 定期review代码，清理真正无用的方法

---

## 参考资料

- Python RAG 服务 API 文档: http://localhost:8001/docs
- 文档入库端点: `POST /api/v1/documents/ingest`
- 任务状态端点: `GET /api/v1/documents/tasks/{task_id}`

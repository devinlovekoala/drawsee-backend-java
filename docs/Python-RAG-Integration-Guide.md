# Java后端集成Python RAG微服务完整指南

## 已完成的集成工作

### 1. **PythonRagService HTTP客户端** ✅
[PythonRagService.java](src/main/java/cn/yifan/drawsee/service/base/PythonRagService.java)

**核心方法**:
```java
// RAG混合检索（向量+结构化数据）
public Map<String, Object> ragQuery(String query, List<String> knowledgeBaseIds,
                                     String classId, Long userId, Integer topK)

// 文档入库（触发ETL流水线）
public Map<String, Object> ingestDocument(String documentId, String knowledgeBaseId,
                                          String classId, Long userId, String pdfPath)

// ETL任务状态查询
public Map<String, Object> getTaskStatus(String taskId)

// 健康检查
public boolean healthCheck()
```

**MCP工具语义**: 所有方法失败时返回null（不抛异常），由调用方决定回退策略。

---

### 2. **InternalJwtUtil 共享认证** ✅
[InternalJwtUtil.java](src/main/java/cn/yifan/drawsee/util/InternalJwtUtil.java)

**功能**: 生成JWT Token用于Java→Python服务间认证

```java
String token = internalJwtUtil.generateToken(userId, classId, knowledgeBaseId);
```

---

### 3. **RagQueryService 更新** ✅
[RagQueryService.java](src/main/java/cn/yifan/drawsee/service/business/RagQueryService.java)

**核心改进**:
- 支持多知识库检索
- 解析Python返回的电路结构化数据（BOM + Topology + Caption）
- 组装为LLM可用的上下文格式
- 失败时返回null（MCP语义）

**返回格式**:
```java
RagChatResponseVO {
    answer: "【电路图1】(页码:3, 相似度:0.95)\n这是一个共射极放大电路...\n\n",
    chunks: [
        {
            circuit_id: "...",
            score: 0.95,
            caption: "...",
            bom: [...],      // 元器件清单
            topology: {...}, // 连接关系网表
            page_number: 3,
            image_url: "..."
        }
    ],
    done: true
}
```

---

## 需要完成的集成工作

### 4. **应用配置更新** 🔧

#### application.yaml 配置
```yaml
drawsee:
  # Python RAG微服务配置
  python-service:
    enabled: true
    base-url: http://localhost:8000
    timeout: 60000  # 60秒

  # JWT共享密钥（与Python .env保持一致）
  internal-jwt:
    secret: your-super-secret-key-min-32-chars
    expiration: 3600  # 1小时
```

---

### 5. **工作流集成RAG查询** 🚀

#### 5.1 KnowledgeWorkFlow（知识点分点工作流）

**集成点**: 在智能交互节点生成前调用RAG检索

**伪代码示例**:
```java
// 文件: KnowledgeWorkFlow.java

@Override
protected void generateNodeContent(WorkContext workContext, Node targetNode) {
    String query = extractQueryFromNode(targetNode);
    List<String> knowledgeBaseIds = getClassKnowledgeBaseIds(workContext);
    String classId = getClassIdFromContext(workContext);
    Long userId = workContext.getUserId();

    // 尝试RAG检索
    RagChatResponseVO ragResponse = ragQueryService.query(
        knowledgeBaseIds, query, null, userId, classId
    );

    // 构造Prompt
    StringBuilder prompt = new StringBuilder();
    prompt.append("用户问题: ").append(query).append("\n\n");

    if (ragResponse != null && !ragResponse.getChunks().isEmpty()) {
        // RAG增强：添加检索到的电路知识
        prompt.append("【知识库检索结果】\n");
        prompt.append(ragResponse.getAnswer());
        prompt.append("\n请基于以上电路知识回答用户问题。\n");
    } else {
        // 回退到纯LLM生成（无RAG增强）
        log.info("RAG检索无结果或服务不可用，使用纯LLM生成");
        prompt.append("请基于你的知识回答用户问题。\n");
    }

    // 调用LLM生成
    streamAiService.generate(prompt.toString(), workContext.getHistory(),
                             createStreamHandler(workContext, targetNode));
}
```

---

#### 5.2 PdfCircuitAnalysisWorkFlow（PDF电路分析工作流）

**集成点**: 在PDF实验任务分析点节点内容生成时，注入相关电路知识

**伪代码示例**:
```java
// 文件: PdfCircuitAnalysisWorkFlow.java

/**
 * 为PDF电路分析点注入RAG增强
 */
private String enhanceAnalysisPointWithRAG(WorkContext workContext, String pointTitle) {
    String classId = getClassIdFromContext(workContext);
    Long userId = workContext.getUserId();
    List<String> knowledgeBaseIds = getClassKnowledgeBaseIds(workContext);

    // 构造检索查询（基于分析点标题）
    String query = pointTitle + " 相关电路原理和实验方法";

    // RAG检索
    RagChatResponseVO ragResponse = ragQueryService.query(
        knowledgeBaseIds, query, null, userId, classId
    );

    if (ragResponse != null && !ragResponse.getChunks().isEmpty()) {
        StringBuilder context = new StringBuilder();
        context.append("【相关电路知识】\n");

        // 添加检索到的电路结构化数据
        for (Map<String, Object> chunk : ragResponse.getChunks()) {
            String caption = (String) chunk.get("caption");
            Object pageNum = chunk.get("page_number");

            context.append(String.format("- (页码: %s) %s\n", pageNum, caption));

            // 可选：添加BOM信息
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> bom = (List<Map<String, Object>>) chunk.get("bom");
            if (bom != null && !bom.isEmpty()) {
                context.append("  元器件: ");
                for (Map<String, Object> component : bom) {
                    context.append(String.format("%s(%s) ",
                        component.get("id"), component.get("type")));
                }
                context.append("\n");
            }
        }

        return context.toString();
    }

    return ""; // RAG无结果时返回空字符串
}

/**
 * 生成分析点内容（已增强RAG）
 */
private void createPdfCircuitPointNode(WorkContext workContext, Long parentId,
                                      AiTaskMessage aiTaskMessage,
                                      String title, String description) {
    // RAG增强
    String ragContext = enhanceAnalysisPointWithRAG(workContext, title);

    // 构造完整的节点描述
    String enhancedDescription = description;
    if (!ragContext.isEmpty()) {
        enhancedDescription = description + "\n\n" + ragContext;
    }

    // 创建节点（原有逻辑）
    Map<String, Object> nodeData = new ConcurrentHashMap<>();
    nodeData.put("title", title);
    nodeData.put("text", enhancedDescription);  // 使用增强后的描述
    nodeData.put("subtype", NodeSubType.PDF_CIRCUIT_POINT);

    Node pointNode = new Node(
        NodeType.PDF_CIRCUIT_POINT,
        objectMapper.writeValueAsString(nodeData),
        objectMapper.writeValueAsString(XYPosition.origin()),
        parentId,
        aiTaskMessage.getUserId(),
        aiTaskMessage.getConvId(),
        true
    );

    insertAndPublishNoneStreamNode(workContext, pointNode, nodeData);
}
```

---

### 6. **文档入库集成** 📥

#### DocumentIngestionService 更新

**集成点**: 在文档入库流程中调用Python ETL流水线

```java
// 文件: DocumentIngestionService.java

@Async("ragIngestionExecutor")
public void startIngestion(String taskId, String documentId) {
    try {
        KnowledgeDocument document = knowledgeDocumentService.getById(documentId);
        KnowledgeBase knowledgeBase = knowledgeBaseMapper.selectById(document.getKnowledgeBaseId());

        // 获取班级ID（多租户）
        String classId = getClassIdForDocument(document);
        Long userId = document.getCreatedBy();

        // 获取PDF文件路径（从MinIO下载或使用本地缓存）
        String pdfPath = downloadOrGetPdfPath(document);

        // 调用Python文档入库API（触发ETL流水线）
        log.info("触发Python ETL流水线: documentId={}, knowledgeBaseId={}",
                 documentId, document.getKnowledgeBaseId());

        Map<String, Object> result = pythonRagService.ingestDocument(
            documentId,
            document.getKnowledgeBaseId(),
            classId,
            userId,
            pdfPath
        );

        if (result != null && Boolean.TRUE.equals(result.get("success"))) {
            String etlTaskId = (String) result.get("task_id");
            log.info("Python ETL任务创建成功: task_id={}", etlTaskId);

            // 轮询ETL任务状态（可选）
            pollETLTaskStatus(etlTaskId, taskId, documentId);
        } else {
            log.warn("Python ETL任务创建失败，回退到原有流程");
            // 回退到原有的文档入库流程
            fallbackToOriginalIngestion(taskId, document, knowledgeBase);
        }

    } catch (Exception ex) {
        log.error("文档入库流程失败, taskId={}", taskId, ex);
        ragIngestionTaskService.markFailed(taskId, ex.getMessage());
    }
}

/**
 * 轮询ETL任务状态
 */
private void pollETLTaskStatus(String etlTaskId, String ingestionTaskId, String documentId) {
    int maxRetries = 60;  // 最多轮询60次（10分钟）
    int retryCount = 0;

    while (retryCount < maxRetries) {
        try {
            Thread.sleep(10000);  // 每10秒查询一次

            Map<String, Object> status = pythonRagService.getTaskStatus(etlTaskId);
            if (status == null) {
                log.warn("无法获取ETL任务状态: {}", etlTaskId);
                break;
            }

            String taskStatus = (String) status.get("status");
            Integer totalCircuits = (Integer) status.get("total_circuits");
            Integer processedCircuits = (Integer) status.get("processed_circuits");

            log.info("ETL任务进度: status={}, processed={}/{}",
                     taskStatus, processedCircuits, totalCircuits);

            // 更新入库任务进度
            if (totalCircuits != null && totalCircuits > 0) {
                int progress = (int) ((processedCircuits * 100.0) / totalCircuits);
                ragIngestionTaskService.updateProgress(ingestionTaskId, progress);
            }

            // 检查是否完成
            if ("completed".equals(taskStatus)) {
                log.info("ETL任务完成: total={}, processed={}",
                         totalCircuits, processedCircuits);
                ragIngestionTaskService.markCompleted(ingestionTaskId);
                knowledgeDocumentService.updateStatus(documentId,
                    KnowledgeDocumentStatus.COMPLETED, null);
                break;
            } else if ("failed".equals(taskStatus)) {
                String failureReason = (String) status.get("failure_reason");
                log.error("ETL任务失败: {}", failureReason);
                ragIngestionTaskService.markFailed(ingestionTaskId, failureReason);
                knowledgeDocumentService.updateStatus(documentId,
                    KnowledgeDocumentStatus.FAILED, failureReason);
                break;
            }

            retryCount++;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            break;
        }
    }
}
```

---

## 测试流程

### 1. 启动Python RAG服务
```bash
cd /home/devin/Workspace/python/drawsee-rag-python
python -m app.main
```

### 2. 验证服务健康
```bash
curl http://localhost:8000/health
curl http://localhost:8000/api/v1/rag/health
```

### 3. 测试文档入库
```bash
# Java端触发
POST /api/knowledge/documents/upload
# 观察Python日志中的ETL流水线执行
```

### 4. 测试RAG查询
```bash
# Java端触发智能交互节点生成
# 观察日志：是否调用Python RAG检索
# 观察前端：生成的内容是否包含知识库电路信息
```

---

## 部署清单

### Python服务环境变量 (.env)
```env
# JWT认证（与Java保持一致）
INTERNAL_JWT_SECRET=your-super-secret-key-min-32-chars

# MySQL（与Java共享数据库）
MYSQL_HOST=localhost
MYSQL_PORT=3306
MYSQL_USER=root
MYSQL_PASSWORD=password
MYSQL_DATABASE=drawsee

# Qdrant向量数据库
QDRANT_URL=http://localhost:6333
QDRANT_COLLECTION_NAME=circuit_embeddings

# Doubao Vision API（电路图解析）
DOUBAO_API_KEY=your-doubao-api-key
DOUBAO_MODEL_NAME=doubao-seed-1-6-vision-250815

# Embedding模型
EMBEDDING_MODEL=text-embedding-3-small
EMBEDDING_API_KEY=your-openai-api-key

# MinIO对象存储
MINIO_ENDPOINT=localhost:9000
MINIO_ACCESS_KEY=minioadmin
MINIO_SECRET_KEY=minioadmin
MINIO_BUCKET_NAME=drawsee

# 服务配置
SERVICE_PORT=8000
LOG_LEVEL=INFO
```

### 数据库初始化
```bash
# 执行Python服务的schema.sql创建表
mysql -u root -p drawsee < database/schema.sql
```

---

## 预期效果

### 用户体验提升
1. **知识点分析增强**: 学生提问时，AI回答会引用知识库中的电路图结构化数据（BOM、连接关系）
2. **实验指导精准**: PDF实验任务分析时，AI会基于相关电路原理给出专业指导
3. **电路图可视化**: 前端可以展示检索到的电路图和元器件清单
4. **多租户隔离**: 每个班级只检索自己的知识库内容

### 技术指标
- **检索延迟**: <2秒（Qdrant向量检索 + MySQL查询）
- **准确性**: 基于结构化数据（BOM + Topology）的精确回答
- **可扩展性**: 支持多知识库并行检索
- **容错性**: Python服务不可用时自动回退到纯LLM生成

---

## 故障排查

### Python服务不可用
```java
// 日志：
WARN: Python RAG服务不可用，返回null由调用方回退到纯LLM生成

// 原因：
1. Python服务未启动
2. 网络连接问题
3. JWT认证失败

// 影响：
用户体验：AI回答质量下降（无知识库增强），但功能可用
```

### RAG检索无结果
```java
// 日志：
INFO: RAG检索无结果

// 原因：
1. 知识库为空（未导入文档）
2. 查询与知识库内容不匹配
3. Qdrant向量相似度低于阈值（0.7）

// 影响：
用户体验：回退到纯LLM生成
```

### ETL任务失败
```java
// 日志：
ERROR: ETL任务失败: VLM解析失败

// 原因：
1. PDF文件损坏或格式不支持
2. Doubao Vision API限流
3. MySQL/Qdrant/MinIO连接失败

// 影响：
文档状态：FAILED
用户操作：需重新上传或联系管理员
```

---

## 后续优化建议

1. **异步化ETL**: 使用Celery + Redis任务队列
2. **缓存层**: Redis缓存热门查询结果
3. **监控面板**: Grafana + Prometheus监控ETL和RAG性能
4. **GraphRAG增强**: NetworkX构建电路图，增强连接关系推理
5. **多模态检索**: 支持图片查询（CLIP Embedding）

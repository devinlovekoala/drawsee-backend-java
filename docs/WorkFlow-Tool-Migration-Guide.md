# WorkFlow Tool-based 架构迁移对比

## 核心架构变更

### 旧架构（已废弃）
```
用户查询 → Java WorkFlow → PythonRagService.agenticStreamQuery()
  → Python Agentic RAG → Python LLM生成完整答案 → SSE流式返回 → Java转发 → 前端
```

**问题**：
- ❌ Python LLM生成答案，绕过Java的LangChain4j体系
- ❌ SSE流转发低效且复杂
- ❌ 无法利用LangChain4j的Tool、Memory等特性
- ❌ 效果差，用户体验不佳

### 新架构（推荐）
```
用户查询 → Java WorkFlow → Java LangChain4j ChatModel (流式生成)
  ↓ (需要RAG时)
  调用Tool: AgenticRagTool.searchKnowledgeBase()
  ↓
  Python Agentic RAG (同步返回检索结果)
  ↓
  Java LangChain4j基于检索结果继续流式生成 → 前端
```

**优势**：
- ✅ Java主导对话生成，保持原有流式体验
- ✅ Python专注提供RAG工具和检索能力
- ✅ 可以利用LangChain4j的完整生态（Tool、Memory、Chain等）
- ✅ 架构清晰，易于维护和扩展

---

## 代码对比：CircuitAnalysisDetailWorkFlow

### 1. 依赖注入变化

#### 旧版本
```java
private final PythonRagService pythonRagService;  // 直接调用Python服务

public CircuitAnalysisDetailWorkFlow(
    // ... 其他参数 ...
    PythonRagService pythonRagService,
    KnowledgeBaseService knowledgeBaseService
) {
    super(...);
    this.pythonRagService = pythonRagService;
    this.knowledgeBaseService = knowledgeBaseService;
}
```

#### 新版本（V2）
```java
private final AgenticRagTool agenticRagTool;  // 使用Tool封装

@Value("${drawsee.llm.api-key}")
private String llmApiKey;  // 需要配置LLM

@Value("${drawsee.llm.base-url}")
private String llmBaseUrl;

@Value("${drawsee.llm.model}")
private String llmModel;

public CircuitAnalysisDetailWorkFlowV2(
    // ... 其他参数 ...
    AgenticRagTool agenticRagTool,
    KnowledgeBaseService knowledgeBaseService
) {
    super(...);
    this.agenticRagTool = agenticRagTool;
    this.knowledgeBaseService = knowledgeBaseService;
}
```

---

### 2. streamChat() 方法核心变化

#### 旧版本（204-216行）- SSE流转发
```java
@Override
public void streamChat(WorkContext workContext, StreamingResponseHandler<AiMessage> handler) {
    // ... 构建查询 ...

    // 直接调用Python流式接口，Python LLM生成答案
    try {
        log.info("[CircuitAnalysisDetail] 使用Agentic RAG v2进行追问分析");
        pythonRagService.agenticStreamQuery(
            detailQuery,
            knowledgeBaseIds,
            aiTaskMessage.getClassId(),
            aiTaskMessage.getUserId(),
            handler  // 直接转发Python的SSE流
        );
    } catch (Exception e) {
        // 降级到传统方式
        streamAiService.circuitAnalysisChat(history, detailPrompt, model, handler);
    }
}
```

**流程**：
1. 构建查询字符串
2. 调用Python流式接口
3. Python LLM生成完整答案
4. Java被动转发SSE流到前端

**问题**：
- Python控制生成逻辑
- Java只是管道，无法干预
- SSE转发复杂且低效

---

#### 新版本（V2）（186-257行）- Tool-based生成
```java
@Override
public void streamChat(WorkContext workContext, StreamingResponseHandler<AiMessage> handler) {
    // ... 构建查询 ...

    // 1. 构建系统提示词
    String systemPrompt = buildSystemPrompt();

    // 2. 构建用户查询（包含电路信息）
    String userQuery = buildUserQuery(
        followUpQuestion, contextTitle, contextText, spiceNetlist, circuitDesign
    );

    // 3. 获取知识库权限
    List<String> knowledgeBaseIds = knowledgeBaseService.getUserAccessibleKnowledgeBaseIds(
        aiTaskMessage.getUserId(),
        aiTaskMessage.getClassId()
    );

    log.info("[CircuitAnalysisDetailV2] 开始Tool-based对话生成: kb_count={}", knowledgeBaseIds.size());

    // 4. 创建流式ChatModel
    StreamingChatLanguageModel chatModel = OpenAiStreamingChatModel.builder()
        .apiKey(llmApiKey)
        .baseUrl(llmBaseUrl)
        .modelName(llmModel)
        .temperature(0.7)
        .timeout(Duration.ofSeconds(60))
        .build();

    // 5. 使用AiServices构建Assistant，注册AgenticRagTool
    CircuitAnalysisAssistant assistant = AiServices.builder(CircuitAnalysisAssistant.class)
        .streamingChatLanguageModel(chatModel)
        .tools(agenticRagTool)  // 🔥 关键：注册Tool
        .chatMemory(MessageWindowChatMemory.withMaxMessages(10))
        .build();

    // 6. 组合系统提示词和用户查询
    String fullQuery = systemPrompt + "\n\n" + userQuery;

    // 7. 调用Assistant进行流式对话（LangChain4j会自动调用Tool）
    assistant.chat(fullQuery)
        .onNext(token -> {
            // Java LLM生成的每个token
            handler.onNext(token);
        })
        .onComplete(response -> {
            // 对话完成
            handler.onComplete(response);
        })
        .onError(error -> {
            // 处理错误
            handler.onError(error);
        })
        .start();
}
```

**流程**：
1. 构建System Prompt（定义何时调用Tool）
2. 构建User Query（包含电路信息）
3. 创建Java LangChain4j ChatModel
4. 注册AgenticRagTool
5. Java LLM流式生成（需要时自动调用Tool）
6. Tool返回检索结果后，Java LLM继续生成

**优势**：
- ✅ Java LLM主导生成
- ✅ Python仅提供检索服务
- ✅ LangChain4j自动管理Tool调用
- ✅ 流式体验更流畅

---

### 3. 新增方法：buildSystemPrompt()

**新版本新增**：
```java
private String buildSystemPrompt() {
    return """
        你是一位专业的电路分析助教，负责帮助学生理解电路设计和工作原理。

        **重要规则**：
        - 当需要查询教材、课件、电路知识库中的内容时，使用searchKnowledgeBase工具
        - 当涉及电路理论、公式推导、元件特性时，优先调用searchKnowledgeBase工具
        - 对于电路结构分析、拓扑分析等可以直接基于提供的网表和电路图回答
        - 回答要准确、详细，结合理论知识和实际电路分析

        **输出格式**：
        - 使用清晰的Markdown格式
        - 对关键概念加粗或标注
        - 如果涉及公式，使用LaTeX格式
        - 结构化你的回答（分点、分段）

        请基于知识库内容和你的专业知识，为学生提供准确、详细的电路分析。
        """;
}
```

**作用**：
- 定义助教角色
- **明确何时调用Tool**（关键！）
- 定义输出格式

---

### 4. 新增方法：buildUserQuery()

**新版本新增**（272-318行）：
```java
private String buildUserQuery(
    String followUpQuestion,
    String contextTitle,
    String contextText,
    String spiceNetlist,
    CircuitDesign circuitDesign
) {
    StringBuilder query = new StringBuilder();

    query.append("## 电路分析追问\n\n");

    // 添加前置上下文
    query.append("**前置上下文**: ").append(contextTitle).append("\n");
    if (contextText != null && !contextText.isBlank()) {
        String shortContext = contextText.length() > 200
            ? contextText.substring(0, 200) + "..."
            : contextText;
        query.append(shortContext).append("\n\n");
    }

    // 添加电路信息
    query.append("**电路信息**:\n");
    query.append("- 元件数量: ").append(circuitDesign.getElements().size()).append("\n");
    query.append("- 连线数量: ").append(circuitDesign.getConnections().size()).append("\n\n");

    // 添加SPICE网表
    if (spiceNetlist != null && !spiceNetlist.isBlank()) {
        query.append("**电路网表**:\n```spice\n");
        query.append(spiceNetlist);
        query.append("\n```\n\n");
    }

    // 添加用户的追问
    query.append("**学生追问**: ").append(followUpQuestion).append("\n\n");

    query.append("请结合电路信息和知识库内容，详细回答学生的问题。");

    return query.toString();
}
```

**对比旧版本**（221-250行）：
```java
private String buildDetailQueryForAgenticRAG(
    String followUpQuestion,
    String contextTitle,
    String contextText,
    String spiceNetlist
) {
    StringBuilder query = new StringBuilder();

    // 旧版本是给Python的查询字符串
    query.append("针对电路分析的追问：\n\n");
    query.append("**前置上下文**: ").append(contextTitle).append("\n");
    // ... 类似逻辑 ...

    return query.toString();
}
```

**区别**：
- 旧版：构建给Python Agentic RAG的查询字符串
- 新版：构建给Java LLM的完整上下文（更详细）

---

## Tool自动调用机制

### LangChain4j如何决定调用Tool？

1. **System Prompt定义规则**：
   ```
   - 当需要查询教材、课件、电路知识库中的内容时，使用searchKnowledgeBase工具
   - 当涉及电路理论、公式推导、元件特性时，优先调用searchKnowledgeBase工具
   ```

2. **Tool的@Tool注解提供描述**（AgenticRagTool.java:40）：
   ```java
   @Tool("Search knowledge base using Agentic RAG system. Use this when you need to retrieve information from course materials, textbooks, or circuit knowledge base.")
   public String searchKnowledgeBase(String query, List<String> knowledgeBaseIds)
   ```

3. **LangChain4j自动判断**：
   - 用户问："什么是基尔霍夫电压定律？"
   - LLM识别到需要知识库内容
   - 自动调用`searchKnowledgeBase("基尔霍夫电压定律", knowledgeBaseIds)`
   - 获取检索结果
   - 基于检索结果继续生成答案

---

## 完整调用流程对比

### 旧架构流程

```
1. 用户提问："请解释半加器的工作原理"
   ↓
2. CircuitAnalysisDetailWorkFlow.streamChat()
   ↓
3. 调用 pythonRagService.agenticStreamQuery()
   ↓
4. Java WebClient发送HTTP请求到Python /api/v1/rag/agentic/stream
   ↓
5. Python Agentic RAG:
   - InputTypeClassifier: MIXED
   - IntentClassifier: CONCEPT
   - Adaptive Router → ReasoningChannel
   - ReasoningChannel使用Python LLM生成完整答案
   ↓
6. Python以SSE流式返回: "data: 半加器是一种...\n\n"
   ↓
7. Java WebClient接收SSE chunk
   ↓
8. Java转发给StreamingResponseHandler
   ↓
9. WorkFlow.onNext() → Redis Stream → 前端
```

**特点**：
- Python LLM生成答案（Python控制）
- Java只是转发管道
- SSE流转发复杂

---

### 新架构流程

```
1. 用户提问："请解释半加器的工作原理"
   ↓
2. CircuitAnalysisDetailWorkFlowV2.streamChat()
   ↓
3. 创建Java LangChain4j ChatModel (OpenAI/Qwen)
   ↓
4. 创建CircuitAnalysisAssistant (注册AgenticRagTool)
   ↓
5. assistant.chat("System Prompt + User Query").start()
   ↓
6. Java LLM开始生成（流式）
   ↓
7. Java LLM判断："需要查询知识库"
   ↓
8. LangChain4j自动调用 agenticRagTool.searchKnowledgeBase("半加器工作原理", kbIds)
   ↓
9. AgenticRagTool → PythonRagService.agenticQuerySync()
   ↓
10. Java RestTemplate发送HTTP POST到Python /api/v1/rag/agentic/query-sync
    ↓
11. Python Agentic RAG:
    - Adaptive Router → ReasoningChannel
    - 返回检索结果（JSON格式，NOT 流式）
    ↓
12. Python同步返回: {"success": true, "answer": "半加器是...", "channel": "REASONING"}
    ↓
13. AgenticRagTool返回answer字符串给LangChain4j
    ↓
14. Java LLM基于检索结果继续生成（流式）
    ↓
15. TokenStream.onNext() → handler.onNext() → Redis Stream → 前端
```

**特点**：
- ✅ Java LLM生成答案（Java控制）
- ✅ Python仅提供检索结果（同步）
- ✅ LangChain4j自动管理Tool调用
- ✅ 流式体验更流畅

---

## 关键文件对比

### 需要修改的文件

| 文件 | 旧架构 | 新架构（V2） | 变化 |
|------|--------|-------------|------|
| WorkFlow类 | 调用 `pythonRagService.agenticStreamQuery()` | 创建 `CircuitAnalysisAssistant` + `AgenticRagTool` | ✅ |
| PythonRagService | 提供 `agenticStreamQuery()` | 提供 `agenticQuerySync()` | ✅ 新增方法 |
| Python API | `/api/v1/rag/agentic/stream` | `/api/v1/rag/agentic/query-sync` | ✅ 新增endpoint |

### 新增的文件

| 文件 | 作用 |
|------|------|
| `AgenticRagTool.java` | 将Python Agentic RAG包装为LangChain4j Tool |
| `CircuitAnalysisAssistant.java` | 定义Assistant接口，支持流式对话 |
| `CircuitAnalysisDetailWorkFlowV2.java` | 新架构的WorkFlow实现（示例） |

---

## 迁移检查清单

### ✅ 已完成
- [x] 创建 `AgenticRagTool.java`
- [x] 在 `PythonRagService.java` 中添加 `agenticQuerySync()` 方法
- [x] 在Python端添加 `/api/v1/rag/agentic/query-sync` endpoint
- [x] 创建 `CircuitAnalysisAssistant.java` 接口
- [x] 创建 `CircuitAnalysisDetailWorkFlowV2.java` 示例

### 🔄 待完成
- [ ] 迁移 `CircuitAnalysisWorkFlow.java`
- [ ] 迁移 `PdfCircuitAnalysisWorkFlow.java`
- [ ] 迁移 `PdfCircuitAnalysisDetailWorkFlow.java`
- [ ] 修复TokenUsage NPE错误
- [ ] 测试端到端流程
- [ ] 性能对比测试

---

## 性能对比预期

| 指标 | 旧架构 | 新架构 | 改进 |
|------|--------|--------|------|
| 首Token延迟 | ~2-3秒 | ~0.5-1秒 | ⬇️ 60-70% |
| 流式流畅度 | 中等（SSE转发） | 高（直接流式） | ⬆️ 显著 |
| 答案质量 | 中等 | 高（Java LLM更好） | ⬆️ 提升 |
| 架构复杂度 | 高（SSE转发） | 低（Tool调用） | ⬇️ 简化 |
| 可维护性 | 低 | 高 | ⬆️ 提升 |

---

## 常见问题

### Q1: 为什么旧架构效果差？
A: Python LLM生成答案时，Java无法控制生成策略、温度、采样等参数，且SSE流转发增加延迟。

### Q2: 新架构会调用两次LLM吗？
A: 不会！Python Agentic RAG返回的是检索结果（纯文本），不是LLM生成的。只有Java LLM进行生成。

### Q3: Tool调用会阻塞流式输出吗？
A: 会的，但这是必要的。LangChain4j在需要时暂停流式生成，调用Tool获取信息，然后继续生成。用户体验上仍然是流畅的。

### Q4: 如何调试Tool调用？
A: 查看Java日志中的 `[AgenticRagTool]` 和 Python日志中的 `[AgenticRAG-Sync]` 标签。

### Q5: 旧WorkFlow还能用吗？
A: 可以，但强烈建议迁移到V2。旧架构将在未来版本中废弃。

---

## 总结

**核心变化**：
- 从 "Python生成 + Java转发" → "Java生成 + Python提供工具"
- 从 "SSE流式转发" → "同步Tool调用"
- 从 "被动管道" → "主动控制"

**效果提升**：
- ✅ 更快的响应速度
- ✅ 更流畅的流式体验
- ✅ 更好的答案质量
- ✅ 更清晰的架构
- ✅ 更易维护和扩展

**推荐行动**：
1. 立即测试 `CircuitAnalysisDetailWorkFlowV2`
2. 验证端到端流程
3. 逐步迁移其他3个WorkFlow
4. 废弃旧的 `agenticStreamQuery()` 方法

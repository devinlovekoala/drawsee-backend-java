# Agentic RAG Tool 集成指南

## 架构变更说明

### 旧架构（已废弃）
```
用户查询 → Java WorkFlow → PythonRagService.agenticStreamQuery()
  → Python生成完整答案 → SSE流式返回 → Java转发 → 前端
```

**问题**：
- Python LLM生成答案，绕过了Java的LangChain4j体系
- SSE流转发低效且复杂
- 无法利用LangChain4j的Tool、Memory等特性
- 效果差，用户体验不佳

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
- Java主导对话生成，保持原有流式体验
- Python专注提供RAG工具和检索能力
- 可以利用LangChain4j的完整生态（Tool、Memory、Chain等）
- 架构清晰，易于维护和扩展

## 快速集成

### 1. 在WorkFlow中注入AgenticRagTool

```java
@Component
public class CircuitAnalysisWorkFlow extends BaseWorkFlow {

    @Autowired
    private AgenticRagTool agenticRagTool;  // 注入Tool

    // ... 其他依赖
}
```

### 2. 配置ChatModel使用Tool

```java
StreamingChatLanguageModel chatModel = OpenAiStreamingChatLanguageModel.builder()
    .apiKey(llmApiKey)
    .baseUrl(llmBaseUrl)
    .modelName(llmModel)
    .temperature(0.7)
    .timeout(Duration.ofSeconds(60))
    .build();

// 使用AiServices包装，自动支持Tool调用
YourAssistant assistant = AiServices.builder(YourAssistant.class)
    .streamingChatLanguageModel(chatModel)
    .tools(agenticRagTool)  // 注册Tool
    .chatMemory(MessageWindowChatMemory.withMaxMessages(10))
    .build();
```

### 3. 定义Assistant接口

```java
public interface CircuitAnalysisAssistant {

    /**
     * 分析电路设计
     *
     * LangChain4j会自动识别何时需要调用searchKnowledgeBase Tool
     */
    TokenStream chat(String userMessage);
}
```

### 4. 使用示例

```java
public void processCircuitAnalysis(AiTaskMessage message) {
    // 1. 构建查询上下文
    String query = buildCircuitQuery(message);

    // 2. 调用Assistant（带Tool支持）
    circuitAssistant.chat(query)
        .onNext(token -> {
            // 流式输出到前端
            emitter.send(token);
        })
        .onComplete(response -> {
            // 完成处理
            log.info("分析完成: {}", response.content());
        })
        .onError(error -> {
            log.error("分析失败", error);
        })
        .start();
}
```

## Tool自动调用流程

1. **用户提问**："请分析这个半加器电路的工作原理"

2. **LangChain4j判断需要知识库**：ChatModel识别到需要查询教材知识

3. **自动调用Tool**：
   ```java
   agenticRagTool.searchKnowledgeBase(
       "半加器电路工作原理",
       knowledgeBaseIds
   )
   ```

4. **Python Agentic RAG处理**：
   - 输入类型分类：MIXED（电路+分析）
   - 意图分类：ANALYSIS
   - 路由到：CircuitReasoningChannel
   - 返回检索结果

5. **Java继续生成**：ChatModel基于检索结果生成完整答案，流式输出给前端

## 高级用法

### 自定义Tool调用时机

```java
String systemPrompt = """
你是一位专业的电路分析助教。

**重要规则**：
- 当需要查询教材、课件、电路知识时，使用searchKnowledgeBase工具
- 当需要计算公式时，使用searchKnowledgeBase工具（会自动路由到FormulaChannel）
- 对于常识性问题，可以直接回答，无需调用工具

请基于知识库内容和你的专业知识，为学生提供准确、详细的电路分析。
""";

CircuitAnalysisAssistant assistant = AiServices.builder(CircuitAnalysisAssistant.class)
    .streamingChatLanguageModel(chatModel)
    .tools(agenticRagTool)
    .chatMemory(memory)
    .build();

assistant.chat(systemPrompt + "\n\n" + userQuery);
```

### 处理Tool返回结果

Tool的返回值会自动注入到ChatModel的上下文中：

```
User: 请分析半加器电路
Assistant: [调用searchKnowledgeBase("半加器电路工作原理")]
Tool Result: "半加器是一种数字电路，用于将两个一位二进制数相加..."
Assistant: 根据知识库内容，半加器是一种数字电路... [继续基于检索结果生成]
```

## 迁移现有WorkFlow

### 步骤1：移除流式SSE调用

**旧代码**（删除）：
```java
pythonRagService.agenticStreamQuery(
    query,
    knowledgeBaseIds,
    classId,
    userId,
    new StreamingResponseHandler<AiMessage>() {
        @Override
        public void onNext(String token) {
            emitter.send(token);
        }
        // ...
    }
);
```

### 步骤2：改用Tool-based架构

**新代码**：
```java
@Autowired
private AgenticRagTool agenticRagTool;

CircuitAnalysisAssistant assistant = AiServices.builder(CircuitAnalysisAssistant.class)
    .streamingChatLanguageModel(chatModel)
    .tools(agenticRagTool)  // 注册Tool
    .build();

assistant.chat(query)
    .onNext(token -> emitter.send(token))
    .onComplete(response -> emitter.complete())
    .start();
```

## 测试Tool功能

```java
@Test
public void testAgenticRagTool() {
    String result = agenticRagTool.searchKnowledgeBase(
        "什么是基尔霍夫电压定律？",
        List.of("kb_001")
    );

    assertNotNull(result);
    assertTrue(result.contains("基尔霍夫"));
}
```

## 常见问题

### Q: Tool何时会被调用？
A: LangChain4j的ChatModel会根据Tool的description和当前对话上下文，自动判断是否需要调用Tool。

### Q: 如何控制Tool调用频率？
A: 通过调整systemPrompt中的规则，明确指定何时应该使用Tool。

### Q: Tool调用会阻塞吗？
A: 是的，Tool调用是同步的，但这是必要的。ChatModel需要等待Tool返回结果后才能继续生成。

### Q: 能否同时使用多个Tool？
A: 可以！只需在`tools()`中传入多个Tool实例即可：
```java
.tools(agenticRagTool, calculatorTool, codeExecutorTool)
```

## 性能优化建议

1. **缓存知识库ID**：避免每次都查询用户权限
2. **设置合理的超时**：`agenticQuerySync`建议设置30-60秒超时
3. **使用Memory**：避免重复调用相同的Tool查询
4. **并行Tool调用**：LangChain4j支持并行执行多个Tool（如果需要）

## 总结

新架构将**对话生成**和**知识检索**解耦：
- **Java LangChain4j**：负责对话流程、流式输出、Tool编排
- **Python Agentic RAG**：负责智能检索、公式计算、电路分析

这样既保持了流畅的用户体验，又充分利用了Python端的Agentic RAG能力！

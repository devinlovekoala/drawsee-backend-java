# Agentic RAG v2 API 接口规范

**版本**: v2.0
**日期**: 2025-12-16
**目标**: 将Agentic RAG v2路由系统对接到现有Java后端和前端

---

## 1. 核心API端点

### 1.1 智能路由查询（主接口）

**端点**: `POST /api/v1/agentic/query`

**描述**: 统一入口，自动进行双层分类（INPUT_TYPE + INTENT）并路由到对应频道

**请求格式**:
```json
{
  "query": "R1=10kΩ, R2=20kΩ, Vin=12V, 求Vout",
  "knowledge_base_ids": ["kb_001", "kb_002"],
  "session_id": "session_123",
  "has_image": false,
  "image_url": null,
  "history": [
    {
      "role": "user",
      "content": "什么是分压电路？"
    },
    {
      "role": "assistant",
      "content": "分压电路是一种..."
    }
  ],
  "context": {
    "user_id": 1001,
    "class_id": "class_001"
  }
}
```

**请求字段说明**:
| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| query | string | ✅ | 用户输入的问题/文本 |
| knowledge_base_ids | array[string] | ✅ | 知识库ID列表 |
| session_id | string | ❌ | 会话ID（用于多轮对话） |
| has_image | boolean | ❌ | 是否包含图片（默认false） |
| image_url | string | ❌ | 图片URL（has_image=true时需提供） |
| history | array[object] | ❌ | 对话历史（用于上下文理解） |
| context | object | ❌ | 额外上下文信息（user_id, class_id等） |

**响应格式**:
```json
{
  "success": true,
  "session_id": "session_123",
  "message_id": "msg_456",
  "cost_time_ms": 1250,
  "classification": {
    "input_type": "FORMULA_PROBLEM",
    "input_type_confidence": 0.98,
    "intent": "COMPUTATION",
    "intent_confidence": 0.98
  },
  "routing": {
    "channel": "FORMULA",
    "reason": "输入载体为公式问题，强制路由到FormulaChannel（确定性计算）"
  },
  "result": {
    "answer": "## 分压电路计算\n\n### 已知条件\n- Vin = 12V\n- R1 = 10kΩ\n- R2 = 20kΩ\n\n### 使用公式\nVout = Vin × R2 / (R1 + R2)\n\n### 计算过程\n1. 代入已知值:\n   Vout = 12V × 20kΩ / (10kΩ + 20kΩ)\n2. 求解:\n   Vout = 12V × 20kΩ / 30kΩ\n   Vout = 8.0V\n\n### 最终结果\n**Vout = 8.0 V**",
    "answer_type": "formula_computation",
    "sources": [],
    "problem": {
      "given": {
        "Vin": {"value": 12, "unit": "V"},
        "R1": {"value": 10000, "unit": "Ω"},
        "R2": {"value": 20000, "unit": "Ω"}
      },
      "unknown": "Vout",
      "formula_type": "分压公式",
      "formula_used": "Vout = Vin × R2 / (R1 + R2)"
    },
    "solution": {
      "steps": [
        "使用分压公式",
        "代入已知值: Vin=12V, R1=10kΩ, R2=20kΩ",
        "计算: Vout = 12 × 20000 / (10000 + 20000) = 8.0V"
      ],
      "result": {
        "value": 8.0,
        "unit": "V",
        "display": "8.0 V"
      }
    },
    "confidence": 0.99
  },
  "metadata": {
    "tokens": 150,
    "channel_available": true,
    "fallback": false
  }
}
```

**响应字段说明**:
| 字段路径 | 类型 | 说明 |
|---------|------|------|
| success | boolean | 请求是否成功 |
| session_id | string | 会话ID |
| message_id | string | 消息ID（唯一标识） |
| cost_time_ms | integer | 处理耗时（毫秒） |
| classification.input_type | string | 输入载体类型（5种） |
| classification.intent | string | 意图类型（5种） |
| routing.channel | string | 路由到的频道 |
| routing.reason | string | 路由原因说明 |
| result.answer | string | 格式化的答案（Markdown） |
| result.answer_type | string | 答案类型（formula_computation/knowledge_qa/circuit_analysis等） |
| result.sources | array | 来源引用（KnowledgeChannel有） |
| result.problem | object | 问题解析（FormulaChannel有） |
| result.solution | object | 解答步骤（FormulaChannel有） |
| result.confidence | float | 答案置信度（0-1） |
| metadata.fallback | boolean | 是否是降级处理 |

---

## 2. 知识问答专用接口（可选）

### 2.1 强制RAG查询

**端点**: `POST /api/v1/agentic/knowledge/query`

**描述**: 绕过路由，直接使用KnowledgeChannel进行强制RAG查询

**请求格式**:
```json
{
  "query": "什么是基尔霍夫定律？",
  "knowledge_base_ids": ["kb_001"],
  "intent": "CONCEPT",
  "session_id": "session_123",
  "history": [],
  "context": {}
}
```

**响应格式**:
```json
{
  "success": true,
  "answer": "基尔霍夫定律包括两个基本定律...\n\n**来源**: 电路基础.pdf (第23页)",
  "sources": [
    {
      "type": "text_chunk",
      "content": "基尔霍夫电流定律（KCL）指出...",
      "metadata": {
        "filename": "电路基础.pdf",
        "page": 23,
        "document_id": "doc_001"
      },
      "score": 0.85
    }
  ],
  "confidence": 0.92,
  "retrieval_stats": {
    "total_results": 5,
    "circuit_count": 1,
    "text_count": 4,
    "max_score": 0.85
  }
}
```

---

## 3. 公式计算专用接口（可选）

### 3.1 确定性计算

**端点**: `POST /api/v1/agentic/formula/compute`

**描述**: 绕过路由，直接使用FormulaChannel进行SymPy确定性计算

**请求格式**:
```json
{
  "query": "R=330Ω, V=5V, 求I",
  "session_id": "session_123",
  "context": {}
}
```

**响应格式**:
```json
{
  "success": true,
  "problem": {
    "given": {
      "R": {"value": 330, "unit": "Ω"},
      "V": {"value": 5, "unit": "V"}
    },
    "unknown": "I",
    "formula_type": "欧姆定律",
    "formula_used": "I = V / R"
  },
  "solution": {
    "steps": [
      "使用欧姆定律: I = V / R",
      "代入已知值: V=5V, R=330Ω",
      "计算: I = 5 / 330 = 0.015152 A"
    ],
    "result": {
      "value": 0.015151515151515152,
      "unit": "A",
      "display": "15.152 mA"
    }
  },
  "explanation": "## 欧姆定律计算\n\n### 已知条件\n- V = 5V\n- R = 330Ω\n\n### 使用公式\nI = V / R\n\n### 计算过程\n1. 代入已知值: V=5V, R=330Ω\n2. 求解: I = 5 / 330 = 0.015152 A\n\n### 最终结果\n**I = 15.152 mA**",
  "confidence": 1.0
}
```

---

## 4. 分类预览接口（调试用）

### 4.1 输入载体分类

**端点**: `POST /api/v1/agentic/classify/input-type`

**描述**: 仅进行输入载体分类，不执行路由和处理

**请求格式**:
```json
{
  "query": "R1=10kΩ, R2=20kΩ, Vin=12V, 求Vout",
  "has_image": false,
  "image_url": null
}
```

**响应格式**:
```json
{
  "input_type": "FORMULA_PROBLEM",
  "confidence": 0.98,
  "reasoning": "检测到数值、单位、计算动词，判定为公式计算问题",
  "detection_method": "rule_based"
}
```

### 4.2 意图分类

**端点**: `POST /api/v1/agentic/classify/intent`

**描述**: 仅进行意图分类，不执行路由和处理

**请求格式**:
```json
{
  "query": "分析三极管放大电路的工作原理",
  "input_type": "NATURAL_QA"
}
```

**响应格式**:
```json
{
  "intent": "ANALYSIS",
  "confidence": 0.93,
  "reasoning": "询问工作原理，判定为分析意图",
  "classification_method": "llm_assisted"
}
```

---

## 5. 流式查询接口（高级）

### 5.1 SSE流式响应

**端点**: `POST /api/v1/agentic/query/stream`

**描述**: 与主接口相同，但使用SSE流式返回答案

**请求格式**: 同主接口

**响应格式（SSE流）**:
```
event: classification
data: {"input_type": "NATURAL_QA", "intent": "CONCEPT"}

event: routing
data: {"channel": "KNOWLEDGE", "reason": "意图为概念理解，路由到KnowledgeChannel"}

event: chunk
data: {"content": "基尔霍夫定律包括", "index": 0}

event: chunk
data: {"content": "两个基本定律", "index": 1}

event: sources
data: {"sources": [...]}

event: done
data: {"total_tokens": 150, "cost_time_ms": 1250}
```

---

## 6. 错误处理

### 6.1 错误响应格式

```json
{
  "success": false,
  "error": "classification_failed",
  "message": "输入类型分类失败",
  "detail": "LLM调用超时",
  "request_id": "req_789",
  "timestamp": "2025-12-16T04:30:00Z"
}
```

### 6.2 错误代码

| 错误代码 | HTTP状态 | 说明 | 降级策略 |
|---------|---------|------|---------|
| classification_failed | 500 | 分类失败 | 降级到KnowledgeChannel |
| channel_not_available | 503 | 频道不可用 | 自动降级 |
| knowledge_base_not_found | 404 | 知识库不存在 | 返回错误 |
| llm_call_failed | 500 | LLM调用失败 | 重试3次 |
| invalid_formula_format | 400 | 公式格式错误 | 返回提示 |
| retrieval_failed | 500 | 检索失败 | 返回错误 |

---

## 7. 与现有系统集成

### 7.1 与Java WorkFlow集成

**新增TaskType**: `AGENTIC_RAG_V2`

**创建任务**:
```java
CreateAiTaskDTO dto = CreateAiTaskDTO.builder()
    .type("AGENTIC_RAG_V2")
    .prompt("R1=10kΩ, R2=20kΩ, Vin=12V, 求Vout")
    .promptParams(Map.of(
        "knowledge_base_ids", List.of("kb_001"),
        "has_image", false
    ))
    .convId(123L)
    .build();

CreateAiTaskVO result = flowController.createTask(dto);
```

**WorkFlow处理**:
```java
public class AgenticRagV2WorkFlow extends WorkFlow {

    @Override
    public void createOtherNodesOrUpdateNodeData(WorkContext workContext) {
        // 1. 调用Python Agentic RAG API
        Map<String, Object> response = pythonRagService.agenticQuery(...);

        // 2. 根据channel类型创建节点
        String channel = (String) response.get("channel");

        if ("FORMULA".equals(channel)) {
            // 创建公式计算节点
            createFormulaNodes(workContext, response);
        } else if ("KNOWLEDGE".equals(channel)) {
            // 创建知识问答节点
            createKnowledgeNodes(workContext, response);
        }

        // 3. 发布到Redis Stream
        publishToStream(workContext, response);
    }
}
```

### 7.2 Node节点映射

| Channel | NodeType | 数据存储 |
|---------|----------|---------|
| FORMULA | ANSWER + ANSWER_DETAIL | problem + solution in data |
| KNOWLEDGE | ANSWER + KNOWLEDGE_HEAD | answer + sources in data |
| NETLIST | CIRCUIT_ANALYZE | netlist_structure in data |
| VISION | CIRCUIT_CANVAS | image_analysis in data |
| REASONING | ANSWER + ANSWER_POINT | reasoning_steps in data |

---

## 8. 前端集成示例

### 8.1 React调用示例

```typescript
// API调用
const response = await fetch('/api/v1/agentic/query', {
  method: 'POST',
  headers: {
    'Content-Type': 'application/json',
    'Authorization': `Bearer ${token}`
  },
  body: JSON.stringify({
    query: userInput,
    knowledge_base_ids: selectedKnowledgeBases,
    session_id: currentSessionId,
    has_image: hasUploadedImage,
    image_url: uploadedImageUrl,
    history: conversationHistory
  })
});

const result = await response.json();

// 处理响应
if (result.success) {
  const { classification, routing, result: answer } = result;

  // 显示分类信息（调试用）
  console.log(`Input Type: ${classification.input_type}`);
  console.log(`Intent: ${classification.intent}`);
  console.log(`Channel: ${routing.channel}`);

  // 根据answer_type渲染不同组件
  if (answer.answer_type === 'formula_computation') {
    renderFormulaAnswer(answer);
  } else if (answer.answer_type === 'knowledge_qa') {
    renderKnowledgeAnswer(answer);
  }
}
```

### 8.2 流式调用示例

```typescript
const eventSource = new EventSource(
  `/api/v1/agentic/query/stream?` + new URLSearchParams({
    query: userInput,
    knowledge_base_ids: JSON.stringify(selectedKnowledgeBases),
    session_id: currentSessionId
  })
);

eventSource.addEventListener('classification', (e) => {
  const data = JSON.parse(e.data);
  setClassification(data);
});

eventSource.addEventListener('routing', (e) => {
  const data = JSON.parse(e.data);
  setRouting(data);
});

eventSource.addEventListener('chunk', (e) => {
  const data = JSON.parse(e.data);
  appendAnswerChunk(data.content);
});

eventSource.addEventListener('done', (e) => {
  eventSource.close();
  setIsComplete(true);
});
```

---

## 9. 性能指标

| 指标 | 目标值 | 说明 |
|------|--------|------|
| 分类延迟 | <200ms | 双层分类总耗时 |
| FormulaChannel延迟 | <100ms | SymPy计算 |
| KnowledgeChannel延迟 | <1500ms | 包含检索+生成 |
| 首Token延迟（流式） | <500ms | SSE首次返回 |
| 吞吐量 | >50 QPS | 并发查询能力 |

---

## 10. 安全性

### 10.1 认证

- Java → Python: 内部JWT（共享secret）
- 前端 → Java: 用户JWT（Sa-Token）

### 10.2 权限控制

- 知识库访问: 验证user_id + class_id权限
- 多租户隔离: class_id级别隔离

### 10.3 输入验证

- query长度限制: ≤5000字符
- image_url验证: HTTPS + 白名单域名
- knowledge_base_ids: 验证存在性和权限

---

## 11. 监控和日志

### 11.1 关键日志点

```python
logger.info(f"[AgenticRAG] 收到请求: query='{query[:50]}...', session_id={session_id}")
logger.info(f"[AgenticRAG] 分类完成: input_type={input_type}, intent={intent}")
logger.info(f"[AgenticRAG] 路由决策: channel={channel}, reason={reason}")
logger.info(f"[AgenticRAG] 处理完成: cost_time={cost_time}ms, tokens={tokens}")
```

### 11.2 监控指标

- 请求总量（按channel分组）
- 平均延迟（按channel分组）
- 分类准确率（人工标注验证）
- 降级率（fallback频率）
- 错误率（按error_code分组）

---

**API规范版本**: v2.0
**最后更新**: 2025-12-16 04:35
**下一步**: 实现Python端点

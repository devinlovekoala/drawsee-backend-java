# Agentic RAG v2 API 接口设计规范

**版本**: v1.0
**日期**: 2025-12-16
**用途**: 前端(drawsee-web) ↔ Python RAG服务 ↔ Java后端集成

---

## 1. 设计原则

### 1.1 集成架构

```
前端 drawsee-web (React)
    ↓ HTTP + SSE
Java后端 drawsee-java:6868
    ↓ HTTP
Python RAG服务 drawsee-rag-python:8000
    ↓
Agentic Router (v2)
    ├─ KnowledgeChannel
    ├─ FormulaChannel
    ├─ NetlistChannel (待实现)
    ├─ VisionChannel (待实现)
    └─ ReasoningChannel (待实现)
```

### 1.2 数据流设计

**场景1: 同步查询**（适用于简单问答、公式计算）
```
前端 → Java → Python (Agentic Router) → 返回完整结果
```

**场景2: 流式查询**（适用于复杂分析、长篇回答）
```
前端 → Java (SSE) ← Java ← Python (Server-Sent Events)
```

### 1.3 兼容性约定

- **向前兼容**: 新增字段可选，旧字段保留
- **错误处理**: 统一错误码和消息格式
- **多模态支持**: 图片、文本、Netlist统一处理
- **知识库绑定**: 支持多知识库ID查询

---

## 2. 核心API接口

### 2.1 Agentic RAG 查询接口（同步）

**端点**: `POST /api/v1/rag/agentic-query`

**请求体** (`AgenticQueryRequest`):
```json
{
  "query": "R1=10kΩ, R2=20kΩ, Vin=12V, 求Vout",
  "knowledge_base_ids": ["kb_001", "kb_002"],
  "input_context": {
    "has_image": false,
    "image_url": null,
    "netlist_content": null,
    "conversation_history": []
  },
  "options": {
    "model": "deepseek-v3",
    "temperature": 0.3,
    "enable_followup": true,
    "max_sources": 5
  }
}
```

**字段说明**:
- `query`: 用户问题（必填）
- `knowledge_base_ids`: 知识库ID列表（必填）
- `input_context.has_image`: 是否包含图片
- `input_context.image_url`: 图片URL（已上传）
- `input_context.netlist_content`: SPICE Netlist内容
- `input_context.conversation_history`: 会话历史（用于上下文）
- `options.model`: LLM模型选择
- `options.enable_followup`: 是否生成追问建议
- `options.max_sources`: 最大来源数量

**响应体** (`AgenticQueryResponse`):
```json
{
  "success": true,
  "request_id": "req_20251216_001",
  "channel": "FORMULA",
  "classification": {
    "input_type": "FORMULA_PROBLEM",
    "input_type_confidence": 0.98,
    "intent": "COMPUTATION",
    "intent_confidence": 0.98
  },
  "result": {
    "answer": "## 分压公式计算\n\n### 已知条件\n...",
    "answer_type": "formula_computation",
    "confidence": 0.99,
    "sources": [
      {
        "type": "text_chunk",
        "content": "分压公式说明...",
        "metadata": {
          "filename": "电路基础.pdf",
          "page": 23,
          "knowledge_base_id": "kb_001"
        },
        "score": 0.85
      }
    ],
    "followup_suggestions": [
      "如果R1改为5kΩ，Vout会变成多少？",
      "这个电路的功率消耗是多少？"
    ],
    "metadata": {
      "formula_type": "分压公式",
      "computation_steps": 3,
      "processing_time_ms": 156
    }
  },
  "routing_reason": "输入载体为公式问题，强制路由到FormulaChannel",
  "fallback": false,
  "timestamp": "2025-12-16T04:30:00Z"
}
```

**字段说明**:
- `channel`: 实际处理频道（KNOWLEDGE/FORMULA/NETLIST/VISION/REASONING）
- `classification`: 双层分类结果
- `result.answer`: 最终答案（Markdown格式）
- `result.answer_type`: 答案类型标识
- `result.sources`: 来源标注（RAG场景）
- `result.followup_suggestions`: 追问建议
- `result.metadata`: 频道特定的元数据
- `fallback`: 是否使用了降级策略

---

### 2.2 Agentic RAG 流式查询接口

**端点**: `GET /api/v1/rag/agentic-stream?query_id={query_id}`

**流程**:
1. 客户端先调用 `POST /api/v1/rag/agentic-query-async` 创建异步任务
2. 获得 `query_id`
3. 使用SSE连接 `/api/v1/rag/agentic-stream?query_id={query_id}`

**SSE消息格式**:
```typescript
// 消息类型
type AgenticStreamMessageType =
  | 'classification'  // 分类结果
  | 'routing'         // 路由决策
  | 'chunk'           // 流式文本块
  | 'source'          // 来源标注
  | 'followup'        // 追问建议
  | 'metadata'        // 元数据
  | 'done'            // 完成信号
  | 'error';          // 错误

// 消息结构
interface AgenticStreamMessage {
  type: AgenticStreamMessageType;
  data: unknown;
  timestamp: string;
}
```

**消息示例**:
```
data: {"type":"classification","data":{"input_type":"FORMULA_PROBLEM","confidence":0.98},"timestamp":"..."}

data: {"type":"routing","data":{"channel":"FORMULA","reason":"..."},"timestamp":"..."}

data: {"type":"chunk","data":{"content":"## 分压公式计算\n\n"},"timestamp":"..."}

data: {"type":"chunk","data":{"content":"### 已知条件\n- R1 = 10kΩ\n"},"timestamp":"..."}

data: {"type":"source","data":{"type":"text_chunk","content":"...","metadata":{...}},"timestamp":"..."}

data: {"type":"followup","data":{"suggestion":"如果R1改为5kΩ..."},"timestamp":"..."}

data: {"type":"done","data":null,"timestamp":"..."}
```

---

### 2.3 图片上传 + Agentic处理

**端点**: `POST /api/v1/rag/agentic-image-query`

**请求**: `multipart/form-data`
```
file: [image binary]
query: "分析这个电路的工作原理"
knowledge_base_ids: ["kb_001"]
options: {"model": "deepseek-v3", ...}
```

**响应**: 同 `AgenticQueryResponse`，但：
- `classification.input_type = "CIRCUIT_DIAGRAM"`
- `channel = "VISION"` (或降级到KNOWLEDGE)
- `result.metadata` 包含图片识别结果

---

## 3. 辅助接口

### 3.1 输入类型预判接口

**端点**: `POST /api/v1/rag/classify-input-type`

**用途**: 前端提交前快速判断输入类型，给用户提示

**请求**:
```json
{
  "query": "R1 1 2 10k\nV1 1 0 DC 5",
  "has_image": false
}
```

**响应**:
```json
{
  "input_type": "CIRCUIT_NETLIST",
  "confidence": 0.98,
  "reasoning": "检测到SPICE Netlist语法",
  "suggestions": [
    "这是一个电路Netlist，系统将使用形式化解析引擎处理"
  ]
}
```

### 3.2 意图识别接口

**端点**: `POST /api/v1/rag/classify-intent`

**请求**:
```json
{
  "query": "什么是基尔霍夫定律？",
  "input_type": "NATURAL_QA"
}
```

**响应**:
```json
{
  "intent": "CONCEPT",
  "confidence": 0.95,
  "reasoning": "询问概念定义"
}
```

### 3.3 可用频道查询

**端点**: `GET /api/v1/rag/channels/status`

**响应**:
```json
{
  "available_channels": ["KNOWLEDGE", "FORMULA"],
  "pending_channels": ["NETLIST", "VISION", "REASONING"],
  "channel_details": {
    "KNOWLEDGE": {
      "status": "available",
      "version": "v2.0",
      "capabilities": ["concept_qa", "rule_qa", "source_citation"]
    },
    "FORMULA": {
      "status": "available",
      "version": "v2.0",
      "capabilities": ["symbolic_computation", "6_formula_types", "unit_conversion"]
    },
    "NETLIST": {
      "status": "pending",
      "estimated_completion": "2025-12-20"
    }
  }
}
```

---

## 4. 错误处理

### 4.1 错误响应格式

```json
{
  "success": false,
  "error": {
    "code": "AGENTIC_ROUTING_FAILED",
    "message": "路由决策失败：无法识别输入类型",
    "detail": "InputTypeClassifier returned confidence < 0.5",
    "request_id": "req_20251216_002",
    "timestamp": "2025-12-16T04:30:00Z"
  }
}
```

### 4.2 错误码定义

| 错误码 | HTTP状态码 | 描述 |
|-------|-----------|------|
| `INVALID_REQUEST` | 400 | 请求参数无效 |
| `KNOWLEDGE_BASE_NOT_FOUND` | 404 | 知识库不存在 |
| `CLASSIFICATION_FAILED` | 500 | 分类器失败 |
| `ROUTING_FAILED` | 500 | 路由决策失败 |
| `CHANNEL_EXECUTION_FAILED` | 500 | 频道执行失败 |
| `INSUFFICIENT_KNOWLEDGE` | 200 | 知识不足（业务成功，但无内容） |
| `MODEL_UNAVAILABLE` | 503 | LLM模型不可用 |

---

## 5. Java集成适配

### 5.1 Java Service接口

**文件**: `drawsee-java/src/main/java/cn/yifan/drawsee/service/base/AgenticRagService.java`

```java
@Service
public class AgenticRagService {

    @Value("${python.rag.base-url}")
    private String pythonRagBaseUrl;  // http://localhost:8000

    private final RestTemplate restTemplate;

    /**
     * 同步Agentic查询
     */
    public AgenticQueryResponse agenticQuery(
        String query,
        List<String> knowledgeBaseIds,
        AgenticInputContext inputContext,
        AgenticQueryOptions options
    ) {
        String url = pythonRagBaseUrl + "/api/v1/rag/agentic-query";

        AgenticQueryRequest request = AgenticQueryRequest.builder()
            .query(query)
            .knowledgeBaseIds(knowledgeBaseIds)
            .inputContext(inputContext)
            .options(options)
            .build();

        ResponseEntity<AgenticQueryResponse> response = restTemplate.postForEntity(
            url,
            request,
            AgenticQueryResponse.class
        );

        return response.getBody();
    }

    /**
     * 创建异步查询任务（用于SSE流式）
     */
    public String createAgenticQueryTask(...) {
        // 调用 /api/v1/rag/agentic-query-async
        // 返回 query_id
    }

    /**
     * 获取输入类型分类
     */
    public InputTypeClassification classifyInputType(String query, boolean hasImage) {
        // 调用 /api/v1/rag/classify-input-type
    }
}
```

### 5.2 Java DTO类

```java
@Data
@Builder
public class AgenticQueryRequest {
    private String query;
    private List<String> knowledgeBaseIds;
    private AgenticInputContext inputContext;
    private AgenticQueryOptions options;
}

@Data
@Builder
public class AgenticInputContext {
    private Boolean hasImage;
    private String imageUrl;
    private String netlistContent;
    private List<ConversationMessage> conversationHistory;
}

@Data
@Builder
public class AgenticQueryOptions {
    private String model;              // "deepseek-v3" / "doubao"
    private Double temperature;
    private Boolean enableFollowup;
    private Integer maxSources;
}

@Data
public class AgenticQueryResponse {
    private Boolean success;
    private String requestId;
    private String channel;
    private AgenticClassification classification;
    private AgenticResult result;
    private String routingReason;
    private Boolean fallback;
    private String timestamp;
}
```

---

## 6. 前端集成示例

### 6.1 新增API方法

**文件**: `drawsee-web/src/api/methods/agentic.methods.ts`

```typescript
export interface AgenticQueryRequest {
  query: string;
  knowledgeBaseIds: string[];
  inputContext?: {
    hasImage?: boolean;
    imageUrl?: string;
    netlistContent?: string;
    conversationHistory?: ConversationMessage[];
  };
  options?: {
    model?: 'deepseekV3' | 'doubao';
    temperature?: number;
    enableFollowup?: boolean;
    maxSources?: number;
  };
}

export interface AgenticQueryResponse {
  success: boolean;
  requestId: string;
  channel: string;
  classification: {
    inputType: string;
    inputTypeConfidence: number;
    intent: string;
    intentConfidence: number;
  };
  result: {
    answer: string;
    answerType: string;
    confidence: number;
    sources?: Array<{
      type: string;
      content: string;
      metadata: Record<string, any>;
      score: number;
    }>;
    followupSuggestions?: string[];
    metadata?: Record<string, any>;
  };
  routingReason: string;
  fallback: boolean;
  timestamp: string;
}

// Java后端代理
export const agenticQuery = (request: AgenticQueryRequest) => {
  return alova.Post<AgenticQueryResponse>(
    '/api/agentic/query',  // Java端点
    request
  );
};

// 输入类型预判
export const classifyInputType = (query: string, hasImage: boolean = false) => {
  return alova.Post<{
    inputType: string;
    confidence: number;
    reasoning: string;
    suggestions: string[];
  }>(
    '/api/agentic/classify-input-type',
    { query, hasImage }
  );
};
```

### 6.2 新增AiTaskType

**文件**: `drawsee-web/src/api/types/flow.types.ts`

```typescript
type AiTaskType =
  | "GENERAL"
  | "GENERAL_CONTINUE"
  | "GENERAL_DETAIL"
  | "KNOWLEDGE"
  | "KNOWLEDGE_DETAIL"
  | "ANIMATION"
  | "ANIMATION_DETAIL"
  | "SOLVER_FIRST"
  | "SOLVER_CONTINUE"
  | "SOLVER_SUMMARY"
  | "CIRCUIT_ANALYSIS"
  | "CIRCUIT_DETAIL"
  | "PDF_CIRCUIT_ANALYSIS"
  | "PDF_CIRCUIT_ANALYSIS_DETAIL"
  | "PDF_CIRCUIT_DESIGN"
  | "AGENTIC_RAG"              // 新增：Agentic RAG v2
  | "AGENTIC_RAG_FORMULA"      // 新增：公式计算
  | "AGENTIC_RAG_NETLIST"      // 新增：Netlist解析
  | "AGENTIC_RAG_IMAGE";       // 新增：图片分析
```

### 6.3 使用示例

```typescript
// 在 FlowInputPanel.tsx 中
const handleAgenticQuery = async (query: string) => {
  try {
    // 1. 预判输入类型（可选，用于UI提示）
    const classification = await classifyInputType(query, hasImage);
    console.log(`检测到输入类型: ${classification.inputType}`);

    // 2. 创建AI任务
    const taskResult = await createAiTask({
      type: 'AGENTIC_RAG',
      prompt: query,
      promptParams: null,
      convId: currentConvId,
      parentId: currentNodeId,
      model: selectedModel,
      classId: null
    });

    // 3. 启动SSE流式连接（现有逻辑）
    await chat(taskResult.taskId);

  } catch (error) {
    console.error('Agentic查询失败:', error);
  }
};
```

---

## 7. 测试场景

### 7.1 公式计算场景

**输入**:
```json
{
  "query": "R1=10kΩ, R2=20kΩ, Vin=12V, 求Vout",
  "knowledge_base_ids": ["kb_001"]
}
```

**预期**:
- `classification.input_type = "FORMULA_PROBLEM"`
- `classification.intent = "COMPUTATION"`
- `channel = "FORMULA"`
- `result.answer` 包含完整计算步骤
- `result.sources = []` (公式计算无需RAG)

### 7.2 概念问答场景

**输入**:
```json
{
  "query": "什么是基尔霍夫定律？",
  "knowledge_base_ids": ["kb_001", "kb_002"]
}
```

**预期**:
- `classification.input_type = "NATURAL_QA"`
- `classification.intent = "CONCEPT"`
- `channel = "KNOWLEDGE"`
- `result.sources.length >= 1` (来源标注)

### 7.3 图片分析场景（降级）

**输入**:
```json
{
  "query": "分析这个电路",
  "knowledge_base_ids": ["kb_001"],
  "input_context": {
    "has_image": true,
    "image_url": "https://example.com/circuit.png"
  }
}
```

**预期**:
- `classification.input_type = "CIRCUIT_DIAGRAM"`
- `channel = "VISION"` or `"KNOWLEDGE"` (降级)
- `fallback = true` (如果降级)
- `result.answer` 包含降级提示

---

## 8. 性能要求

| 指标 | 目标 | 备注 |
|------|------|------|
| 同步查询响应时间 | < 2s | 公式计算场景 |
| 同步查询响应时间 | < 5s | 知识问答场景 |
| 流式首字节时间 | < 500ms | SSE场景 |
| 分类准确率 | > 95% | INPUT_TYPE + INTENT |
| 路由准确率 | > 98% | 频道选择 |
| 并发支持 | 100 QPS | 单实例 |

---

## 9. 部署配置

### 9.1 Python服务配置

```yaml
# application.yml
agentic_rag:
  enabled: true
  version: v2.0

  # 频道配置
  channels:
    knowledge:
      enabled: true
      max_sources: 5
      min_confidence: 0.7
    formula:
      enabled: true
      supported_formulas: 6
    netlist:
      enabled: false  # 待实现
    vision:
      enabled: false  # 待实现
    reasoning:
      enabled: false  # 待实现

  # 模型配置
  models:
    - name: deepseek-v3
      api_key: ${DEEPSEEK_API_KEY}
      temperature: 0.3
    - name: doubao
      api_key: ${DOUBAO_API_KEY}
      temperature: 0.3
```

### 9.2 Java服务配置

```yaml
# application.properties
python.rag.base-url=http://localhost:8000
python.rag.timeout=30000
python.rag.retry=3
```

---

**文档版本**: 1.0
**更新日期**: 2025-12-16
**状态**: ✅ 设计完成，待实现

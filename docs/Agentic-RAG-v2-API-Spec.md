# Agentic RAG v2 API 接口设计规范

**版本**: v1.0
**日期**: 2025-12-16
**目标**: 将Agentic RAG v2系统对接到现有drawsee-web前端

---

## 设计原则

1. **兼容现有架构**: 遵循现有的Flow API模式和SSE流式响应
2. **渐进式扩展**: 不破坏现有功能，通过扩展支持新特性
3. **类型安全**: 清晰的TypeScript类型定义
4. **前后端一致**: 统一的数据格式和错误处理

---

## 核心API端点

### 1. Agentic RAG 查询接口

**端点**: `POST /api/v1/rag/agentic/query`

**功能**: 统一的Agentic RAG查询入口，自动进行双层分类和路由

**请求体**:
```json
{
  "query": "R1=10kΩ, R2=20kΩ, Vin=12V, 求Vout",
  "knowledge_base_ids": ["kb_001", "kb_002"],
  "has_image": false,
  "image_url": null,
  "context": {
    "conversation_id": "conv_123",
    "parent_node_id": "node_456",
    "user_id": "user_789"
  },
  "options": {
    "model": "deepseekV3",
    "temperature": 0.7,
    "max_tokens": 2000,
    "stream": true
  }
}
```

**Python类型定义**:
```python
from pydantic import BaseModel, Field
from typing import Optional, Dict, Any, List

class AgenticRAGQueryRequest(BaseModel):
    query: str = Field(..., description="用户查询文本")
    knowledge_base_ids: List[str] = Field(..., description="知识库ID列表")
    has_image: bool = Field(default=False, description="是否包含图片")
    image_url: Optional[str] = Field(None, description="图片URL")
    context: Optional[Dict[str, Any]] = Field(None, description="上下文信息")
    options: Optional[Dict[str, Any]] = Field(None, description="可选配置")
```

**响应格式** (SSE流式):

```
event: classification
data: {"input_type": "FORMULA_PROBLEM", "input_type_confidence": 0.98, "intent": "COMPUTATION", "intent_confidence": 0.98, "channel": "FORMULA"}

event: routing
data: {"channel": "FORMULA", "reason": "输入载体为公式问题，强制路由到FormulaChannel"}

event: processing
data: {"status": "parsing", "message": "正在解析公式..."}

event: processing
data: {"status": "solving", "message": "使用SymPy求解...", "formula": "分压公式"}

event: result
data: {"success": true, "answer": "...", "problem": {...}, "solution": {...}}

event: done
data: {"task_id": "task_123", "total_time": 1.5}
```

**TypeScript类型定义**:
```typescript
// 请求类型
interface AgenticRAGQueryRequest {
  query: string;
  knowledge_base_ids: string[];
  has_image?: boolean;
  image_url?: string | null;
  context?: {
    conversation_id?: string;
    parent_node_id?: string;
    user_id?: string;
  };
  options?: {
    model?: 'deepseekV3' | 'doubao';
    temperature?: number;
    max_tokens?: number;
    stream?: boolean;
  };
}

// SSE事件类型
type AgenticRAGEvent =
  | { event: 'classification'; data: ClassificationData }
  | { event: 'routing'; data: RoutingData }
  | { event: 'processing'; data: ProcessingData }
  | { event: 'result'; data: ResultData }
  | { event: 'sources'; data: SourcesData }
  | { event: 'error'; data: ErrorData }
  | { event: 'done'; data: DoneData };

interface ClassificationData {
  input_type: string;
  input_type_confidence: number;
  intent: string;
  intent_confidence: number;
  channel: string;
}

interface RoutingData {
  channel: string;
  reason: string;
  fallback?: boolean;
  original_channel?: string;
}

interface ProcessingData {
  status: string;
  message: string;
  [key: string]: any;
}

interface ResultData {
  success: boolean;
  answer?: string;
  problem?: any;
  solution?: any;
  confidence?: number;
  [key: string]: any;
}

interface SourcesData {
  sources: Array<{
    type: string;
    content: string;
    metadata: Record<string, any>;
    score: number;
  }>;
}

interface ErrorData {
  error: string;
  detail?: string;
  code?: string;
}

interface DoneData {
  task_id: string;
  total_time: number;
}
```

---

### 2. 图片上传和识别接口

**端点**: `POST /api/v1/rag/agentic/upload-image`

**功能**: 上传图片，自动路由到VisionChannel或降级处理

**请求**: `multipart/form-data`
- `file`: 图片文件
- `query`: 用户问题文本（可选）
- `knowledge_base_ids`: 知识库ID列表（JSON字符串）

**响应** (SSE流式):
```
event: upload
data: {"status": "uploaded", "image_url": "https://..."}

event: vision
data: {"status": "recognizing", "message": "正在识别电路图..."}

event: vision_result
data: {"status": "recognized", "circuit_description": "...", "components": [...]}

event: routing
data: {"channel": "REASONING", "reason": "图片识别后转向电路分析"}

event: result
data: {"success": true, "answer": "..."}

event: done
data: {"task_id": "task_456"}
```

---

### 3. 知识库管理接口

#### 3.1 获取知识库列表

**端点**: `GET /api/v1/rag/knowledge-bases`

**查询参数**:
- `user_id`: 用户ID（可选，用于过滤用户知识库）

**响应**:
```json
{
  "success": true,
  "data": [
    {
      "id": "kb_001",
      "name": "电路基础教材",
      "description": "大学电路基础课程教材",
      "document_count": 15,
      "circuit_count": 120,
      "text_chunk_count": 3500,
      "created_at": "2025-01-01T00:00:00Z",
      "updated_at": "2025-12-15T10:30:00Z"
    }
  ]
}
```

#### 3.2 知识库详情

**端点**: `GET /api/v1/rag/knowledge-bases/{kb_id}`

**响应**:
```json
{
  "success": true,
  "data": {
    "id": "kb_001",
    "name": "电路基础教材",
    "description": "...",
    "statistics": {
      "document_count": 15,
      "circuit_count": 120,
      "text_chunk_count": 3500,
      "avg_chunk_length": 512,
      "embedding_model": "bge-large-zh-v1.5"
    },
    "documents": [
      {
        "id": "doc_001",
        "filename": "电路基础.pdf",
        "pages": 350,
        "circuits": 45,
        "text_chunks": 890
      }
    ]
  }
}
```

---

### 4. 路由统计接口

**端点**: `GET /api/v1/rag/agentic/stats`

**功能**: 获取路由统计信息，用于调试和监控

**响应**:
```json
{
  "success": true,
  "data": {
    "available_channels": ["KNOWLEDGE", "FORMULA"],
    "pending_channels": ["NETLIST", "VISION", "REASONING"],
    "recent_queries": [
      {
        "query": "R1=10k, R2=20k, 求分压",
        "input_type": "FORMULA_PROBLEM",
        "intent": "COMPUTATION",
        "channel": "FORMULA",
        "success": true,
        "timestamp": "2025-12-16T04:30:00Z"
      }
    ],
    "routing_accuracy": {
      "total": 1000,
      "correct": 980,
      "accuracy": 0.98
    }
  }
}
```

---

## Flow API 集成

### 扩展现有 `/flow/tasks` 端点

**新增任务类型**: `AGENTIC_RAG_V2`

**请求体扩展**:
```typescript
interface CreateAiTaskDTO {
  type: AiTaskType | "AGENTIC_RAG_V2";  // 新增类型
  prompt: string | null;
  promptParams: Record<string, string> | null;
  convId: number | null;
  parentId: number | null;
  model: string | null;
  classId: string | null;

  // 新增：Agentic RAG 配置
  agenticConfig?: {
    knowledgeBaseIds: string[];        // 知识库ID列表
    hasImage?: boolean;                // 是否包含图片
    imageUrl?: string;                 // 图片URL
    channelPreference?: string;        // 频道偏好（可选）
  };
}
```

**后端处理逻辑** (Java):
```java
if (taskType == AiTaskType.AGENTIC_RAG_V2) {
    // 调用 Python RAG 服务
    AgenticRAGQueryRequest request = new AgenticRAGQueryRequest();
    request.setQuery(prompt);
    request.setKnowledgeBaseIds(agenticConfig.getKnowledgeBaseIds());
    request.setHasImage(agenticConfig.getHasImage());
    request.setImageUrl(agenticConfig.getImageUrl());

    // 获取 SSE 流
    SseEmitter emitter = agenticRagService.query(request);

    // 转换 Python SSE 事件到 Flow ChatTask 格式
    // ...
}
```

---

## SSE 消息格式映射

### Python → Java → 前端映射规则

| Python Event | Java ChatTask Type | 前端处理 |
|-------------|-------------------|---------|
| `classification` | `data` | 显示分类结果（调试面板） |
| `routing` | `data` | 显示路由决策 |
| `processing` | `text` | 流式更新节点文本 |
| `result` | `node` + `data` | 创建结果节点 |
| `sources` | `node` | 创建来源节点 |
| `error` | `error` | 显示错误提示 |
| `done` | `done` | 标记任务完成 |

### 示例转换逻辑 (Java)

```java
public ChatTask convertPythonEventToFlowTask(AgenticRAGEvent pythonEvent) {
    switch (pythonEvent.getEvent()) {
        case "classification":
            // 转换为 data 类型，包含分类信息
            return ChatTask.data(nodeId, Map.of(
                "classification", pythonEvent.getData()
            ));

        case "processing":
            // 转换为 text 类型，流式更新
            ProcessingData data = pythonEvent.getData();
            return ChatTask.text(nodeId, data.getMessage());

        case "result":
            // 转换为 node 类型，创建答案节点
            ResultData result = pythonEvent.getData();
            if (result.getChannel().equals("FORMULA")) {
                return ChatTask.node(createFormulaResultNode(result));
            } else if (result.getChannel().equals("KNOWLEDGE")) {
                return ChatTask.node(createKnowledgeAnswerNode(result));
            }
            break;

        case "sources":
            // 创建来源节点
            SourcesData sources = pythonEvent.getData();
            return ChatTask.node(createSourcesNode(sources));

        case "error":
            return ChatTask.error(pythonEvent.getData().getError());

        case "done":
            return ChatTask.done();
    }
}
```

---

## 节点类型扩展

### 新增 Flow 节点类型

#### 1. `agentic-rag-query` - RAG查询节点

```typescript
interface AgenticRAGQueryNodeData {
  query: string;
  classification: {
    input_type: string;
    intent: string;
  };
  routing: {
    channel: string;
    reason: string;
  };
}
```

#### 2. `agentic-rag-result` - RAG结果节点

```typescript
interface AgenticRAGResultNodeData {
  channel: string;
  answer: string;
  confidence?: number;

  // FormulaChannel 特定
  problem?: {
    given: Record<string, any>;
    unknown: string;
    formula_type: string;
  };
  solution?: {
    steps: string[];
    result: any;
  };

  // KnowledgeChannel 特定
  sources?: Array<{
    type: string;
    content: string;
    metadata: Record<string, any>;
    score: number;
  }>;
}
```

#### 3. `agentic-rag-sources` - 来源标注节点

```typescript
interface AgenticRAGSourcesNodeData {
  sources: Array<{
    id: string;
    type: 'text_chunk' | 'circuit_diagram';
    title: string;
    excerpt: string;
    metadata: {
      filename: string;
      page?: number;
    };
    relevance: number;
  }>;
}
```

---

## 错误处理规范

### Python错误码定义

```python
class AgenticRAGErrorCode(str, Enum):
    # 分类错误
    CLASSIFICATION_FAILED = "CLASSIFICATION_FAILED"

    # 路由错误
    ROUTING_FAILED = "ROUTING_FAILED"
    CHANNEL_NOT_FOUND = "CHANNEL_NOT_FOUND"

    # 频道执行错误
    FORMULA_PARSE_ERROR = "FORMULA_PARSE_ERROR"
    KNOWLEDGE_SEARCH_FAILED = "KNOWLEDGE_SEARCH_FAILED"
    VISION_RECOGNITION_FAILED = "VISION_RECOGNITION_FAILED"

    # 知识库错误
    KB_NOT_FOUND = "KB_NOT_FOUND"
    KB_EMPTY = "KB_EMPTY"

    # 系统错误
    INTERNAL_ERROR = "INTERNAL_ERROR"
    TIMEOUT = "TIMEOUT"
```

### 错误响应格式

```json
{
  "success": false,
  "error": {
    "code": "FORMULA_PARSE_ERROR",
    "message": "无法解析公式输入",
    "detail": "未能提取到已知条件和待求量",
    "suggestion": "请确保输入格式为：R1=10k, R2=20k, 求Vout"
  }
}
```

---

## 前端集成指南

### 1. 新增 Hook: `useAgenticRAG`

```typescript
interface UseAgenticRAGOptions {
  knowledgeBaseIds: string[];
  onClassification?: (data: ClassificationData) => void;
  onRouting?: (data: RoutingData) => void;
  onProcessing?: (data: ProcessingData) => void;
  onResult?: (data: ResultData) => void;
  onSources?: (data: SourcesData) => void;
  onError?: (error: ErrorData) => void;
}

const useAgenticRAG = (options: UseAgenticRAGOptions) => {
  const [status, setStatus] = useState<'idle' | 'processing' | 'done' | 'error'>('idle');
  const [classification, setClassification] = useState<ClassificationData | null>(null);
  const [routing, setRouting] = useState<RoutingData | null>(null);
  const [result, setResult] = useState<ResultData | null>(null);
  const [sources, setSources] = useState<SourcesData['sources']>([]);

  const query = async (queryText: string, hasImage?: boolean, imageUrl?: string) => {
    setStatus('processing');

    // 发送请求到 Java Flow API
    const taskResponse = await createAiTask({
      type: 'AGENTIC_RAG_V2',
      prompt: queryText,
      agenticConfig: {
        knowledgeBaseIds: options.knowledgeBaseIds,
        hasImage,
        imageUrl
      }
    });

    // 建立 SSE 连接
    const source = new SSE(`/flow/completion?taskId=${taskResponse.taskId}`);

    source.addEventListener('message', (e) => {
      const data = JSON.parse(e.data);

      if (data.type === 'data' && data.data.classification) {
        setClassification(data.data.classification);
        options.onClassification?.(data.data.classification);
      } else if (data.type === 'data' && data.data.routing) {
        setRouting(data.data.routing);
        options.onRouting?.(data.data.routing);
      } else if (data.type === 'text') {
        options.onProcessing?.({ status: 'processing', message: data.data.content });
      } else if (data.type === 'node') {
        setResult(data.data.data);
        options.onResult?.(data.data.data);
      } else if (data.type === 'done') {
        setStatus('done');
      } else if (data.type === 'error') {
        setStatus('error');
        options.onError?.({ error: data.data });
      }
    });

    source.stream();
  };

  return {
    status,
    classification,
    routing,
    result,
    sources,
    query
  };
};
```

### 2. 扩展 FlowInputPanel

在 `FlowInputPanel.tsx` 中添加知识库选择器：

```typescript
const [selectedKnowledgeBases, setSelectedKnowledgeBases] = useState<string[]>([]);

// 渲染知识库选择器
<KnowledgeBaseSelector
  value={selectedKnowledgeBases}
  onChange={setSelectedKnowledgeBases}
/>

// 提交时传递配置
const handleSubmit = async () => {
  await createAiTask({
    type: 'AGENTIC_RAG_V2',
    prompt: inputValue,
    agenticConfig: {
      knowledgeBaseIds: selectedKnowledgeBases,
      hasImage: uploadedImage !== null,
      imageUrl: uploadedImageUrl
    }
  });
};
```

---

## 性能优化建议

### 1. 缓存机制

```python
# Python端：缓存分类结果
@lru_cache(maxsize=1000)
def classify_input_type(query: str, has_image: bool) -> Dict:
    # ...
```

### 2. 批量处理

```python
# 支持批量查询（可选）
@router.post("/api/v1/rag/agentic/batch-query")
async def batch_query(requests: List[AgenticRAGQueryRequest]):
    # 并发处理多个查询
    results = await asyncio.gather(*[
        route_and_process(req) for req in requests
    ])
    return results
```

### 3. 超时控制

```python
# 设置合理的超时
CLASSIFICATION_TIMEOUT = 5  # 秒
CHANNEL_EXECUTION_TIMEOUT = 30  # 秒

async def query_with_timeout(request: AgenticRAGQueryRequest):
    try:
        async with asyncio.timeout(CHANNEL_EXECUTION_TIMEOUT):
            result = await adaptive_router.route(...)
            return result
    except asyncio.TimeoutError:
        raise HTTPException(
            status_code=408,
            detail="查询超时，请稍后重试"
        )
```

---

## 安全性考虑

### 1. 权限验证

```python
# 验证用户是否有权访问知识库
async def verify_kb_access(user_id: str, kb_ids: List[str]):
    for kb_id in kb_ids:
        if not await kb_service.check_access(user_id, kb_id):
            raise HTTPException(
                status_code=403,
                detail=f"无权访问知识库: {kb_id}"
            )
```

### 2. 输入验证

```python
# 限制查询长度
class AgenticRAGQueryRequest(BaseModel):
    query: str = Field(..., min_length=1, max_length=2000)
    knowledge_base_ids: List[str] = Field(..., max_items=10)
```

### 3. 速率限制

```python
from slowapi import Limiter
from slowapi.util import get_remote_address

limiter = Limiter(key_func=get_remote_address)

@router.post("/api/v1/rag/agentic/query")
@limiter.limit("10/minute")  # 每分钟最多10次查询
async def query(request: AgenticRAGQueryRequest):
    # ...
```

---

## 监控和日志

### 1. 结构化日志

```python
import structlog

logger = structlog.get_logger()

logger.info(
    "agentic_rag_query",
    user_id=user_id,
    query_length=len(request.query),
    kb_count=len(request.knowledge_base_ids),
    has_image=request.has_image,
    input_type=classification["input_type"],
    intent=classification["intent"],
    channel=routing["channel"],
    success=result["success"],
    processing_time=elapsed_time
)
```

### 2. 指标收集

```python
from prometheus_client import Counter, Histogram

rag_query_counter = Counter(
    'agentic_rag_queries_total',
    'Total number of Agentic RAG queries',
    ['channel', 'success']
)

rag_query_duration = Histogram(
    'agentic_rag_query_duration_seconds',
    'Duration of Agentic RAG queries',
    ['channel']
)
```

---

## 测试建议

### 1. 单元测试

```python
@pytest.mark.asyncio
async def test_formula_query():
    request = AgenticRAGQueryRequest(
        query="R1=10kΩ, R2=20kΩ, Vin=12V, 求Vout",
        knowledge_base_ids=["kb_test"]
    )

    result = await query_handler(request)

    assert result["success"] is True
    assert result["classification"]["input_type"] == "FORMULA_PROBLEM"
    assert result["classification"]["intent"] == "COMPUTATION"
    assert result["routing"]["channel"] == "FORMULA"
    assert "V_out" in result["result"]["solution"]["result"]
```

### 2. 集成测试

```typescript
describe('AgenticRAG Flow Integration', () => {
  it('should create task and receive SSE events', async () => {
    const task = await createAiTask({
      type: 'AGENTIC_RAG_V2',
      prompt: '什么是基尔霍夫定律？',
      agenticConfig: {
        knowledgeBaseIds: ['kb_001']
      }
    });

    expect(task.taskId).toBeDefined();

    const events = await collectSSEEvents(task.taskId);

    expect(events).toContainEqual(
      expect.objectContaining({ type: 'data', data: { classification: expect.any(Object) } })
    );
    expect(events).toContainEqual(
      expect.objectContaining({ type: 'done' })
    );
  });
});
```

---

## 部署配置

### 1. 环境变量

```bash
# Python服务
AGENTIC_RAG_CLASSIFICATION_TIMEOUT=5
AGENTIC_RAG_CHANNEL_TIMEOUT=30
AGENTIC_RAG_MAX_CONCURRENT=10

# Java服务
PYTHON_RAG_SERVICE_URL=http://localhost:8001
PYTHON_RAG_CONNECTION_TIMEOUT=60000
PYTHON_RAG_READ_TIMEOUT=120000
```

### 2. Docker配置

```yaml
# docker-compose.yml
services:
  python-rag:
    build: ./drawsee-rag-python
    environment:
      - AGENTIC_RAG_ENABLED=true
    ports:
      - "8001:8001"

  java-backend:
    build: ./drawsee-java
    environment:
      - PYTHON_RAG_SERVICE_URL=http://python-rag:8001
    depends_on:
      - python-rag
```

---

**文档版本**: 1.0
**状态**: ✅ 设计完成，可进入实现阶段
**下一步**: 实现Python RAG API endpoint

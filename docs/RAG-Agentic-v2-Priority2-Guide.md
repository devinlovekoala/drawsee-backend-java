# Agentic RAG v2 Priority 2 实现指南

**开发阶段**: Priority 2 - Java集成和API对接
**日期**: 2025-12-16
**状态**: 设计完成，实现进行中

---

## 已完成工作总结

### ✅ Priority 1 - 基础路由集成（100%完成）

1. **InputTypeClassifier** - 输入类型分类器
2. **IntentClassifier v2** - 意图分类器（含COMPUTATION）
3. **FormulaChannel** - 公式计算频道
4. **KnowledgeChannel** - 知识问答频道
5. **AdaptiveChannelRouter** - 自适应路由器

**文档**:
- [RAG-Agentic-v2-Progress.md](/home/devin/Workspace/drawsee-platform/drawsee-java/docs/RAG-Agentic-v2-Progress.md) - 实现进度
- [RAG-Agentic-v2-Priority1-Complete.md](/home/devin/Workspace/drawsee-platform/drawsee-java/docs/RAG-Agentic-v2-Priority1-Complete.md) - 完成报告
- [RAG-Agentic-API-Design.md](/home/devin/Workspace/drawsee-platform/drawsee-java/docs/RAG-Agentic-API-Design.md) - API接口设计

### ✅ Priority 2 - API设计（100%完成）

1. **前端架构分析** - 完整分析drawsee-web会话系统
2. **API接口规范** - Python ↔ Java ↔ 前端数据格式
3. **集成架构设计** - 三层架构（前端-Java-Python）

---

## Priority 2 实现任务清单

### 第1步：Python API实现

#### 1.1 创建Pydantic模型

**文件**: `/home/devin/Workspace/python/drawsee-rag-python/app/api/models/agentic.py`

```python
"""
Agentic RAG API 数据模型
"""
from typing import List, Dict, Any, Optional
from pydantic import BaseModel, Field
from enum import Enum

# 输入类型枚举
class InputTypeEnum(str, Enum):
    NATURAL_QA = "NATURAL_QA"
    FORMULA_PROBLEM = "FORMULA_PROBLEM"
    CIRCUIT_NETLIST = "CIRCUIT_NETLIST"
    CIRCUIT_DIAGRAM = "CIRCUIT_DIAGRAM"
    MIXED = "MIXED"

# 意图类型枚举
class IntentEnum(str, Enum):
    CONCEPT = "CONCEPT"
    RULE = "RULE"
    COMPUTATION = "COMPUTATION"
    ANALYSIS = "ANALYSIS"
    DEBUG = "DEBUG"

# 频道类型枚举
class ChannelEnum(str, Enum):
    KNOWLEDGE = "KNOWLEDGE"
    FORMULA = "FORMULA"
    NETLIST = "NETLIST"
    VISION = "VISION"
    REASONING = "REASONING"

# 请求模型
class AgenticQueryRequest(BaseModel):
    query: str = Field(..., description="用户问题", min_length=1)
    knowledge_base_ids: List[str] = Field(..., description="知识库ID列表")
    input_context: dict = Field(default_factory=dict)
    options: dict = Field(default_factory=dict)

# 响应模型
class AgenticQueryResponse(BaseModel):
    success: bool
    request_id: str
    channel: str
    classification: dict
    result: dict
    routing_reason: str
    fallback: bool = False
    timestamp: str

# 更多模型见完整代码...
```

#### 1.2 创建API路由

**文件**: `/home/devin/Workspace/python/drawsee-rag-python/app/api/v1/agentic.py`

```python
"""
Agentic RAG API 路由
"""
from fastapi import APIRouter, HTTPException
from app.api.models.agentic import (
    AgenticQueryRequest,
    AgenticQueryResponse,
    InputTypeClassificationRequest,
    InputTypeClassificationResponse,
    IntentClassificationRequest,
    IntentClassificationResponse,
    ChannelsStatusResponse
)
from app.services.agentic.adaptive_router import adaptive_router
from app.services.agentic.input_type_classifier import input_type_classifier
from app.services.agentic.intent_classifier import intent_classifier
import uuid
from datetime import datetime
import logging

logger = logging.getLogger(__name__)
router = APIRouter(prefix="/rag", tags=["Agentic RAG"])


@router.post("/agentic-query", response_model=AgenticQueryResponse)
async def agentic_query(request: AgenticQueryRequest):
    """
    Agentic RAG v2 统一查询接口（同步）

    Args:
        request: 查询请求

    Returns:
        AgenticQueryResponse: 查询结果
    """
    request_id = f"req_{datetime.now().strftime('%Y%m%d_%H%M%S')}_{uuid.uuid4().hex[:8]}"

    try:
        logger.info(f"[{request_id}] Agentic查询: query='{request.query[:50]}...'")

        # 调用自适应路由器
        routing_result = await adaptive_router.route(
            query=request.query,
            knowledge_base_ids=request.knowledge_base_ids,
            has_image=request.input_context.get('has_image', False),
            image_url=request.input_context.get('image_url'),
            context={
                'model': request.options.get('model', 'deepseek-v3'),
                'temperature': request.options.get('temperature', 0.3),
                'enable_followup': request.options.get('enable_followup', True),
                'max_sources': request.options.get('max_sources', 5),
                'netlist_content': request.input_context.get('netlist_content'),
                'conversation_history': request.input_context.get('conversation_history', [])
            }
        )

        if not routing_result['success']:
            raise HTTPException(
                status_code=500,
                detail={
                    'code': 'AGENTIC_ROUTING_FAILED',
                    'message': '路由处理失败',
                    'detail': routing_result.get('error', 'Unknown error')
                }
            )

        # 构建响应
        channel_result = routing_result['result']

        response = AgenticQueryResponse(
            success=True,
            request_id=request_id,
            channel=routing_result['channel'],
            classification={
                'input_type': routing_result['classification']['input_type'],
                'input_type_confidence': routing_result['classification']['input_type_confidence'],
                'intent': routing_result['classification']['intent'],
                'intent_confidence': routing_result['classification']['intent_confidence']
            },
            result={
                'answer': channel_result.get('answer', ''),
                'answer_type': routing_result['channel'].lower(),
                'confidence': channel_result.get('confidence', 0.9),
                'sources': channel_result.get('sources', []),
                'followup_suggestions': channel_result.get('followup_suggestions', []),
                'metadata': channel_result.get('metadata', {})
            },
            routing_reason=routing_result['routing_reason'],
            fallback=routing_result.get('result', {}).get('fallback', False),
            timestamp=datetime.now().isoformat()
        )

        logger.info(
            f"[{request_id}] 处理完成: channel={response.channel}, "
            f"fallback={response.fallback}"
        )

        return response

    except HTTPException:
        raise
    except Exception as e:
        logger.error(f"[{request_id}] Agentic查询失败: {e}", exc_info=True)
        raise HTTPException(
            status_code=500,
            detail={
                'code': 'INTERNAL_SERVER_ERROR',
                'message': 'Agentic查询失败',
                'detail': str(e),
                'request_id': request_id
            }
        )


@router.post("/classify-input-type", response_model=InputTypeClassificationResponse)
async def classify_input_type(request: InputTypeClassificationRequest):
    """
    输入类型快速分类

    用途: 前端提交前预判输入类型
    """
    try:
        result = await input_type_classifier.classify(
            query=request.query,
            has_image=request.has_image
        )

        suggestions = []
        input_type = result['input_type']

        if input_type == 'FORMULA_PROBLEM':
            suggestions.append("检测到公式计算问题，系统将使用确定性计算引擎（100%准确）")
        elif input_type == 'CIRCUIT_NETLIST':
            suggestions.append("检测到SPICE Netlist，系统将使用形式化语言解析引擎")
        elif input_type == 'CIRCUIT_DIAGRAM':
            suggestions.append("检测到电路图，系统将使用图像识别引擎")
        elif input_type == 'NATURAL_QA':
            suggestions.append("这是一个自然语言问题，系统将使用知识库检索")

        return InputTypeClassificationResponse(
            input_type=result['input_type'],
            confidence=result['confidence'],
            reasoning=result.get('reasoning', ''),
            suggestions=suggestions
        )

    except Exception as e:
        logger.error(f"输入类型分类失败: {e}")
        raise HTTPException(status_code=500, detail=str(e))


@router.post("/classify-intent", response_model=IntentClassificationResponse)
async def classify_intent(request: IntentClassificationRequest):
    """
    意图快速分类
    """
    try:
        result = await intent_classifier.classify(
            query=request.query,
            input_type=request.input_type
        )

        return IntentClassificationResponse(
            intent=result['intent'],
            confidence=result['confidence'],
            reasoning=result.get('reasoning', '')
        )

    except Exception as e:
        logger.error(f"意图分类失败: {e}")
        raise HTTPException(status_code=500, detail=str(e))


@router.get("/channels/status", response_model=ChannelsStatusResponse)
async def get_channels_status():
    """
    获取所有频道的当前状态
    """
    try:
        stats = adaptive_router.get_routing_stats()

        channel_details = {}

        # KNOWLEDGE频道
        if 'KNOWLEDGE' in stats['available_channels']:
            channel_details['KNOWLEDGE'] = {
                'status': 'available',
                'version': 'v2.0',
                'capabilities': [
                    'concept_qa',
                    'rule_qa',
                    'source_citation',
                    'forced_rag',
                    'honesty_principle'
                ]
            }

        # FORMULA频道
        if 'FORMULA' in stats['available_channels']:
            channel_details['FORMULA'] = {
                'status': 'available',
                'version': 'v2.0',
                'capabilities': [
                    'symbolic_computation',
                    '6_formula_types',
                    'unit_conversion',
                    'step_by_step_solution'
                ]
            }

        # 待实现频道
        for channel in stats['pending_channels']:
            channel_details[channel] = {
                'status': 'pending',
                'estimated_completion': '2025-12-20'
            }

        return ChannelsStatusResponse(
            available_channels=stats['available_channels'],
            pending_channels=stats['pending_channels'],
            channel_details=channel_details
        )

    except Exception as e:
        logger.error(f"获取频道状态失败: {e}")
        raise HTTPException(status_code=500, detail=str(e))
```

#### 1.3 注册路由到FastAPI

**文件**: `/home/devin/Workspace/python/drawsee-rag-python/app/main.py`

```python
# 导入agentic路由
from app.api.v1 import agentic

# 注册路由
app.include_router(agentic.router, prefix="/api/v1")
```

---

### 第2步：Java服务实现

#### 2.1 创建Java DTO类

**文件**: `drawsee-java/src/main/java/cn/yifan/drawsee/dto/agentic/AgenticQueryRequest.java`

```java
package cn.yifan.drawsee.dto.agentic;

import lombok.Builder;
import lombok.Data;
import java.util.List;
import java.util.Map;

@Data
@Builder
public class AgenticQueryRequest {
    private String query;
    private List<String> knowledgeBaseIds;
    private AgenticInputContext inputContext;
    private AgenticQueryOptions options;
}
```

**文件**: `drawsee-java/src/main/java/cn/yifan/drawsee/dto/agentic/AgenticInputContext.java`

```java
package cn.yifan.drawsee.dto.agentic;

import lombok.Builder;
import lombok.Data;
import java.util.List;

@Data
@Builder
public class AgenticInputContext {
    private Boolean hasImage;
    private String imageUrl;
    private String netlistContent;
    private List<ConversationMessage> conversationHistory;
}
```

**文件**: `drawsee-java/src/main/java/cn/yifan/drawsee/dto/agentic/AgenticQueryResponse.java`

```java
package cn.yifan.drawsee.dto.agentic;

import lombok.Data;
import java.util.List;
import java.util.Map;

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

@Data
class AgenticClassification {
    private String inputType;
    private Double inputTypeConfidence;
    private String intent;
    private Double intentConfidence;
}

@Data
class AgenticResult {
    private String answer;
    private String answerType;
    private Double confidence;
    private List<SourceItem> sources;
    private List<String> followupSuggestions;
    private Map<String, Object> metadata;
}

@Data
class SourceItem {
    private String type;
    private String content;
    private Map<String, Object> metadata;
    private Double score;
}
```

#### 2.2 创建AgenticRAGService

**文件**: `drawsee-java/src/main/java/cn/yifan/drawsee/service/base/AgenticRagService.java`

```java
package cn.yifan.drawsee.service.base;

import cn.yifan.drawsee.dto.agentic.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import javax.annotation.Resource;
import java.util.List;

@Slf4j
@Service
public class AgenticRagService {

    @Value("${python.rag.base-url}")
    private String pythonRagBaseUrl;  // http://localhost:8000

    @Resource
    private RestTemplate restTemplate;

    /**
     * Agentic RAG v2 统一查询接口
     *
     * @param query             用户问题
     * @param knowledgeBaseIds  知识库ID列表
     * @param inputContext      输入上下文
     * @param options           查询选项
     * @return AgenticQueryResponse
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
                .inputContext(inputContext != null ? inputContext : AgenticInputContext.builder().build())
                .options(options != null ? options : AgenticQueryOptions.builder().build())
                .build();

        log.info("[AgenticRAG] 查询: query='{}', knowledgeBaseIds={}",
                 query.length() > 50 ? query.substring(0, 50) + "..." : query,
                 knowledgeBaseIds);

        try {
            ResponseEntity<AgenticQueryResponse> response = restTemplate.postForEntity(
                    url,
                    request,
                    AgenticQueryResponse.class
            );

            AgenticQueryResponse result = response.getBody();
            log.info("[AgenticRAG] 查询成功: channel={}, fallback={}",
                     result.getChannel(), result.getFallback());

            return result;

        } catch (Exception e) {
            log.error("[AgenticRAG] 查询失败: {}", e.getMessage(), e);
            throw new RuntimeException("Agentic RAG查询失败: " + e.getMessage(), e);
        }
    }

    /**
     * 输入类型快速分类
     */
    public InputTypeClassificationResponse classifyInputType(String query, Boolean hasImage) {
        String url = pythonRagBaseUrl + "/api/v1/rag/classify-input-type";

        InputTypeClassificationRequest request = new InputTypeClassificationRequest();
        request.setQuery(query);
        request.setHasImage(hasImage != null ? hasImage : false);

        try {
            ResponseEntity<InputTypeClassificationResponse> response = restTemplate.postForEntity(
                    url,
                    request,
                    InputTypeClassificationResponse.class
            );

            return response.getBody();

        } catch (Exception e) {
            log.error("[AgenticRAG] 输入类型分类失败: {}", e.getMessage());
            throw new RuntimeException("输入类型分类失败: " + e.getMessage(), e);
        }
    }

    /**
     * 意图快速分类
     */
    public IntentClassificationResponse classifyIntent(String query, String inputType) {
        String url = pythonRagBaseUrl + "/api/v1/rag/classify-intent";

        IntentClassificationRequest request = new IntentClassificationRequest();
        request.setQuery(query);
        request.setInputType(inputType);

        try {
            ResponseEntity<IntentClassificationResponse> response = restTemplate.postForEntity(
                    url,
                    request,
                    IntentClassificationResponse.class
            );

            return response.getBody();

        } catch (Exception e) {
            log.error("[AgenticRAG] 意图分类失败: {}", e.getMessage());
            throw new RuntimeException("意图分类失败: " + e.getMessage(), e);
        }
    }

    /**
     * 获取频道状态
     */
    public ChannelsStatusResponse getChannelsStatus() {
        String url = pythonRagBaseUrl + "/api/v1/rag/channels/status";

        try {
            ResponseEntity<ChannelsStatusResponse> response = restTemplate.getForEntity(
                    url,
                    ChannelsStatusResponse.class
            );

            return response.getBody();

        } catch (Exception e) {
            log.error("[AgenticRAG] 获取频道状态失败: {}", e.getMessage());
            throw new RuntimeException("获取频道状态失败: " + e.getMessage(), e);
        }
    }
}
```

#### 2.3 配置文件

**文件**: `drawsee-java/src/main/resources/application.properties`

```properties
# Python RAG服务配置
python.rag.base-url=http://localhost:8000
python.rag.timeout=30000
python.rag.retry=3
```

---

### 第3步：WorkFlow集成

#### 3.1 创建新的AiTaskType

**文件**: `drawsee-java/src/main/java/cn/yifan/drawsee/enums/AiTaskType.java`

```java
// 在现有AiTaskType枚举中添加
public enum AiTaskType {
    // ... 现有类型

    // Agentic RAG v2 类型（新增）
    AGENTIC_RAG,              // 通用Agentic RAG查询
    AGENTIC_RAG_FORMULA,      // 公式计算
    AGENTIC_RAG_NETLIST,      // Netlist解析
    AGENTIC_RAG_IMAGE,        // 图片分析
}
```

#### 3.2 创建AgenticTaskHandler

**文件**: `drawsee-java/src/main/java/cn/yifan/drawsee/service/task/handler/AgenticTaskHandler.java`

```java
package cn.yifan.drawsee.service.task.handler;

import cn.yifan.drawsee.dto.agentic.*;
import cn.yifan.drawsee.enums.AiTaskType;
import cn.yifan.drawsee.service.base.AgenticRagService;
import cn.yifan.drawsee.service.task.ChatTaskEmitter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.util.List;

@Slf4j
@Component
public class AgenticTaskHandler implements TaskHandler {

    @Resource
    private AgenticRagService agenticRagService;

    @Override
    public boolean supports(AiTaskType taskType) {
        return taskType == AiTaskType.AGENTIC_RAG ||
               taskType == AiTaskType.AGENTIC_RAG_FORMULA ||
               taskType == AiTaskType.AGENTIC_RAG_NETLIST ||
               taskType == AiTaskType.AGENTIC_RAG_IMAGE;
    }

    @Override
    public void handle(TaskContext context, ChatTaskEmitter emitter) {
        String query = context.getPrompt();
        List<String> knowledgeBaseIds = context.getKnowledgeBaseIds();
        String model = context.getModel();

        log.info("[AgenticTaskHandler] 处理任务: query='{}', type={}",
                 query.substring(0, Math.min(50, query.length())),
                 context.getTaskType());

        try {
            // 1. 发送node消息（创建查询节点）
            emitter.emitNode(createQueryNode(context));

            // 2. 构建输入上下文
            AgenticInputContext inputContext = AgenticInputContext.builder()
                    .hasImage(context.hasImage())
                    .imageUrl(context.getImageUrl())
                    .netlistContent(context.getNetlistContent())
                    .build();

            // 3. 构建查询选项
            AgenticQueryOptions options = AgenticQueryOptions.builder()
                    .model(model != null ? model : "deepseek-v3")
                    .temperature(0.3)
                    .enableFollowup(true)
                    .maxSources(5)
                    .build();

            // 4. 调用Agentic RAG服务
            AgenticQueryResponse response = agenticRagService.agenticQuery(
                    query,
                    knowledgeBaseIds,
                    inputContext,
                    options
            );

            if (!response.getSuccess()) {
                emitter.emitError("Agentic RAG查询失败");
                return;
            }

            // 5. 发送node消息（创建答案节点）
            NodeVO answerNode = createAnswerNode(context, response);
            emitter.emitNode(answerNode);

            // 6. 流式发送答案文本
            String answer = response.getResult().getAnswer();
            emitTextInChunks(answerNode.getId(), answer, emitter);

            // 7. 发送元数据
            emitter.emitData(answerNode.getId(), Map.of(
                    "channel", response.getChannel(),
                    "classification", response.getClassification(),
                    "sources", response.getResult().getSources(),
                    "followups", response.getResult().getFollowupSuggestions(),
                    "fallback", response.getFallback()
            ));

            // 8. 发送完成信号
            emitter.emitDone();

            log.info("[AgenticTaskHandler] 任务完成: channel={}", response.getChannel());

        } catch (Exception e) {
            log.error("[AgenticTaskHandler] 任务失败: {}", e.getMessage(), e);
            emitter.emitError("处理失败: " + e.getMessage());
        }
    }

    private NodeVO createQueryNode(TaskContext context) {
        // 创建查询节点逻辑...
    }

    private NodeVO createAnswerNode(TaskContext context, AgenticQueryResponse response) {
        // 创建答案节点逻辑...
    }

    private void emitTextInChunks(Long nodeId, String text, ChatTaskEmitter emitter) {
        // 分块发送文本（模拟流式）
        int chunkSize = 50;
        for (int i = 0; i < text.length(); i += chunkSize) {
            int end = Math.min(i + chunkSize, text.length());
            String chunk = text.substring(i, end);
            emitter.emitText(nodeId, chunk);

            try {
                Thread.sleep(20);  // 模拟流式延迟
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
    }
}
```

---

### 第4步：前端集成

#### 4.1 创建API方法

**文件**: `drawsee-web/src/api/methods/agentic.methods.ts`

```typescript
import { alova } from '../index';

export interface AgenticQueryRequest {
  query: string;
  knowledgeBaseIds: string[];
  inputContext?: {
    hasImage?: boolean;
    imageUrl?: string;
    netlistContent?: string;
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
    sources: Array<{
      type: string;
      content: string;
      metadata: Record<string, any>;
      score: number;
    }>;
    followupSuggestions: string[];
    metadata: Record<string, any>;
  };
  routingReason: string;
  fallback: boolean;
  timestamp: string;
}

/**
 * Agentic RAG查询（通过Java代理）
 */
export const agenticQuery = (request: AgenticQueryRequest) => {
  return alova.Post<AgenticQueryResponse>('/api/agentic/query', request);
};

/**
 * 输入类型分类
 */
export const classifyInputType = (query: string, hasImage: boolean = false) => {
  return alova.Post<{
    inputType: string;
    confidence: number;
    reasoning: string;
    suggestions: string[];
  }>('/api/agentic/classify-input-type', { query, hasImage });
};

/**
 * 获取频道状态
 */
export const getChannelsStatus = () => {
  return alova.Get<{
    availableChannels: string[];
    pendingChannels: string[];
    channelDetails: Record<string, any>;
  }>('/api/agentic/channels/status');
};
```

#### 4.2 更新AiTaskType

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
  | "AGENTIC_RAG"              // 新增
  | "AGENTIC_RAG_FORMULA"      // 新增
  | "AGENTIC_RAG_NETLIST"      // 新增
  | "AGENTIC_RAG_IMAGE";       // 新增
```

---

## 测试计划

### 单元测试

```bash
# Python端
cd /home/devin/Workspace/python/drawsee-rag-python
python test_agentic_router.py

# 测试API接口
curl -X POST http://localhost:8000/api/v1/rag/agentic-query \
  -H "Content-Type: application/json" \
  -d '{
    "query": "R1=10kΩ, R2=20kΩ, Vin=12V, 求Vout",
    "knowledge_base_ids": ["kb_001"]
  }'
```

### 集成测试

```bash
# Java端
mvn test -Dtest=AgenticRagServiceTest

# 端到端测试（前端 → Java → Python）
# 在前端控制台执行
import { agenticQuery } from '@/api/methods/agentic.methods';

const result = await agenticQuery({
  query: "什么是基尔霍夫定律？",
  knowledgeBaseIds: ["kb_001"],
  options: { model: "deepseekV3" }
});

console.log(result);
```

---

## 部署清单

### Python服务
- [ ] 创建API models
- [ ] 创建API routes
- [ ] 注册到FastAPI
- [ ] 测试API端点
- [ ] 更新requirements.txt

### Java服务
- [ ] 创建DTO类
- [ ] 创建AgenticRagService
- [ ] 创建AgenticTaskHandler
- [ ] 更新AiTaskType枚举
- [ ] 配置RestTemplate
- [ ] 测试服务调用

### 前端
- [ ] 创建API methods
- [ ] 更新AiTaskType
- [ ] 测试集成

---

## 下一步

完成Priority 2后，继续Priority 3：

1. NetlistChannel实现
2. VisionChannel实现（GLM-4V）
3. CircuitReasoningChannel实现（Agent + 工具链）

**预计完成时间**: 2025-12-20

---

**文档版本**: 1.0
**最后更新**: 2025-12-16
**状态**: 实现指南已完成，等待代码实现

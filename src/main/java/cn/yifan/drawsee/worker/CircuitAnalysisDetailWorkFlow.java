package cn.yifan.drawsee.worker;

import cn.yifan.drawsee.assistant.CircuitAnalysisAssistant;
import cn.yifan.drawsee.constant.AiModel;
import cn.yifan.drawsee.constant.AiTaskMessageType;
import cn.yifan.drawsee.constant.AiTaskStatus;
import cn.yifan.drawsee.constant.NodeSubType;
import cn.yifan.drawsee.constant.NodeTitle;
import cn.yifan.drawsee.constant.NodeType;
import cn.yifan.drawsee.converter.SpiceConverter;
import cn.yifan.drawsee.mapper.AiTaskMapper;
import cn.yifan.drawsee.mapper.ConversationMapper;
import cn.yifan.drawsee.mapper.NodeMapper;
import cn.yifan.drawsee.mapper.UserMapper;
import cn.yifan.drawsee.pojo.XYPosition;
import cn.yifan.drawsee.pojo.entity.AiTask;
import cn.yifan.drawsee.pojo.entity.CircuitDesign;
import cn.yifan.drawsee.pojo.entity.Node;
import cn.yifan.drawsee.pojo.rabbit.AiTaskMessage;
import cn.yifan.drawsee.service.base.AiService;
import cn.yifan.drawsee.service.base.PromptService;
import cn.yifan.drawsee.service.base.StreamAiService;
import cn.yifan.drawsee.service.business.KnowledgeBaseService;
import cn.yifan.drawsee.tool.AgenticRagTool;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.model.StreamingResponseHandler;
import dev.langchain4j.model.output.Response;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RStream;
import org.redisson.api.RedissonClient;
import org.redisson.api.stream.StreamAddArgs;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 电路分析追问工作流 - Tool-based架构
 *
 * 架构变更：
 * - 旧版：Python LLM生成完整答案，Java转发SSE流
 * - 新版：Java LangChain4j主导对话生成，Python仅提供RAG工具
 *
 * @author Drawsee Team
 */
@Slf4j
@Service
public class CircuitAnalysisDetailWorkFlow extends WorkFlow {

    private final PromptService promptService;
    private final SpiceConverter spiceConverter;
    private final KnowledgeBaseService knowledgeBaseService;
    private final AgenticRagTool agenticRagTool;

    public CircuitAnalysisDetailWorkFlow(
        UserMapper userMapper,
        AiService aiService,
        StreamAiService streamAiService,
        RedissonClient redissonClient,
        NodeMapper nodeMapper,
        ConversationMapper conversationMapper,
        AiTaskMapper aiTaskMapper,
        ObjectMapper objectMapper,
        PromptService promptService,
        SpiceConverter spiceConverter,
        KnowledgeBaseService knowledgeBaseService,
        ObjectProvider<AgenticRagTool> agenticRagToolProvider
    ) {
        super(userMapper, aiService, streamAiService, redissonClient, nodeMapper, conversationMapper, aiTaskMapper, objectMapper);
        this.promptService = promptService;
        this.spiceConverter = spiceConverter;
        this.knowledgeBaseService = knowledgeBaseService;
        this.agenticRagTool = agenticRagToolProvider.getIfAvailable();
    }

    @Override
    public Boolean validateAndInit(WorkContext workContext) {
        Boolean isValid = super.validateAndInit(workContext);
        if (!isValid) return false;

        Node parentNode = workContext.getParentNode();
        AiTaskMessage aiTaskMessage = workContext.getAiTaskMessage();

        if (!parentNode.getType().equals(NodeType.CIRCUIT_ANALYZE)) {
            log.error("父节点不是电路分析节点, taskMessage: {}", aiTaskMessage);
            return false;
        }

        CircuitDesign circuitDesign = findCircuitDesign(workContext);
        if (circuitDesign == null) {
            log.error("未在会话中找到电路画布节点, taskMessage: {}", aiTaskMessage);
            publishErrorAndFail(workContext, "未找到电路设计数据，请先完成电路画布解析后再追问");
            return false;
        }
        workContext.putExtraData("circuitDesign", circuitDesign);

        return true;
    }

    @Override
    public void createInitNodes(WorkContext workContext) throws JsonProcessingException {
        AiTaskMessage aiTaskMessage = workContext.getAiTaskMessage();
        Long parentNodeId = aiTaskMessage.getParentId();
        createInitStreamNode(workContext, parentNodeId);
    }

    @Override
    public void createInitStreamNode(WorkContext workContext, Long parentNodeId) throws JsonProcessingException {
        AiTaskMessage aiTaskMessage = workContext.getAiTaskMessage();
        Node parentNode = workContext.getParentNode();

        TypeReference<Map<String, Object>> dataTypeRef = new TypeReference<>() {};
        Map<String, Object> parentNodeData = objectMapper.readValue(parentNode.getData(), dataTypeRef);
        String contextTitle = parentNodeData.getOrDefault("contextTitle", parentNodeData.getOrDefault("title", "电路分析")).toString();
        String contextText = parentNodeData.getOrDefault("text", "").toString();
        String followUp = aiTaskMessage.getPrompt();

        Map<String, Object> circuitDetailNodeData = new ConcurrentHashMap<>();
        circuitDetailNodeData.put("subtype", NodeSubType.CIRCUIT_DETAIL);
        circuitDetailNodeData.put("title", NodeTitle.CIRCUIT_DETAIL);
        circuitDetailNodeData.put("text", "");
        circuitDetailNodeData.put("contextTitle", contextTitle);
        circuitDetailNodeData.put("contextText", contextText);
        circuitDetailNodeData.put("followUp", followUp);
        circuitDetailNodeData.put("followUps", new ArrayList<>());
        circuitDetailNodeData.put("parentPointId", String.valueOf(parentNodeId));

        Node circuitDetailNode = new Node(
            NodeType.CIRCUIT_ANALYZE,
            objectMapper.writeValueAsString(circuitDetailNodeData),
            objectMapper.writeValueAsString(new XYPosition(420, 0)),
            parentNodeId,
            aiTaskMessage.getUserId(),
            aiTaskMessage.getConvId(),
            true
        );
        insertAndPublishStreamNode(workContext, circuitDetailNode, circuitDetailNodeData);
        log.info("[CircuitAnalysisDetail-Tool] 创建追问解析节点, parentNodeId={}, detailNodeId={}", parentNodeId, circuitDetailNode.getId());
    }

    @Override
    public void streamChat(WorkContext workContext, StreamingResponseHandler<AiMessage> handler) throws JsonProcessingException {
        AiTaskMessage aiTaskMessage = workContext.getAiTaskMessage();
        Node parentNode = workContext.getParentNode();

        TypeReference<Map<String, Object>> dataTypeRef = new TypeReference<>() {};
        Map<String, Object> parentNodeData = objectMapper.readValue(parentNode.getData(), dataTypeRef);
        String contextTitle = parentNodeData.getOrDefault("contextTitle", parentNodeData.getOrDefault("title", "电路分析")).toString();
        String contextText = parentNodeData.getOrDefault("text", "").toString();
        String followUpQuestion = aiTaskMessage.getPrompt();

        // 获取电路设计数据
        CircuitDesign circuitDesign = (CircuitDesign) workContext.getExtraData("circuitDesign");
        if (circuitDesign == null) {
            circuitDesign = findCircuitDesign(workContext);
        }
        if (circuitDesign == null) {
            log.error("未找到电路设计数据, taskMessage: {}", aiTaskMessage);
            publishErrorAndFail(workContext, "未找到电路设计数据，请重新上传或刷新页面");
            return;
        }

        // 生成SPICE网表
        String spiceNetlist = spiceConverter.generateNetlist(circuitDesign);

        // 构建系统提示词
        String systemPrompt = buildSystemPrompt();
        if (agenticRagTool == null) {
            systemPrompt = systemPrompt + "\n\n【注意】当前环境未启用知识库检索工具，请不要生成任何tool调用内容。";
        }

        // 构建用户查询（包含上下文和电路信息）
        String userQuery = buildUserQuery(
            followUpQuestion, contextTitle, contextText, spiceNetlist, circuitDesign
        );

        // 获取用户可访问的知识库ID列表（用于Tool调用时的权限控制）
        List<String> knowledgeBaseIds = knowledgeBaseService.getUserAccessibleKnowledgeBaseIds(
            aiTaskMessage.getUserId(),
            aiTaskMessage.getClassId()
        );

        // 将知识库ID存入上下文，供AgenticRagTool使用
        workContext.putExtraData("knowledgeBaseIds", knowledgeBaseIds);
        workContext.putExtraData("userId", aiTaskMessage.getUserId());
        if (aiTaskMessage.getClassId() != null) {
            workContext.putExtraData("classId", aiTaskMessage.getClassId());
        }

        log.info("[CircuitAnalysisDetail-Tool] 开始Tool-based对话生成: question='{}...', kb_count={}",
                 followUpQuestion.length() > 50 ? followUpQuestion.substring(0, 50) : followUpQuestion,
                 knowledgeBaseIds.size());

        // 使用streamAiService的toolBasedChat方法
        streamAiService.toolBasedChat(
            systemPrompt,
            userQuery,
            agenticRagTool != null ? new Object[]{agenticRagTool} : new Object[]{},
            AiModel.DOUBAO,  // 使用豆包模型
            CircuitAnalysisAssistant.class,
            handler
        );
    }

    /**
     * 构建系统提示词
     */
    private String buildSystemPrompt() {
        return """
            你是一位专业的电路分析助教，负责帮助学生理解电路设计和工作原理。

            **重要规则**：
            - 当需要查询教材、课件、电路知识库中的内容时，使用searchKnowledgeBase工具
            - 当涉及电路理论、公式推导、元件特性时，优先调用searchKnowledgeBase工具
            - 对于电路结构分析、拓扑分析等可以直接基于提供的网表和电路图回答
            
            **回答要求**：
            1. 先判断电路类型/功能（如放大、滤波、整流等），说明判断依据。
            2. 标注关键器件与关键节点，必要时用KCL/KVL或等效模型做简要校验。
            3. 如果缺少参数或电路图信息，明确缺失项并给出需要补充的具体数据。
            4. 给出可操作的检查步骤或改进建议，突出电子电路实践可落地性。
            - 回答要准确、详细，结合理论知识和实际电路分析

            **输出格式**：
            - 使用清晰的Markdown格式
            - 对关键概念加粗或标注
            - 如果涉及公式，使用LaTeX格式
            - 结构化你的回答（分点、分段）

            请基于知识库内容和你的专业知识，为学生提供准确、详细的电路分析。
            """;
    }

    /**
     * 构建用户查询（包含上下文和电路信息）
     */
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
        query.append("- 元件数量: ").append(circuitDesign.getElements() != null ? circuitDesign.getElements().size() : 0).append("\n");
        query.append("- 连线数量: ").append(circuitDesign.getConnections() != null ? circuitDesign.getConnections().size() : 0).append("\n\n");

        // 添加SPICE网表（如果有）
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

    @Override
    public void createOtherNodesOrUpdateNodeData(WorkContext workContext) throws JsonProcessingException {
        RStream<String, Object> redisStream = workContext.getRedisStream();
        Node streamNode = workContext.getStreamNode();
        Map<String, Object> streamNodeData = workContext.getStreamNodeData();
        Response<AiMessage> streamResponse = workContext.getStreamResponse();
        String detailText = streamResponse.content().text();

        List<Map<String, Object>> followUpSuggestions = extractContinuationSuggestions(detailText);
        streamNodeData.put("text", detailText);
        streamNodeData.put("followUps", followUpSuggestions);
        streamNodeData.put("isGenerated", true);
        streamNodeData.put("isDone", true);
        log.info("[CircuitAnalysisDetail-Tool] 追问生成完成, nodeId={}, followUps={}", streamNode.getId(), followUpSuggestions.size());

        Map<String, Object> payload = new ConcurrentHashMap<>();
        payload.put("nodeId", streamNode.getId());
        payload.put("text", detailText);
        payload.put("followUps", followUpSuggestions);
        payload.put("isGenerated", true);
        payload.put("isDone", true);
        redisStream.add(StreamAddArgs.entries(
            "type", AiTaskMessageType.DATA,
            "data", payload
        ));
    }

    @Override
    public void updateStreamNode(WorkContext workContext) throws JsonProcessingException {
        Node streamNode = workContext.getStreamNode();
        Map<String, Object> streamNodeData = workContext.getStreamNodeData();
        streamNode.setIsDeleted(false);
        streamNode.setData(objectMapper.writeValueAsString(streamNodeData));
        workContext.getNodesToUpdate().add(streamNode);
    }

    private List<Map<String, Object>> extractContinuationSuggestions(String responseText) {
        if (responseText == null || responseText.isBlank()) {
            return Collections.emptyList();
        }
        String normalized = responseText.replace("\r", "");
        final String sectionFlag = "## 继续探索";
        int sectionIndex = normalized.indexOf(sectionFlag);
        if (sectionIndex < 0) {
            return Collections.emptyList();
        }
        String section = normalized.substring(sectionIndex + sectionFlag.length()).trim();
        if (section.isEmpty()) {
            return Collections.emptyList();
        }
        String[] lines = section.split("\n");
        List<String> suggestions = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        for (String rawLine : lines) {
            String line = rawLine.trim();
            if (line.startsWith("##")) {
                break;
            }
            if (line.isEmpty()) {
                if (current.length() > 0) {
                    suggestions.add(current.toString().trim());
                    current.setLength(0);
                }
                continue;
            }
            boolean isNewBullet = line.startsWith("-") || line.startsWith("*") || line.matches("^\\d+[\\.．、)]\\s*.*");
            if (isNewBullet && current.length() > 0) {
                suggestions.add(current.toString().trim());
                current.setLength(0);
            }
            if (isNewBullet) {
                current.append(normalizeSuggestionLine(line));
            } else if (current.length() > 0) {
                current.append(' ').append(line);
            }
        }
        if (current.length() > 0) {
            suggestions.add(current.toString().trim());
        }

        List<Map<String, Object>> followUps = new ArrayList<>();
        for (int i = 0; i < suggestions.size(); i++) {
            String suggestion = suggestions.get(i);
            if (suggestion.isEmpty()) {
                continue;
            }
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("title", "追问 " + (i + 1));
            map.put("hint", suggestion);
            map.put("followUp", ensureQuestionSentence(suggestion));
            followUps.add(map);
            if (followUps.size() >= 3) {
                break;
            }
        }
        return followUps;
    }

    private String normalizeSuggestionLine(String line) {
        String trimmed = line.trim();
        if (trimmed.startsWith("-") || trimmed.startsWith("*")) {
            trimmed = trimmed.substring(1).trim();
        }
        if (trimmed.matches("^\\d+[\\.．、)]\\s*.*")) {
            trimmed = trimmed.replaceFirst("^\\d+[\\.．、)]\\s*", "");
        }
        return trimmed.trim();
    }

    private String ensureQuestionSentence(String text) {
        if (text == null) {
            return "";
        }
        String trimmed = text.trim();
        if (trimmed.isEmpty()) {
            return "";
        }
        if (trimmed.endsWith("？") || trimmed.endsWith("?")) {
            return trimmed;
        }
        if (trimmed.endsWith("。") || trimmed.endsWith(".")) {
            trimmed = trimmed.substring(0, trimmed.length() - 1).trim();
        }
        return trimmed + "？";
    }

    private CircuitDesign findCircuitDesign(WorkContext workContext) {
        Node parentNode = workContext.getParentNode();
        Long convId = parentNode.getConvId();
        List<Node> nodes = nodeMapper.getByConvId(convId);
        Map<Long, Node> nodeMap = nodes.stream().collect(ConcurrentHashMap::new, (map, node) -> map.put(node.getId(), node), ConcurrentHashMap::putAll);

        Node currentNode = parentNode;
        while (currentNode != null && !currentNode.getType().equals(NodeType.ROOT)) {
            Node nextNode = nodeMap.get(currentNode.getParentId());

            if (nextNode != null && nextNode.getType().equals(NodeType.CIRCUIT_CANVAS)) {
                try {
                    TypeReference<Map<String, Object>> dataTypeRef = new TypeReference<>() {};
                    Map<String, Object> canvasNodeData = objectMapper.readValue(nextNode.getData(), dataTypeRef);
                    Object circuitDesignObj = canvasNodeData.get("circuitDesign");

                    String circuitDesignJson = objectMapper.writeValueAsString(circuitDesignObj);
                    return objectMapper.readValue(circuitDesignJson, CircuitDesign.class);
                } catch (JsonProcessingException e) {
                    log.error("解析电路画布节点数据失败: {}", e.getMessage());
                    return null;
                }
            }

            currentNode = nextNode;
        }

        return null;
    }

    private void publishErrorAndFail(WorkContext workContext, String message) {
        try {
            RStream<String, Object> redisStream = workContext.getRedisStream();
            if (redisStream != null) {
                redisStream.add(StreamAddArgs.entries(
                    "type", AiTaskMessageType.ERROR,
                    "data", message
                ));
                redisStream.add(StreamAddArgs.entries(
                    "type", AiTaskMessageType.DONE,
                    "data", ""
                ));
            }
        } catch (Exception e) {
            log.warn("推送电路详情错误消息失败: {}", e.getMessage());
        }

        try {
            AiTask aiTask = workContext.getAiTask();
            aiTask.setStatus(AiTaskStatus.FAILED);
            aiTask.setResult(message);
            aiTaskMapper.update(aiTask);
        } catch (Exception e) {
            log.warn("更新电路详情任务状态失败: {}", e.getMessage());
        }
    }
}

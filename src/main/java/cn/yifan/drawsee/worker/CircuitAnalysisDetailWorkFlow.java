package cn.yifan.drawsee.worker;

import cn.yifan.drawsee.constant.AiTaskMessageType;
import cn.yifan.drawsee.constant.NodeSubType;
import cn.yifan.drawsee.constant.NodeTitle;
import cn.yifan.drawsee.constant.NodeType;
import cn.yifan.drawsee.converter.SpiceConverter;
import cn.yifan.drawsee.mapper.AiTaskMapper;
import cn.yifan.drawsee.mapper.ConversationMapper;
import cn.yifan.drawsee.mapper.NodeMapper;
import cn.yifan.drawsee.mapper.UserMapper;
import cn.yifan.drawsee.pojo.XYPosition;
import cn.yifan.drawsee.pojo.entity.CircuitDesign;
import cn.yifan.drawsee.pojo.entity.Node;
import cn.yifan.drawsee.pojo.rabbit.AiTaskMessage;
import cn.yifan.drawsee.service.base.AiService;
import cn.yifan.drawsee.service.base.PromptService;
import cn.yifan.drawsee.service.base.StreamAiService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.model.StreamingResponseHandler;
import dev.langchain4j.model.output.Response;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RStream;
import org.redisson.api.RedissonClient;
import org.redisson.api.stream.StreamAddArgs;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * @FileName CircuitAnalysisDetailWorkFlow
 * @Description 处理电路分析点详情任务的工作流
 * @Author yifan
 * @date 2025-04-15 14:30
 **/

@Slf4j
@Service
public class CircuitAnalysisDetailWorkFlow extends WorkFlow {

    private final PromptService promptService;
    private final SpiceConverter spiceConverter;

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
        SpiceConverter spiceConverter
    ) {
        super(userMapper, aiService, streamAiService, redissonClient, nodeMapper, conversationMapper, aiTaskMapper, objectMapper);
        this.promptService = promptService;
        this.spiceConverter = spiceConverter;
    }
    
    @Override
    public Boolean validateAndInit(WorkContext workContext) {
        Boolean isValid = super.validateAndInit(workContext);
        if (!isValid) return false;
        
        Node parentNode = workContext.getParentNode();
        AiTaskMessage aiTaskMessage = workContext.getAiTaskMessage();
        
        // 校验父节点类型是否为新的电路分析节点
        if (!parentNode.getType().equals(NodeType.CIRCUIT_ANALYZE)) {
            log.error("父节点不是电路分析节点, taskMessage: {}", aiTaskMessage);
            return false;
        }
        
        return true;
    }
    
    /**
     * 覆盖父类方法，避免创建QUERY节点
     * 直接使用circuit-analyze节点作为详情回答的父节点
     */
    @Override
    public void createInitNodes(WorkContext workContext) throws JsonProcessingException {
        AiTaskMessage aiTaskMessage = workContext.getAiTaskMessage();
        Long parentNodeId = aiTaskMessage.getParentId();
        
        // 直接创建详情回答节点，跳过创建QUERY节点
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
        circuitDetailNodeData.put("subtype", NodeSubType.CIRCUIT_ANALYZE);
        circuitDetailNodeData.put("title", NodeTitle.CIRCUIT_DETAIL);
        circuitDetailNodeData.put("text", "");
        circuitDetailNodeData.put("contextTitle", contextTitle);
        circuitDetailNodeData.put("contextText", contextText);
        circuitDetailNodeData.put("followUp", followUp);
        circuitDetailNodeData.put("followUps", new ArrayList<>());
        
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
        log.info("[CircuitAnalysisDetail] 创建追问解析节点, parentNodeId={}, detailNodeId={}", parentNodeId, circuitDetailNode.getId());
    }
    
    @Override
    public void streamChat(WorkContext workContext, StreamingResponseHandler<AiMessage> handler) throws JsonProcessingException {
        AiTaskMessage aiTaskMessage = workContext.getAiTaskMessage();
        LinkedList<ChatMessage> history = workContext.getHistory();
        Node parentNode = workContext.getParentNode();
        String model = aiTaskMessage.getModel();
        
        TypeReference<Map<String, Object>> dataTypeRef = new TypeReference<>() {};
        Map<String, Object> parentNodeData = objectMapper.readValue(parentNode.getData(), dataTypeRef);
        String contextTitle = parentNodeData.getOrDefault("contextTitle", parentNodeData.getOrDefault("title", "电路分析")).toString();
        String contextText = parentNodeData.getOrDefault("text", "").toString();
        String followUpQuestion = aiTaskMessage.getPrompt();
        
        // 找到电路画布节点以获取电路设计数据
        CircuitDesign circuitDesign = findCircuitDesign(workContext);
        if (circuitDesign == null) {
            log.error("未找到电路设计数据, taskMessage: {}", aiTaskMessage);
            throw new RuntimeException("未找到电路设计数据");
        }
        
        // 生成SPICE网表
        String spiceNetlist = spiceConverter.generateNetlist(circuitDesign);
        
        String detailPrompt = promptService.getCircuitAnalyzeDetailPrompt(
            circuitDesign,
            spiceNetlist,
            contextTitle,
            contextText,
            followUpQuestion
        );
        
        // 调用AI进行详细分析
        streamAiService.circuitAnalysisChat(history, detailPrompt, model, handler);
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
        log.info("[CircuitAnalysisDetail] 追问生成完成, nodeId={}, followUps={}", streamNode.getId(), followUpSuggestions.size());
        
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
    
    /**
     * 向上回溯查找电路设计数据
     * @param workContext 工作上下文
     * @return 电路设计数据
     */
    private CircuitDesign findCircuitDesign(WorkContext workContext) {
        Node parentNode = workContext.getParentNode();
        Long convId = parentNode.getConvId();
        List<Node> nodes = nodeMapper.getByConvId(convId);
        Map<Long, Node> nodeMap = nodes.stream().collect(ConcurrentHashMap::new, (map, node) -> map.put(node.getId(), node), ConcurrentHashMap::putAll);
        
        // 向上回溯查找CIRCUIT_CANVAS节点
        Node currentNode = parentNode;
        while (currentNode != null && !currentNode.getType().equals(NodeType.ROOT)) {
            Node nextNode = nodeMap.get(currentNode.getParentId());
            
            // 如果下一个节点是CIRCUIT_CANVAS节点，则获取电路设计数据
            if (nextNode != null && nextNode.getType().equals(NodeType.CIRCUIT_CANVAS)) {
                try {
                    TypeReference<Map<String, Object>> dataTypeRef = new TypeReference<>() {};
                    Map<String, Object> canvasNodeData = objectMapper.readValue(nextNode.getData(), dataTypeRef);
                    Object circuitDesignObj = canvasNodeData.get("circuitDesign");
                    
                    // 将Object转为JSON字符串再转为CircuitDesign对象
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
}

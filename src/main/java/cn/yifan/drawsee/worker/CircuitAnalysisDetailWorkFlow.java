package cn.yifan.drawsee.worker;

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
import cn.yifan.drawsee.repository.KnowledgeRepository;
import cn.yifan.drawsee.service.base.AiService;
import cn.yifan.drawsee.service.base.PromptService;
import cn.yifan.drawsee.service.base.StreamAiService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.model.StreamingResponseHandler;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Service;

import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * @FileName CircuitAnalysisDetailWorkFlow
 * @Description 处理电路分析点详情任务的工作流
 * @Author devin
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
        KnowledgeRepository knowledgeRepository,
        NodeMapper nodeMapper,
        ConversationMapper conversationMapper,
        AiTaskMapper aiTaskMapper,
        ObjectMapper objectMapper,
        PromptService promptService,
        SpiceConverter spiceConverter
    ) {
        super(userMapper, aiService, streamAiService, redissonClient, knowledgeRepository, nodeMapper, conversationMapper, aiTaskMapper, objectMapper);
        this.promptService = promptService;
        this.spiceConverter = spiceConverter;
    }
    
    @Override
    public Boolean validateAndInit(WorkContext workContext) {
        Boolean isValid = super.validateAndInit(workContext);
        if (!isValid) return false;
        
        Node parentNode = workContext.getParentNode();
        AiTaskMessage aiTaskMessage = workContext.getAiTaskMessage();
        
        // 校验父节点类型是否为电路分析点节点
        if (!parentNode.getType().equals(NodeType.CIRCUIT_POINT)) {
            log.error("父节点不是电路分析点节点, taskMessage: {}", aiTaskMessage);
            return false;
        }
        
        return true;
    }
    
    /**
     * 覆盖父类方法，避免创建QUERY节点
     * 直接使用CIRCUIT_POINT节点作为详情回答的父节点
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
        
        // 从父节点(CIRCUIT_POINT)中读取角度信息
        TypeReference<Map<String, Object>> dataTypeRef = new TypeReference<>() {};
        Map<String, Object> parentNodeData = objectMapper.readValue(parentNode.getData(), dataTypeRef);
        String angleTitle = (String) parentNodeData.get("title");
        
        // 创建详细回答节点
        Map<String, Object> circuitDetailNodeData = new ConcurrentHashMap<>();
        circuitDetailNodeData.put("subtype", "circuit-detail");
        circuitDetailNodeData.put("title", angleTitle + "详情");
        circuitDetailNodeData.put("text", "");
        circuitDetailNodeData.put("angle", angleTitle);
        
        Node circuitDetailNode = new Node(
            NodeType.ANSWER,
            objectMapper.writeValueAsString(circuitDetailNodeData),
            objectMapper.writeValueAsString(XYPosition.origin()),
            parentNodeId,
            aiTaskMessage.getUserId(),
            aiTaskMessage.getConvId(),
            true
        );
        insertAndPublishStreamNode(workContext, circuitDetailNode, circuitDetailNodeData);
    }
    
    @Override
    public void streamChat(WorkContext workContext, StreamingResponseHandler<AiMessage> handler) throws JsonProcessingException {
        AiTaskMessage aiTaskMessage = workContext.getAiTaskMessage();
        LinkedList<ChatMessage> history = workContext.getHistory();
        Node parentNode = workContext.getParentNode();
        String model = aiTaskMessage.getModel();
        
        // 从父节点(CIRCUIT_POINT)中读取角度信息
        TypeReference<Map<String, Object>> dataTypeRef = new TypeReference<>() {};
        Map<String, Object> parentNodeData = objectMapper.readValue(parentNode.getData(), dataTypeRef);
        String angleTitle = (String) parentNodeData.get("title");
        
        // 找到电路画布节点以获取电路设计数据
        CircuitDesign circuitDesign = findCircuitDesign(workContext);
        if (circuitDesign == null) {
            log.error("未找到电路设计数据, taskMessage: {}", aiTaskMessage);
            throw new RuntimeException("未找到电路设计数据");
        }
        
        // 生成SPICE网表
        String spiceNetlist = spiceConverter.generateNetlist(circuitDesign);
        
        // 生成详细分析提示词
        String detailPrompt = promptService.getCircuitPointDetailPrompt(circuitDesign, spiceNetlist, angleTitle);
        
        // 调用AI进行详细分析
        streamAiService.circuitAnalysisChat(history, detailPrompt, model, handler);
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
package cn.yifan.drawsee.worker;

import cn.yifan.drawsee.constant.NodeSubType;
import cn.yifan.drawsee.constant.NodeTitle;
import cn.yifan.drawsee.constant.NodeType;
import cn.yifan.drawsee.mapper.AiTaskMapper;
import cn.yifan.drawsee.mapper.ConversationMapper;
import cn.yifan.drawsee.mapper.NodeMapper;
import cn.yifan.drawsee.mapper.UserMapper;
import cn.yifan.drawsee.pojo.XYPosition;
import cn.yifan.drawsee.pojo.entity.Node;
import cn.yifan.drawsee.pojo.rabbit.AiTaskMessage;
import cn.yifan.drawsee.repository.KnowledgeRepository;
import cn.yifan.drawsee.service.base.AiService;
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
 * @FileName GeneralDetailWorkFlow
 * @Description 处理通用对话详情任务的工作流
 * @Author yifan
 * @date 2025-05-28 10:00
 **/

@Slf4j
@Service
public class GeneralDetailWorkFlow extends WorkFlow {

    public GeneralDetailWorkFlow(
        UserMapper userMapper,
        AiService aiService,
        StreamAiService streamAiService,
        RedissonClient redissonClient,
        KnowledgeRepository knowledgeRepository,
        NodeMapper nodeMapper,
        ConversationMapper conversationMapper,
        AiTaskMapper aiTaskMapper,
        ObjectMapper objectMapper
    ) {
        super(userMapper, aiService, streamAiService, redissonClient, knowledgeRepository, nodeMapper, conversationMapper, aiTaskMapper, objectMapper);
    }
    
    @Override
    public Boolean validateAndInit(WorkContext workContext) {
        Boolean isValid = super.validateAndInit(workContext);
        if (!isValid) return false;
        
        Node parentNode = workContext.getParentNode();
        AiTaskMessage aiTaskMessage = workContext.getAiTaskMessage();
        
        // 校验父节点类型是否为回答角度节点
        if (!parentNode.getType().equals(NodeType.ANSWER_POINT)) {
            log.error("父节点不是回答角度节点, taskMessage: {}", aiTaskMessage);
            return false;
        }
        
        return true;
    }
    
    /**
     * 覆盖父类方法，避免创建QUERY节点
     * 直接使用ANSWER_POINT节点作为详情回答的父节点
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
        
        // 从父节点(ANSWER_POINT)中读取角度信息
        TypeReference<Map<String, Object>> dataTypeRef = new TypeReference<>() {};
        Map<String, Object> parentNodeData = objectMapper.readValue(parentNode.getData(), dataTypeRef);
        String angleTitle = (String) parentNodeData.get("title");
        
        // 创建详细回答节点
        Map<String, Object> answerDetailNodeData = new ConcurrentHashMap<>();
        answerDetailNodeData.put("subtype", NodeSubType.ANSWER_DETAIL);
        answerDetailNodeData.put("title", NodeTitle.ANSWER_DETAIL);
        answerDetailNodeData.put("text", "");
        answerDetailNodeData.put("angle", angleTitle);
        
        Node answerDetailNode = new Node(
            NodeType.ANSWER_DETAIL,
            objectMapper.writeValueAsString(answerDetailNodeData),
            objectMapper.writeValueAsString(XYPosition.origin()),
            parentNodeId,
            aiTaskMessage.getUserId(),
            aiTaskMessage.getConvId(),
            true
        );
        insertAndPublishStreamNode(workContext, answerDetailNode, answerDetailNodeData);
    }
    
    @Override
    public void streamChat(WorkContext workContext, StreamingResponseHandler<AiMessage> handler) throws JsonProcessingException {
        AiTaskMessage aiTaskMessage = workContext.getAiTaskMessage();
        LinkedList<ChatMessage> history = workContext.getHistory();
        Node parentNode = workContext.getParentNode();
        
        // 从父节点(ANSWER_POINT)中读取角度信息
        TypeReference<Map<String, Object>> dataTypeRef = new TypeReference<>() {};
        Map<String, Object> parentNodeData = objectMapper.readValue(parentNode.getData(), dataTypeRef);
        String angleTitle = (String) parentNodeData.get("title");
        
        // 找到原始问题
        // 向上回溯查找QUERY节点获取原始问题
        String originalQuestion = findOriginalQuestion(workContext);
        if (originalQuestion == null) {
            // 如果查找原始问题失败，则使用当前提示作为原始问题
            originalQuestion = aiTaskMessage.getPrompt();
            log.warn("未找到原始问题，使用当前提示作为原始问题: {}", originalQuestion);
        }
        
        // 生成详细回答
        streamAiService.answerDetailChat(
            history,
            originalQuestion,
            angleTitle,
            aiTaskMessage.getModel(),
            handler
        );
    }
    
    /**
     * 向上回溯查找原始问题
     * @param workContext 工作上下文
     * @return 原始问题
     */
    private String findOriginalQuestion(WorkContext workContext) {
        Node parentNode = workContext.getParentNode();
        Long convId = parentNode.getConvId();
        List<Node> nodes = nodeMapper.getByConvId(convId);
        Map<Long, Node> nodeMap = nodes.stream().collect(ConcurrentHashMap::new, (map, node) -> map.put(node.getId(), node), ConcurrentHashMap::putAll);
        
        // 向上回溯查找QUERY节点
        Node currentNode = parentNode;
        while (currentNode != null && !currentNode.getType().equals(NodeType.ROOT)) {
            Node nextNode = nodeMap.get(currentNode.getParentId());
            
            // 如果下一个节点是QUERY节点，则返回其文本内容
            if (nextNode != null && nextNode.getType().equals(NodeType.QUERY)) {
                try {
                    TypeReference<Map<String, Object>> dataTypeRef = new TypeReference<>() {};
                    Map<String, Object> queryNodeData = objectMapper.readValue(nextNode.getData(), dataTypeRef);
                    return (String) queryNodeData.get("text");
                } catch (JsonProcessingException e) {
                    log.error("解析QUERY节点数据失败: {}", e.getMessage());
                    return null;
                }
            }
            
            currentNode = nextNode;
        }
        
        return null;
    }
}
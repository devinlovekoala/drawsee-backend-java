package cn.yifan.drawsee.worker;

import cn.yifan.drawsee.constant.NodeTitle;
import cn.yifan.drawsee.constant.*;
import cn.yifan.drawsee.mapper.AiTaskMapper;
import cn.yifan.drawsee.mapper.ConversationMapper;
import cn.yifan.drawsee.mapper.NodeMapper;
import cn.yifan.drawsee.mapper.UserMapper;
import cn.yifan.drawsee.pojo.XYPosition;
import cn.yifan.drawsee.pojo.entity.Node;
import cn.yifan.drawsee.pojo.mongo.Knowledge;
import cn.yifan.drawsee.pojo.mongo.KnowledgeResource;
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

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * @FileName KnowledgeDetailWorkFlow
 * @Description
 * @Author yifan
 * @date 2025-03-09 13:49
 **/

@Slf4j
@Service
public class KnowledgeDetailWorkFlow extends WorkFlow {

    public KnowledgeDetailWorkFlow(
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
        // 校验父节点类型是否正确
        if (!parentNode.getType().equals(NodeType.KNOWLEDGE_HEAD)) {
            log.error("父节点不是知识点头节点, taskMessage: {}", aiTaskMessage);
            return false;
        }
        return true;
    }

    @Override
    public void createInitNodes(WorkContext workContext) throws JsonProcessingException {
        Node parentNode = workContext.getParentNode();
        AiTaskMessage aiTaskMessage = workContext.getAiTaskMessage();

        // 创建knowledge-detail节点
        Map<String, Object> knowledgeDetailNodeData = new ConcurrentHashMap<>();
        knowledgeDetailNodeData.put("title", NodeTitle.KNOWLEDGE_DETAIL);
        knowledgeDetailNodeData.put("text", "");
        Map<String, Object> knowledgeDetailMedia = new ConcurrentHashMap<>();
        knowledgeDetailMedia.put("animationObjectNames", new ArrayList<>());
        knowledgeDetailMedia.put("bilibiliUrls", new ArrayList<>());
        knowledgeDetailNodeData.put("media", knowledgeDetailMedia);

        Node knowledgeDetailNode = new Node(
            NodeType.KNOWLEDGE_DETAIL,
            objectMapper.writeValueAsString(knowledgeDetailNodeData),
            objectMapper.writeValueAsString(XYPosition.origin()),
            parentNode.getId(),
            aiTaskMessage.getUserId(),
            aiTaskMessage.getConvId(),
            true
        );
        insertAndPublishStreamNode(workContext, knowledgeDetailNode, knowledgeDetailNodeData);
    }

    @Override
    public void streamChat(WorkContext workContext, StreamingResponseHandler<AiMessage> handler) throws JsonProcessingException {
        Node parentNode = workContext.getParentNode();
        LinkedList<ChatMessage> history = workContext.getHistory();
        TypeReference<Map<String, Object>> parentNodeDataTypeReference = new TypeReference<>() {};
        Map<String, Object> parentNodeData = objectMapper.readValue(parentNode.getData(), parentNodeDataTypeReference);
        String knowledgePoint = (String) parentNodeData.get("text");

        streamAiService.knowledgeDetailChat(history, knowledgePoint, handler);
    }

    @Override
    public void createOtherNodesOrUpdateNodeData(WorkContext workContext) throws JsonProcessingException {
        Node parentNode = workContext.getParentNode();
        Node streamNode = workContext.getStreamNode();
        TypeReference<Map<String, Object>> parentNodeDataTypeReference = new TypeReference<>() {};
        Map<String, Object> parentNodeData = objectMapper.readValue(parentNode.getData(), parentNodeDataTypeReference);
        String knowledgePoint = (String) parentNodeData.get("text");

        // 获取media
        Knowledge knowledge = knowledgeRepository.findBySubjectAndName(KnowledgeSubject.LINEAR_ALGEBRA, knowledgePoint);
        if (knowledge == null) return;
        List<KnowledgeResource> resources = knowledge.getResources();

        List<String> animationObjectNames = resources.stream()
                .filter(knowledgeResource -> knowledgeResource.getType().equals(KnowledgeResourceType.ANIMATION))
                .map(KnowledgeResource::getValue).toList();

        List<String> bilibiliUrls = resources.stream()
                .filter(knowledgeResource -> knowledgeResource.getType().equals(KnowledgeResourceType.BILIBILI))
                .map(KnowledgeResource::getValue).toList();

        // 创建 resource节点
        AiTaskMessage aiTaskMessage = workContext.getAiTaskMessage();
        if (!animationObjectNames.isEmpty()) {
            Map<String, Object> animationResourceNodeData = new ConcurrentHashMap<>();
            animationResourceNodeData.put("title", NodeTitle.ANIMATION);
            animationResourceNodeData.put("subtype", NodeSubType.ANIMATION);
            animationResourceNodeData.put("objectNames", animationObjectNames);
            Node animationResourceNode = new Node(
                NodeType.RESOURCE,
                objectMapper.writeValueAsString(animationResourceNodeData),
                objectMapper.writeValueAsString(XYPosition.origin()),
                streamNode.getId(),
                aiTaskMessage.getUserId(),
                aiTaskMessage.getConvId(),
                true
            );
            insertAndPublishNoneStreamNode(workContext, animationResourceNode, animationResourceNodeData);
        }
        if (!bilibiliUrls.isEmpty()) {
            Map<String, Object> bilibiliResourceNodeData = new ConcurrentHashMap<>();
            bilibiliResourceNodeData.put("title", NodeTitle.BILIBILI);
            bilibiliResourceNodeData.put("subtype", NodeSubType.BILIBILI);
            bilibiliResourceNodeData.put("urls", bilibiliUrls);
            Node bilibiliResourceNode = new Node(
                NodeType.RESOURCE,
                objectMapper.writeValueAsString(bilibiliResourceNodeData),
                objectMapper.writeValueAsString(XYPosition.origin()),
                streamNode.getId(),
                aiTaskMessage.getUserId(),
                aiTaskMessage.getConvId(),
                true
            );
            insertAndPublishNoneStreamNode(workContext, bilibiliResourceNode, bilibiliResourceNodeData);
        }
    }

}

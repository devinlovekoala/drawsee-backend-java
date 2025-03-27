package cn.yifan.drawsee.worker;

import cn.yifan.drawsee.constant.NodeTitle;
import cn.yifan.drawsee.constant.NodeType;
import cn.yifan.drawsee.constant.RedisKey;
import cn.yifan.drawsee.mapper.AiTaskMapper;
import cn.yifan.drawsee.mapper.ConversationMapper;
import cn.yifan.drawsee.mapper.NodeMapper;
import cn.yifan.drawsee.mapper.UserMapper;
import cn.yifan.drawsee.pojo.XYPosition;
import cn.yifan.drawsee.pojo.entity.Node;
import cn.yifan.drawsee.pojo.mongo.Knowledge;
import cn.yifan.drawsee.pojo.rabbit.AiTaskMessage;
import cn.yifan.drawsee.repository.KnowledgeRepository;
import cn.yifan.drawsee.service.base.AiService;
import cn.yifan.drawsee.service.base.StreamAiService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RList;
import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.time.Duration;

/**
 * @FileName KnowledgeWorkFlow
 * @Description
 * @Author yifan
 * @date 2025-03-09 13:35
 **/

@Slf4j
@Service
public class KnowledgeWorkFlow extends WorkFlow {

    public KnowledgeWorkFlow(
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
        // 知识点列表
        List<Knowledge> knowledgeList = knowledgeRepository.findAll();
        List<String> knowledgePoints = knowledgeList.stream().map(Knowledge::getName).toList();
        RList<String> rList = redissonClient.getList(RedisKey.CACHE_PREFIX + "knowledge-points");
        rList.clear();
        rList.addAll(knowledgePoints);
        // 清除过期时间，设置为永不过期
        rList.clearExpire();
    }

    @Override
    public void createOtherNodesOrUpdateNodeData(WorkContext workContext) throws JsonProcessingException {
        AiTaskMessage aiTaskMessage = workContext.getAiTaskMessage();
        Node streamNode = workContext.getStreamNode();

        // 获取相关知识点
        RList<String> rList = redissonClient.getList(RedisKey.CACHE_PREFIX + "knowledge-points");
        List<String> knowledgePoints = rList.stream().toList();
        List<String> relatedKnowledgePoints = aiService.getRelatedKnowledgePoints(knowledgePoints, aiTaskMessage.getPrompt(), workContext.getTokens());

        if (relatedKnowledgePoints == null) return;
        if (relatedKnowledgePoints.isEmpty()) return;

        // 创建知识点节点
        for (String knowledgePoint : relatedKnowledgePoints) {
            Map<String, Object> knowledgeHeadNodeData = new ConcurrentHashMap<>();
            knowledgeHeadNodeData.put("title", NodeTitle.KNOWLEDGE_HEAD);
            knowledgeHeadNodeData.put("text", knowledgePoint);
            Node knowledgeHeadNode = new Node(
                NodeType.KNOWLEDGE_HEAD,
                objectMapper.writeValueAsString(knowledgeHeadNodeData),
                objectMapper.writeValueAsString(XYPosition.origin()),
                streamNode.getId(),
                aiTaskMessage.getUserId(),
                aiTaskMessage.getConvId(),
                true
            );
            insertAndPublishNoneStreamNode(workContext, knowledgeHeadNode, knowledgeHeadNodeData);
        }
    }
}

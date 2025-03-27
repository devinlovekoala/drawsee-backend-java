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
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.ai4j.openai4j.chat.AssistantMessage;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.StreamingResponseHandler;
import dev.langchain4j.model.output.Response;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * @FileName PlannerWorkFlow
 * @Description
 * @Author yifan
 * @date 2025-03-24 09:39
 **/

@Service
@Slf4j
public class PlannerWorkFlow extends WorkFlow {

    public PlannerWorkFlow(
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
    public void createInitNodes(WorkContext workContext) throws JsonProcessingException {
        AiTaskMessage aiTaskMessage = workContext.getAiTaskMessage();
        // 创建用户提问节点
        Long queryNodeId = createInitQueryNode(workContext);
        LinkedList<ChatMessage> history = workContext.getHistory();
        String prompt = aiTaskMessage.getPrompt();
        history.add(new UserMessage(prompt));
        // 创建AI回答节点
        Map<String, Object> answerNodeData = new ConcurrentHashMap<>();
        answerNodeData.put("subtype", NodeSubType.PLANNER_FIRST);
        answerNodeData.put("title", NodeTitle.PLANNER_FIRST);
        answerNodeData.put("text", "");
        Node answerNode = new Node(
            NodeType.ANSWER,
            objectMapper.writeValueAsString(answerNodeData),
            objectMapper.writeValueAsString(XYPosition.origin()),
            queryNodeId,
            aiTaskMessage.getUserId(),
            aiTaskMessage.getConvId(),
            true
        );
        insertAndPublishStreamNode(workContext, answerNode, answerNodeData);
    }

    @Override
    public void streamChat(WorkContext workContext, StreamingResponseHandler<AiMessage> handler) throws JsonProcessingException {
        LinkedList<ChatMessage> history = workContext.getHistory();
        streamAiService.plannerFirstChat(history, handler);
    }

    @Override
    public void createOtherNodesOrUpdateNodeData(WorkContext workContext) throws JsonProcessingException {
        // 创建planner-split子节点
        Node streamNode = workContext.getStreamNode();
        AiTaskMessage aiTaskMessage = workContext.getAiTaskMessage();
        Response<AiMessage> streamResponse = workContext.getStreamResponse();
        LinkedList<ChatMessage> history = workContext.getHistory();
        AtomicLong tokens = workContext.getTokens();
        history.add(new AiMessage(streamResponse.content().text()));
        // 获取planner-split
        List<String> plannerSplitList = aiService.getPlannerSplit(history, tokens);
        for (String plannerSplit : plannerSplitList) {
            Map<String, Object> planSplitNodeData = new ConcurrentHashMap<>();
            planSplitNodeData.put("subtype", NodeSubType.PLANNER_SPLIT);
            planSplitNodeData.put("title", NodeTitle.PLANNER_SPLIT);
            planSplitNodeData.put("text", plannerSplit);
            Node planSplitNode = new Node(
                NodeType.ANSWER,
                objectMapper.writeValueAsString(planSplitNodeData),
                objectMapper.writeValueAsString(XYPosition.origin()),
                streamNode.getId(),
                aiTaskMessage.getUserId(),
                aiTaskMessage.getConvId(),
                true
            );
            insertAndPublishNoneStreamNode(workContext, planSplitNode, planSplitNodeData);
        }
    }
}

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
import cn.yifan.drawsee.service.base.AiService;
import cn.yifan.drawsee.service.base.StreamAiService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.model.StreamingResponseHandler;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Service;

import java.util.LinkedList;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * @FileName HtmlMakerWorkFlow
 * @Description
 * @Author yifan
 * @date 2025-03-24 15:16
 **/

@Service
@Slf4j
public class HtmlMakerWorkFlow extends WorkFlow {

    public HtmlMakerWorkFlow(
            UserMapper userMapper,
            AiService aiService,
            StreamAiService streamAiService,
            RedissonClient redissonClient,
            NodeMapper nodeMapper,
            ConversationMapper conversationMapper,
            AiTaskMapper aiTaskMapper,
            ObjectMapper objectMapper
    ) {
        super(userMapper, aiService, streamAiService, redissonClient, nodeMapper, conversationMapper, aiTaskMapper, objectMapper);
    }

    @Override
    public void createInitStreamNode(WorkContext workContext, Long parentNodeId) throws JsonProcessingException {
        AiTaskMessage aiTaskMessage = workContext.getAiTaskMessage();
        // 创建AI回答节点
        Map<String, Object> answerNodeData = new ConcurrentHashMap<>();
        answerNodeData.put("subtype", NodeSubType.HTML_MAKER);
        answerNodeData.put("title", NodeTitle.HTML_MAKER);
        answerNodeData.put("text", "");
        Node answerNode = new Node(
            NodeType.ANSWER,
            objectMapper.writeValueAsString(answerNodeData),
            objectMapper.writeValueAsString(XYPosition.origin()),
            parentNodeId,
            aiTaskMessage.getUserId(),
            aiTaskMessage.getConvId(),
            true
        );
        insertAndPublishStreamNode(workContext, answerNode, answerNodeData);
    }

    @Override
    public void streamChat(WorkContext workContext, StreamingResponseHandler<AiMessage> handler) throws JsonProcessingException {
        LinkedList<ChatMessage> history = workContext.getHistory();
        AiTaskMessage aiTaskMessage = workContext.getAiTaskMessage();
        String prompt = aiTaskMessage.getPrompt();
        String model = aiTaskMessage.getModel();
        streamAiService.htmlMakerChat(history, prompt, model, handler);
    }

}

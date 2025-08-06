package cn.yifan.drawsee.worker;

import cn.yifan.drawsee.constant.AiTaskMessageType;
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
import dev.langchain4j.model.StreamingResponseHandler;
import dev.langchain4j.model.output.Response;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RStream;
import org.redisson.api.RedissonClient;
import org.redisson.api.stream.StreamAddArgs;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * @FileName SolverWorkFlow
 * @Description
 * @Author yifan
 * @date 2025-03-23 13:48
 **/

@Service
@Slf4j
public class SolverContinueWorkFlow extends WorkFlow {

    public SolverContinueWorkFlow(
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

    // TODO 补充校验逻辑

    @Override
    public void createInitNodes(WorkContext workContext) throws JsonProcessingException {
        AiTaskMessage aiTaskMessage = workContext.getAiTaskMessage();
        // 创建AI回答节点
        Map<String, Object> answerNodeData = new ConcurrentHashMap<>();
        answerNodeData.put("subtype", NodeSubType.SOLVER_CONTINUE);
        answerNodeData.put("title", NodeTitle.SOLVER_CONTINUE);
        answerNodeData.put("text", "");
        Node answerNode = new Node(
            NodeType.ANSWER,
            objectMapper.writeValueAsString(answerNodeData),
            objectMapper.writeValueAsString(XYPosition.origin()),
            aiTaskMessage.getParentId(),
            aiTaskMessage.getUserId(),
            aiTaskMessage.getConvId(),
            true
        );
        insertAndPublishStreamNode(workContext, answerNode, answerNodeData);
    }

    @Override
    public void streamChat(WorkContext workContext, StreamingResponseHandler<AiMessage> handler) throws JsonProcessingException {
        AiTaskMessage aiTaskMessage = workContext.getAiTaskMessage();
        String model = aiTaskMessage.getModel();
        streamAiService.solverContinueChat(workContext.getHistory(), model, handler);
    }

    @Override
    public void createOtherNodesOrUpdateNodeData(WorkContext workContext) throws JsonProcessingException {
        Response<AiMessage> streamResponse = workContext.getStreamResponse();
        RStream<String, Object> redisStream = workContext.getRedisStream();
        Node streamNode = workContext.getStreamNode();
        Map<String, Object> streamNodeData = workContext.getStreamNodeData();
        Map<String, Object> data = new ConcurrentHashMap<>();
        data.put("nodeId", streamNode.getId());
        // 判断text中是否包含"[推导结束]"
        Boolean isContain = streamResponse.content().text().contains("[推导结束]");
        data.put("isDone", isContain);
        redisStream.add(StreamAddArgs.entries(
        "type", AiTaskMessageType.DATA,
        "data", data
        ));
        streamNodeData.put("isDone", isContain);
    }

}

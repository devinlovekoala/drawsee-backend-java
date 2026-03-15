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
import cn.yifan.drawsee.service.business.ContextBudgetManager;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.model.StreamingResponseHandler;
import java.util.LinkedList;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Service;

/**
 * @FileName SolverWorkFlow @Description @Author yifan
 *
 * @date 2025-03-23 13:48
 */
@Service
@Slf4j
public class SolverFirstWorkFlow extends WorkFlow {

  public SolverFirstWorkFlow(
      UserMapper userMapper,
      AiService aiService,
      StreamAiService streamAiService,
      RedissonClient redissonClient,
      NodeMapper nodeMapper,
      ConversationMapper conversationMapper,
      AiTaskMapper aiTaskMapper,
      ObjectMapper objectMapper,
      ContextBudgetManager contextBudgetManager) {
    super(
        userMapper,
        aiService,
        streamAiService,
        redissonClient,
        nodeMapper,
        conversationMapper,
        aiTaskMapper,
        objectMapper,
        contextBudgetManager);
  }

  @Override
  public void createInitStreamNode(WorkContext workContext, Long parentNodeId)
      throws JsonProcessingException {
    AiTaskMessage aiTaskMessage = workContext.getAiTaskMessage();

    // 创建AI回答节点
    Map<String, Object> answerNodeData = new ConcurrentHashMap<>();
    answerNodeData.put("subtype", NodeSubType.SOLVER_FIRST);
    answerNodeData.put("title", NodeTitle.SOLVER_FIRST);
    answerNodeData.put("text", "");
    Node answerNode =
        new Node(
            NodeType.ANSWER,
            objectMapper.writeValueAsString(answerNodeData),
            objectMapper.writeValueAsString(XYPosition.origin()),
            parentNodeId,
            aiTaskMessage.getUserId(),
            aiTaskMessage.getConvId(),
            true);
    insertAndPublishStreamNode(workContext, answerNode, answerNodeData);
  }

  @Override
  public void streamChat(WorkContext workContext, StreamingResponseHandler<AiMessage> handler)
      throws JsonProcessingException {
    AiTaskMessage aiTaskMessage = workContext.getAiTaskMessage();
    LinkedList<ChatMessage> history =
        applyHistoryBudget(workContext, planContextBudget(workContext, aiTaskMessage.getPrompt()));
    String model = aiTaskMessage.getModel();
    streamAiService.solverFirstChat(
        history,
        aiTaskMessage.getPrompt(),
        aiTaskMessage.getPromptParams().get("method"),
        model,
        handler);
  }
}

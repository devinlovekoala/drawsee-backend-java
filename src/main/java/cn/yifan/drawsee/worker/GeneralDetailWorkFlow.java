package cn.yifan.drawsee.worker;

import cn.yifan.drawsee.constant.NodeSubType;
import cn.yifan.drawsee.constant.NodeTitle;
import cn.yifan.drawsee.constant.NodeType;
import cn.yifan.drawsee.mapper.AiTaskMapper;
import cn.yifan.drawsee.mapper.ConversationMapper;
import cn.yifan.drawsee.mapper.NodeMapper;
import cn.yifan.drawsee.mapper.UserMapper;
import cn.yifan.drawsee.pojo.XYPosition;
import cn.yifan.drawsee.pojo.entity.CircuitDesign;
import cn.yifan.drawsee.pojo.entity.Node;
import cn.yifan.drawsee.pojo.rabbit.AiTaskMessage;
import cn.yifan.drawsee.pojo.vo.rag.RagChatResponseVO;
import cn.yifan.drawsee.service.base.AiService;
import cn.yifan.drawsee.service.base.StreamAiService;
import cn.yifan.drawsee.service.business.ClassKnowledgeService;
import cn.yifan.drawsee.service.business.ContextBudgetManager;
import cn.yifan.drawsee.service.business.RagEnhancementService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.model.StreamingResponseHandler;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Service;

/**
 * @FileName GeneralDetailWorkFlow @Description 处理通用对话详情任务的工作流 @Author yifan
 *
 * @date 2025-05-28 10:00
 */
@Slf4j
@Service
public class GeneralDetailWorkFlow extends WorkFlow {

  private final ClassKnowledgeService classKnowledgeService;
  private final RagEnhancementService ragEnhancementService;

  public GeneralDetailWorkFlow(
      UserMapper userMapper,
      AiService aiService,
      StreamAiService streamAiService,
      RedissonClient redissonClient,
      NodeMapper nodeMapper,
      ConversationMapper conversationMapper,
      AiTaskMapper aiTaskMapper,
      ObjectMapper objectMapper,
      ContextBudgetManager contextBudgetManager,
      ClassKnowledgeService classKnowledgeService,
      RagEnhancementService ragEnhancementService) {
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
    this.classKnowledgeService = classKnowledgeService;
    this.ragEnhancementService = ragEnhancementService;
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

  /** 覆盖父类方法，避免创建QUERY节点 直接使用ANSWER_POINT节点作为详情回答的父节点 */
  @Override
  public void createInitNodes(WorkContext workContext) throws JsonProcessingException {
    AiTaskMessage aiTaskMessage = workContext.getAiTaskMessage();
    Long parentNodeId = aiTaskMessage.getParentId();

    // 直接创建详情回答节点，跳过创建QUERY节点
    createInitStreamNode(workContext, parentNodeId);
  }

  @Override
  public void createInitStreamNode(WorkContext workContext, Long parentNodeId)
      throws JsonProcessingException {
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

    Node answerDetailNode =
        new Node(
            NodeType.ANSWER_DETAIL,
            objectMapper.writeValueAsString(answerDetailNodeData),
            objectMapper.writeValueAsString(XYPosition.origin()),
            parentNodeId,
            aiTaskMessage.getUserId(),
            aiTaskMessage.getConvId(),
            true);
    insertAndPublishStreamNode(workContext, answerDetailNode, answerDetailNodeData);
  }

  @Override
  public void streamChat(WorkContext workContext, StreamingResponseHandler<AiMessage> handler)
      throws JsonProcessingException {
    AiTaskMessage aiTaskMessage = workContext.getAiTaskMessage();
    LinkedList<ChatMessage> history =
        applyHistoryBudget(workContext, planContextBudget(workContext, aiTaskMessage.getPrompt()));
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

    String enhancedQuestion = originalQuestion;
    RagChatResponseVO ragResponse = tryRagEnhance(workContext, history, originalQuestion);
    if (ragResponse != null
        && ragResponse.getAnswer() != null
        && !ragResponse.getAnswer().isBlank()) {
      enhancedQuestion =
          "【知识库检索结果】\n" + ragResponse.getAnswer() + "\n\n【用户问题】\n" + originalQuestion;
    }

    // 生成详细回答
    streamAiService.answerDetailChat(
        history, enhancedQuestion, angleTitle, aiTaskMessage.getModel(), handler);
  }

  @Override
  public void createOtherNodesOrUpdateNodeData(WorkContext workContext)
      throws JsonProcessingException {
    AiTaskMessage aiTaskMessage = workContext.getAiTaskMessage();
    if (!isCircuitGenerationIntent(aiTaskMessage.getPrompt())) {
      return;
    }
    try {
      createGeneratedCircuitNodes(workContext, aiTaskMessage);
    } catch (Exception e) {
      log.warn(
          "文本生成电路节点失败，降级为纯文本详情。taskId={}, reason={}", aiTaskMessage.getTaskId(), e.getMessage());
    }
  }

  /**
   * 向上回溯查找原始问题
   *
   * @param workContext 工作上下文
   * @return 原始问题
   */
  private String findOriginalQuestion(WorkContext workContext) {
    Node parentNode = workContext.getParentNode();
    Long convId = parentNode.getConvId();
    List<Node> nodes = nodeMapper.getByConvId(convId);
    Map<Long, Node> nodeMap =
        nodes.stream()
            .collect(
                ConcurrentHashMap::new,
                (map, node) -> map.put(node.getId(), node),
                ConcurrentHashMap::putAll);

    // 向上回溯查找QUERY节点
    Node currentNode = parentNode;
    while (currentNode != null && !currentNode.getType().equals(NodeType.ROOT)) {
      Node nextNode = nodeMap.get(currentNode.getParentId());

      // 如果下一个节点是QUERY节点，则返回其文本内容
      if (nextNode != null && nextNode.getType().equals(NodeType.QUERY)) {
        try {
          TypeReference<Map<String, Object>> dataTypeRef = new TypeReference<>() {};
          Map<String, Object> queryNodeData =
              objectMapper.readValue(nextNode.getData(), dataTypeRef);
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

  private RagChatResponseVO tryRagEnhance(
      WorkContext workContext, List<ChatMessage> history, String prompt) {
    AiTaskMessage aiTaskMessage = workContext.getAiTaskMessage();
    Long userId = aiTaskMessage.getUserId();
    String classId = aiTaskMessage.getClassId();
    List<String> knowledgeBaseIds =
        classId != null && !classId.isEmpty()
            ? classKnowledgeService.getAccessibleKnowledgeBaseIds(Long.parseLong(classId), userId)
            : classKnowledgeService.getAccessibleKnowledgeBaseIds(null, userId);
    if (knowledgeBaseIds == null || knowledgeBaseIds.isEmpty()) {
      return null;
    }
    return ragEnhancementService.queryWithTimeout(
        knowledgeBaseIds, prompt, history, userId, String.valueOf(classId));
  }

  private boolean isCircuitGenerationIntent(String prompt) {
    if (prompt == null || prompt.isBlank()) {
      return false;
    }
    String normalized = prompt.toLowerCase(Locale.ROOT);
    boolean hasCircuitKeyword =
        normalized.contains("电路")
            || normalized.contains("circuit")
            || normalized.contains("原理图")
            || normalized.contains("电路图");
    boolean hasGenerateAction =
        normalized.contains("生成")
            || normalized.contains("画")
            || normalized.contains("绘制")
            || normalized.contains("设计")
            || normalized.contains("build")
            || normalized.contains("create");
    return hasCircuitKeyword && hasGenerateAction;
  }

  private void createGeneratedCircuitNodes(WorkContext workContext, AiTaskMessage aiTaskMessage)
      throws JsonProcessingException {
    Node detailNode = workContext.getStreamNode();
    if (detailNode == null || detailNode.getId() == null) {
      return;
    }

    CircuitDesign design =
        aiService.generateCircuitDesignFromText(
            aiTaskMessage.getPrompt(), aiTaskMessage.getModel());
    if (design == null || design.getElements() == null || design.getElements().isEmpty()) {
      return;
    }

    String title =
        design.getMetadata() != null && design.getMetadata().getTitle() != null
            ? design.getMetadata().getTitle()
            : NodeTitle.CIRCUIT_CANVAS;

    Map<String, Object> canvasNodeData = new ConcurrentHashMap<>();
    canvasNodeData.put("title", title);
    canvasNodeData.put("text", "已根据你的描述自动生成可编辑电路图");
    canvasNodeData.put("subtype", NodeSubType.CIRCUIT_CANVAS);
    canvasNodeData.put("circuitDesign", design);
    canvasNodeData.put("mode", aiTaskMessage.getModel());

    Node canvasNode =
        new Node(
            NodeType.CIRCUIT_CANVAS,
            objectMapper.writeValueAsString(canvasNodeData),
            objectMapper.writeValueAsString(new XYPosition(420, 0)),
            detailNode.getId(),
            aiTaskMessage.getUserId(),
            aiTaskMessage.getConvId(),
            true);
    insertAndPublishNoneStreamNode(workContext, canvasNode, canvasNodeData);

    Map<String, Object> analyzeNodeData = new ConcurrentHashMap<>();
    analyzeNodeData.put("title", NodeTitle.CIRCUIT_ANALYSIS);
    analyzeNodeData.put("text", "电路图已生成。你可以继续追问“这个电路如何工作”或“如何优化参数”。");
    analyzeNodeData.put("subtype", NodeSubType.CIRCUIT_ANALYZE);
    analyzeNodeData.put("contextTitle", title);
    analyzeNodeData.put("contextText", aiTaskMessage.getPrompt());
    analyzeNodeData.put("parentPointId", String.valueOf(canvasNode.getId()));
    analyzeNodeData.put("followUps", buildDefaultCircuitFollowUps());

    Node analyzeNode =
        new Node(
            NodeType.CIRCUIT_ANALYZE,
            objectMapper.writeValueAsString(analyzeNodeData),
            objectMapper.writeValueAsString(new XYPosition(420, 0)),
            canvasNode.getId(),
            aiTaskMessage.getUserId(),
            aiTaskMessage.getConvId(),
            true);
    insertAndPublishNoneStreamNode(workContext, analyzeNode, analyzeNodeData);
  }

  private List<Map<String, Object>> buildDefaultCircuitFollowUps() {
    List<Map<String, Object>> followUps = new ArrayList<>();
    followUps.add(createFollowUp("工作原理", "请解释该电路各元件的作用与工作流程"));
    followUps.add(createFollowUp("关键参数", "该电路的关键参数如何选取，为什么"));
    followUps.add(createFollowUp("优化建议", "这个电路有哪些可落地的优化方向"));
    return followUps;
  }

  private Map<String, Object> createFollowUp(String title, String followUp) {
    Map<String, Object> item = new ConcurrentHashMap<>();
    item.put("title", title);
    item.put("followUp", followUp);
    item.put("intent", "circuit-followup");
    return item;
  }
}

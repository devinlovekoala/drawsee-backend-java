package cn.yifan.drawsee.worker;

import cn.yifan.drawsee.assistant.CircuitAnalysisAssistant;
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
import cn.yifan.drawsee.pojo.vo.rag.RagChatResponseVO;
import cn.yifan.drawsee.service.base.AiService;
import cn.yifan.drawsee.service.base.PromptService;
import cn.yifan.drawsee.service.base.StreamAiService;
import cn.yifan.drawsee.service.business.ContextBudgetManager;
import cn.yifan.drawsee.service.business.KnowledgeBaseService;
import cn.yifan.drawsee.service.business.RagEnhancementService;
import cn.yifan.drawsee.tool.AgenticRagTool;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.model.StreamingResponseHandler;
import dev.langchain4j.model.output.Response;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RStream;
import org.redisson.api.RedissonClient;
import org.redisson.api.stream.StreamAddArgs;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

/**
 * 电路分析工作流 - Tool-based架构
 *
 * <p>架构变更： - 旧版：Python LLM生成答案，Java转发SSE流 - 新版：Java LangChain4j主导对话生成，Python仅提供RAG工具
 *
 * @author Drawsee Team
 */
@Slf4j
@Service
public class CircuitAnalysisWorkFlow extends WorkFlow {

  private final PromptService promptService;
  private final SpiceConverter spiceConverter;
  private final KnowledgeBaseService knowledgeBaseService;
  private final AgenticRagTool agenticRagTool;
  private final RagEnhancementService ragEnhancementService;

  public CircuitAnalysisWorkFlow(
      UserMapper userMapper,
      AiService aiService,
      StreamAiService streamAiService,
      RedissonClient redissonClient,
      NodeMapper nodeMapper,
      ConversationMapper conversationMapper,
      AiTaskMapper aiTaskMapper,
      ObjectMapper objectMapper,
      PromptService promptService,
      SpiceConverter spiceConverter,
      KnowledgeBaseService knowledgeBaseService,
      ObjectProvider<AgenticRagTool> agenticRagToolProvider,
      ContextBudgetManager contextBudgetManager,
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
    this.promptService = promptService;
    this.spiceConverter = spiceConverter;
    this.knowledgeBaseService = knowledgeBaseService;
    this.agenticRagTool = agenticRagToolProvider.getIfAvailable();
    this.ragEnhancementService = ragEnhancementService;
  }

  @Override
  public void createInitNodes(WorkContext workContext) throws JsonProcessingException {
    AiTaskMessage aiTaskMessage = workContext.getAiTaskMessage();
    CircuitDesign circuitDesign =
        objectMapper.readValue(aiTaskMessage.getPrompt(), CircuitDesign.class);

    // 创建电路画布节点
    Map<String, Object> canvasNodeData = new HashMap<>();
    canvasNodeData.put("title", NodeTitle.CIRCUIT_CANVAS);
    canvasNodeData.put("text", "电路分析请求");
    canvasNodeData.put("circuitDesign", circuitDesign);
    canvasNodeData.put("mode", aiTaskMessage.getType());

    Node canvasNode =
        new Node(
            NodeType.CIRCUIT_CANVAS,
            objectMapper.writeValueAsString(canvasNodeData),
            objectMapper.writeValueAsString(XYPosition.origin()),
            aiTaskMessage.getParentId(),
            aiTaskMessage.getUserId(),
            aiTaskMessage.getConvId(),
            true);

    insertAndPublishNoneStreamNode(workContext, canvasNode, canvasNodeData);

    // 创建电路分析节点
    Map<String, Object> analyzeNodeData = new HashMap<>();
    analyzeNodeData.put("title", NodeTitle.CIRCUIT_ANALYSIS);
    analyzeNodeData.put("text", "");
    analyzeNodeData.put("subtype", NodeSubType.CIRCUIT_ANALYZE);
    analyzeNodeData.put("contextTitle", resolveDesignTitle(circuitDesign));
    analyzeNodeData.put("followUps", new ArrayList<>());
    analyzeNodeData.put("isGenerated", false);

    Node analyzeNode =
        new Node(
            NodeType.CIRCUIT_ANALYZE,
            objectMapper.writeValueAsString(analyzeNodeData),
            objectMapper.writeValueAsString(new XYPosition(420, 0)),
            canvasNode.getId(),
            aiTaskMessage.getUserId(),
            aiTaskMessage.getConvId(),
            true);
    insertAndPublishStreamNode(workContext, analyzeNode, analyzeNodeData);
    log.info(
        "[CircuitAnalysis-Tool] 初始化节点完成, canvasNodeId={}, analyzeNodeId={}",
        canvasNode.getId(),
        analyzeNode.getId());
  }

  @Override
  public void streamChat(WorkContext workContext, StreamingResponseHandler<AiMessage> handler)
      throws JsonProcessingException {
    AiTaskMessage aiTaskMessage = workContext.getAiTaskMessage();

    // 解析电路设计JSON
    CircuitDesign circuitDesign =
        objectMapper.readValue(aiTaskMessage.getPrompt(), CircuitDesign.class);

    // 生成SPICE网表
    String spiceNetlist = spiceConverter.generateNetlist(circuitDesign);

    // 获取用户可访问的知识库ID列表
    List<String> knowledgeBaseIds =
        knowledgeBaseService.getUserAccessibleKnowledgeBaseIds(
            aiTaskMessage.getUserId(), aiTaskMessage.getClassId());

    // 构建系统提示词
    String systemPrompt = buildSystemPrompt();
    if (agenticRagTool == null) {
      systemPrompt = systemPrompt + "\n\n【注意】当前环境未启用知识库检索工具，请不要生成任何tool调用内容。";
    }

    // 构建用户查询（包含电路信息）
    String userQuery = buildUserQuery(circuitDesign, spiceNetlist);
    RagChatResponseVO ragResponse =
        ragEnhancementService.queryWithTimeout(
            knowledgeBaseIds,
            userQuery,
            workContext.getHistory(),
            aiTaskMessage.getUserId(),
            aiTaskMessage.getClassId());
    if (ragResponse != null
        && ragResponse.getAnswer() != null
        && !ragResponse.getAnswer().isBlank()) {
      userQuery = "【知识库检索结果】\n" + ragResponse.getAnswer() + "\n\n" + userQuery;
    }

    log.info(
        "[CircuitAnalysis-Tool] 开始Tool-based对话生成: circuit_elements={}, kb_count={}",
        circuitDesign.getElements() != null ? circuitDesign.getElements().size() : 0,
        knowledgeBaseIds.size());

    // 使用streamAiService的toolBasedChat方法
    streamAiService.toolBasedChat(
        systemPrompt,
        userQuery,
        agenticRagTool != null ? new Object[] {agenticRagTool} : new Object[] {},
        aiTaskMessage.getModel(),
        CircuitAnalysisAssistant.class,
        handler);
  }

  /** 构建系统提示词 */
  private String buildSystemPrompt() {
    return """
            你是一位专业的电路分析助教，负责帮助学生理解电路设计和工作原理。

            **重要规则**：
            - 当需要查询教材、课件、电路知识库中的内容时，使用searchKnowledgeBase工具
            - 当涉及电路理论、公式推导、元件特性时，优先调用searchKnowledgeBase工具
            - 对于电路结构分析、拓扑分析等可以直接基于提供的网表和电路图回答
            - 回答要准确、详细，结合理论知识和实际电路分析

            **回答要求**：
            1. 先判断电路类型/功能（如放大、滤波、整流等），说明判断依据。
            2. 标注关键器件与关键节点，必要时用KCL/KVL或等效模型做简要校验。
            3. 如果缺少参数或电路图信息，明确缺失项并给出需要补充的具体数据。
            4. 给出可操作的检查步骤或改进建议，突出电子电路实践可落地性。

            **输出格式要求**：
            请按照以下格式输出电路分析结果：

            ### 预热导语
            [对电路的总体评价和核心特点，2-3句话]

            ### 追问方向概览
            - **[追问1标题]**：[简短说明，引导学生思考] _(意图: [分析/概念/计算])_
            - **[追问2标题]**：[简短说明] _(意图: [分析/概念/计算])_
            - **[追问3标题]**：[简短说明] _(意图: [分析/概念/计算])_

            请基于知识库内容和你的专业知识，为学生提供准确、详细的电路分析。
            """;
  }

  /** 构建用户查询（包含电路信息） */
  private String buildUserQuery(CircuitDesign circuitDesign, String spiceNetlist) {
    StringBuilder query = new StringBuilder();

    query.append("## 电路分析任务\n\n");

    // 添加电路元件信息
    if (circuitDesign.getElements() != null && !circuitDesign.getElements().isEmpty()) {
      query.append("**电路元件清单**:\n");
      circuitDesign
          .getElements()
          .forEach(
              elem ->
                  query
                      .append(String.format("- %s (类型: %s", elem.getId(), elem.getType()))
                      .append(
                          elem.getProperties() != null && elem.getProperties().containsKey("value")
                              ? ", 值: " + elem.getProperties().get("value")
                              : "")
                      .append(")\n"));
      query.append("\n");
    }

    // 添加电路连接信息
    if (circuitDesign.getConnections() != null && !circuitDesign.getConnections().isEmpty()) {
      query.append("**电路连接数量**: ").append(circuitDesign.getConnections().size()).append("\n\n");
    }

    // 添加SPICE网表
    if (spiceNetlist != null && !spiceNetlist.isBlank()) {
      query.append("**SPICE网表**:\n```spice\n");
      query.append(spiceNetlist);
      query.append("\n```\n\n");
    }

    // 添加电路元数据
    if (circuitDesign.getMetadata() != null) {
      if (circuitDesign.getMetadata().getTitle() != null) {
        query.append("**电路标题**: ").append(circuitDesign.getMetadata().getTitle()).append("\n");
      }
      if (circuitDesign.getMetadata().getDescription() != null) {
        query
            .append("**电路描述**: ")
            .append(circuitDesign.getMetadata().getDescription())
            .append("\n");
      }
      query.append("\n");
    }

    // 添加分析要求
    query.append("**分析要求**:\n");
    query.append("1. 分析电路拓扑结构和工作原理\n");
    query.append("2. 识别电路类型和应用场景\n");
    query.append("3. 提供3个追问方向，引导学生深入学习\n");
    query.append("4. 如果涉及理论知识，请调用searchKnowledgeBase工具查询教材内容\n");

    return query.toString();
  }

  @Override
  public void createOtherNodesOrUpdateNodeData(WorkContext workContext)
      throws JsonProcessingException {
    RStream<String, Object> redisStream = workContext.getRedisStream();
    Node streamNode = workContext.getStreamNode();
    Map<String, Object> streamNodeData = workContext.getStreamNodeData();
    Response<AiMessage> streamResponse = workContext.getStreamResponse();

    String responseText = streamResponse.content().text();
    CircuitWarmupResult warmupResult = parseWarmupResponse(responseText);
    String displayText = buildDisplayText(warmupResult);
    List<Map<String, Object>> followUpPayload =
        warmupResult.getFollowUps().stream()
            .map(FollowUpSuggestion::toPayload)
            .collect(Collectors.toList());

    streamNodeData.put("text", displayText);
    streamNodeData.put("followUps", followUpPayload);
    streamNodeData.put("isGenerated", true);
    streamNodeData.put("isDone", true);
    log.info(
        "[CircuitAnalysis-Tool] 解析完成, nodeId={}, warmupLength={}, followUps={}",
        streamNode.getId(),
        displayText.length(),
        followUpPayload.size());

    Map<String, Object> dataPayload = new HashMap<>();
    dataPayload.put("nodeId", streamNode.getId());
    dataPayload.put("text", displayText);
    dataPayload.put("followUps", followUpPayload);
    dataPayload.put("isGenerated", true);
    dataPayload.put("isDone", true);
    redisStream.add(StreamAddArgs.entries("type", AiTaskMessageType.DATA, "data", dataPayload));
  }

  @Override
  public void updateStreamNode(WorkContext workContext) throws JsonProcessingException {
    Node streamNode = workContext.getStreamNode();
    Map<String, Object> streamNodeData = workContext.getStreamNodeData();
    streamNode.setIsDeleted(false);
    streamNode.setData(objectMapper.writeValueAsString(streamNodeData));
    workContext.getNodesToUpdate().add(streamNode);
  }

  private CircuitWarmupResult parseWarmupResponse(String responseText) {
    String normalized = Optional.ofNullable(responseText).orElse("").replace("\r", "").trim();
    String warmupSection = normalized;
    String followUpSection = "";

    final String warmupFlag = "### 预热导语";
    final String followFlag = "### 追问方向概览";
    int warmupIndex = normalized.indexOf(warmupFlag);
    if (warmupIndex >= 0) {
      int followIndex = normalized.indexOf(followFlag, warmupIndex + warmupFlag.length());
      if (followIndex >= 0) {
        warmupSection = normalized.substring(warmupIndex + warmupFlag.length(), followIndex).trim();
        followUpSection = normalized.substring(followIndex + followFlag.length()).trim();
      } else {
        warmupSection = normalized.substring(warmupIndex + warmupFlag.length()).trim();
      }
    }

    List<FollowUpSuggestion> followUps =
        followUpSection.isEmpty() ? Collections.emptyList() : parseFollowUpSection(followUpSection);

    String resolvedWarmup = warmupSection.isEmpty() ? normalized : warmupSection;
    return new CircuitWarmupResult(resolvedWarmup, followUps);
  }

  private List<FollowUpSuggestion> parseFollowUpSection(String followUpSection) {
    List<String> entries = splitFollowUpEntries(followUpSection);
    List<FollowUpSuggestion> followUps = new ArrayList<>();
    for (int i = 0; i < entries.size(); i++) {
      FollowUpSuggestion suggestion = buildFollowUpSuggestion(entries.get(i), i + 1);
      if (suggestion != null) {
        followUps.add(suggestion);
      }
    }
    return followUps;
  }

  private List<String> splitFollowUpEntries(String section) {
    String[] lines = section.split("\n");
    List<String> entries = new ArrayList<>();
    StringBuilder current = new StringBuilder();
    Pattern entryPattern = Pattern.compile("^-\\s*\\*\\*.*");

    for (String rawLine : lines) {
      String line = rawLine.trim();
      if (line.isEmpty()) {
        continue;
      }
      Matcher matcher = entryPattern.matcher(line);
      if (matcher.find()) {
        if (!current.isEmpty()) {
          entries.add(current.toString().trim());
        }
        current.setLength(0);
        current.append(line);
      } else if (!current.isEmpty()) {
        current.append(' ').append(line);
      }
    }
    if (!current.isEmpty()) {
      entries.add(current.toString().trim());
    }
    if (entries.isEmpty() && !section.isBlank()) {
      entries.add(section.trim());
    }
    return entries;
  }

  private FollowUpSuggestion buildFollowUpSuggestion(String entry, int order) {
    if (entry == null || entry.isBlank()) {
      return null;
    }
    String normalized = entry.replace("\r", "").trim();

    // 解析格式：- **[标题]**：[说明] _(意图: [分析])_
    String title = null;
    String hint = null;
    String intent = null;

    // 提取标题
    Pattern titlePattern = Pattern.compile("\\*\\*([^*]+)\\*\\*");
    Matcher titleMatcher = titlePattern.matcher(normalized);
    if (titleMatcher.find()) {
      title = titleMatcher.group(1).trim();
    }

    // 提取意图
    Pattern intentPattern = Pattern.compile("_\\(意图:\\s*([^)]+)\\)_");
    Matcher intentMatcher = intentPattern.matcher(normalized);
    if (intentMatcher.find()) {
      intent = intentMatcher.group(1).trim();
    }

    // 提取说明（在标题和意图之间）
    if (titleMatcher.find()) {
      int titleEnd = titleMatcher.end();
      int intentStart = intentMatcher.find() ? intentMatcher.start() : normalized.length();
      hint = normalized.substring(titleEnd, intentStart).trim();
      // 去除前导的：或:
      if (hint.startsWith("：") || hint.startsWith(":")) {
        hint = hint.substring(1).trim();
      }
    }

    if (title == null || title.isBlank()) {
      title = "追问 " + order;
    }

    String followUp = hint != null && !hint.isBlank() ? hint : title;

    return new FollowUpSuggestion(title, hint, followUp, intent, null);
  }

  private String buildDisplayText(CircuitWarmupResult result) {
    StringBuilder builder = new StringBuilder();
    if (hasText(result.getWarmupText())) {
      builder.append("### 预热导语\n");
      builder.append(result.getWarmupText().trim()).append("\n\n");
    }
    if (!result.getFollowUps().isEmpty()) {
      builder.append("### 追问方向概览\n");
      for (FollowUpSuggestion followUp : result.getFollowUps()) {
        String hint = hasText(followUp.getHint()) ? followUp.getHint() : "可以继续追问这一方向获取更深入的洞察。";
        builder.append("- **").append(followUp.getTitle()).append("**：").append(hint);
        if (hasText(followUp.getIntent())) {
          builder.append(" _(意图: ").append(followUp.getIntent()).append(")_");
        }
        builder.append("\n");
      }
    }
    String content = builder.toString().trim();
    return content.isEmpty() ? result.getWarmupText() : content;
  }

  private boolean hasText(String value) {
    return value != null && !value.trim().isEmpty();
  }

  private String resolveDesignTitle(CircuitDesign circuitDesign) {
    return Optional.ofNullable(circuitDesign)
        .map(CircuitDesign::getMetadata)
        .map(CircuitDesign.CircuitMetadata::getTitle)
        .filter(this::hasText)
        .orElse("电路分析");
  }

  @Getter
  private static class CircuitWarmupResult {
    private final String warmupText;
    private final List<FollowUpSuggestion> followUps;

    CircuitWarmupResult(String warmupText, List<FollowUpSuggestion> followUps) {
      this.warmupText = warmupText;
      this.followUps = followUps;
    }
  }

  @Getter
  private static class FollowUpSuggestion {
    private final String title;
    private final String hint;
    private final String followUp;
    private final String intent;
    private final Double confidence;

    FollowUpSuggestion(
        String title, String hint, String followUp, String intent, Double confidence) {
      this.title = title;
      this.hint = hint;
      this.followUp = followUp;
      this.intent = intent;
      this.confidence = confidence;
    }

    public Map<String, Object> toPayload() {
      Map<String, Object> map = new LinkedHashMap<>();
      map.put("title", title);
      if (hint != null) {
        map.put("hint", hint);
      }
      if (followUp != null) {
        map.put("followUp", followUp);
      }
      if (intent != null) {
        map.put("intent", intent);
      }
      if (confidence != null) {
        map.put("confidence", confidence);
      }
      return map;
    }
  }
}

package cn.yifan.drawsee.worker;

import cn.yifan.drawsee.assistant.CircuitAnalysisAssistant;
import cn.yifan.drawsee.constant.AiTaskMessageType;
import cn.yifan.drawsee.constant.NodeSubType;
import cn.yifan.drawsee.constant.NodeType;
import cn.yifan.drawsee.mapper.AiTaskMapper;
import cn.yifan.drawsee.mapper.ConversationMapper;
import cn.yifan.drawsee.mapper.NodeMapper;
import cn.yifan.drawsee.mapper.UserMapper;
import cn.yifan.drawsee.pojo.XYPosition;
import cn.yifan.drawsee.pojo.entity.Node;
import cn.yifan.drawsee.pojo.rabbit.AiTaskMessage;
import cn.yifan.drawsee.service.base.AiService;
import cn.yifan.drawsee.service.base.MinioService;
import cn.yifan.drawsee.service.base.PromptService;
import cn.yifan.drawsee.service.base.StreamAiService;
import cn.yifan.drawsee.service.business.ContextBudgetManager;
import cn.yifan.drawsee.service.business.KnowledgeBaseService;
import cn.yifan.drawsee.service.business.RagEnhancementService;
import cn.yifan.drawsee.tool.AgenticRagTool;
import cn.yifan.drawsee.util.PdfUtils;
import cn.yifan.drawsee.pojo.vo.rag.RagChatResponseVO;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.model.StreamingResponseHandler;
import dev.langchain4j.model.output.Response;
import io.minio.GetObjectResponse;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RedissonClient;
import org.redisson.api.RStream;
import org.redisson.api.stream.StreamAddArgs;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * PDF电路实验任务分析点详情工作流 - Tool-based架构
 *
 * 架构变更：
 * - 旧版：Python LLM生成答案，Java转发SSE流
 * - 新版：Java LangChain4j主导对话生成，Python仅提供RAG工具
 *
 * @author Drawsee Team
 */

@Slf4j
@Service
public class PdfCircuitAnalysisDetailWorkFlow extends WorkFlow {

    private final PromptService promptService;
    private final MinioService minioService;
    private final KnowledgeBaseService knowledgeBaseService;
    private final AgenticRagTool agenticRagTool;
    private final RagEnhancementService ragEnhancementService;

    public PdfCircuitAnalysisDetailWorkFlow(
        UserMapper userMapper,
        AiService aiService,
        StreamAiService streamAiService,
        RedissonClient redissonClient,
        NodeMapper nodeMapper,
        ConversationMapper conversationMapper,
        AiTaskMapper aiTaskMapper,
        ObjectMapper objectMapper,
        PromptService promptService,
        MinioService minioService,
        KnowledgeBaseService knowledgeBaseService,
        ObjectProvider<AgenticRagTool> agenticRagToolProvider,
        ContextBudgetManager contextBudgetManager,
        RagEnhancementService ragEnhancementService
    ) {
        super(userMapper, aiService, streamAiService, redissonClient, nodeMapper, conversationMapper, aiTaskMapper, objectMapper, contextBudgetManager);
        this.promptService = promptService;
        this.minioService = minioService;
        this.knowledgeBaseService = knowledgeBaseService;
        this.agenticRagTool = agenticRagToolProvider.getIfAvailable();
        this.ragEnhancementService = ragEnhancementService;
    }

    @Override
    public Boolean validateAndInit(WorkContext workContext) {
        Boolean isValid = super.validateAndInit(workContext);
        if (!isValid) return false;

        Node parentNode = workContext.getParentNode();
        AiTaskMessage aiTaskMessage = workContext.getAiTaskMessage();

        // 校验父节点类型是否为PDF电路实验任务分析点节点
        if (!parentNode.getType().equals(NodeType.PDF_CIRCUIT_POINT)) {
            log.error("父节点不是PDF电路实验任务分析点节点, taskMessage: {}", aiTaskMessage);
            return false;
        }

        return true;
    }

    /**
     * 覆盖父类方法，避免创建QUERY节点
     * 直接使用PDF_CIRCUIT_POINT节点作为详情回答的父节点
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

        // 从父节点(PDF_CIRCUIT_POINT)中读取角度信息
        TypeReference<Map<String, Object>> dataTypeRef = new TypeReference<>() {};
        Map<String, Object> parentNodeData = objectMapper.readValue(parentNode.getData(), dataTypeRef);
        String angleTitle = (String) parentNodeData.get("title");

        // 创建详细回答节点
        Map<String, Object> pdfCircuitDetailNodeData = new ConcurrentHashMap<>();
        pdfCircuitDetailNodeData.put("subtype", NodeSubType.PDF_CIRCUIT_DETAIL);
        pdfCircuitDetailNodeData.put("title", angleTitle + "详情");
        pdfCircuitDetailNodeData.put("text", "");
        pdfCircuitDetailNodeData.put("angle", angleTitle);
        pdfCircuitDetailNodeData.put("parentPointId", String.valueOf(parentNodeId));
        pdfCircuitDetailNodeData.put("isGenerated", false);
        pdfCircuitDetailNodeData.put("isDone", false);

        Node pdfCircuitDetailNode = new Node(
            NodeType.PDF_CIRCUIT_DETAIL,
            objectMapper.writeValueAsString(pdfCircuitDetailNodeData),
            objectMapper.writeValueAsString(XYPosition.origin()),
            parentNodeId,
            aiTaskMessage.getUserId(),
            aiTaskMessage.getConvId(),
            true
        );
        insertAndPublishStreamNode(workContext, pdfCircuitDetailNode, pdfCircuitDetailNodeData);
    }

    @Override
    public void streamChat(WorkContext workContext, StreamingResponseHandler<AiMessage> handler) throws JsonProcessingException {
        AiTaskMessage aiTaskMessage = workContext.getAiTaskMessage();
        Node parentNode = workContext.getParentNode();
        String model = aiTaskMessage.getModel();

        // 从父节点(PDF_CIRCUIT_POINT)中读取角度信息
        TypeReference<Map<String, Object>> dataTypeRef = new TypeReference<>() {};
        Map<String, Object> parentNodeData = objectMapper.readValue(parentNode.getData(), dataTypeRef);
        String angleTitle = (String) parentNodeData.get("title");

        // 找到PDF文档URL并提取文本
        String pdfUrl = findPdfUrl(workContext);
        if (pdfUrl == null) {
            log.error("未找到PDF文档URL, taskMessage: {}", aiTaskMessage);
            throw new RuntimeException("未找到PDF文档URL");
        }

        // 提取PDF文本
        String prompt;
        String extractedText = tryExtractPdfTextFromMinioUrl(pdfUrl);
        if (extractedText != null && !extractedText.isBlank()) {
            // 为避免上下文过长，做一次长度裁剪
            int maxChars = 6000;
            if (extractedText.length() > maxChars) {
                extractedText = extractedText.substring(0, maxChars);
            }
            prompt = promptService.getPdfCircuitPointDetailPrompt(extractedText, angleTitle);
            log.info("PDF电路分析详情工作流：使用抽取的PDF正文生成提示词，chars={}, angle={}", extractedText.length(), angleTitle);
        } else {
            // 回退：直接把 URL 作为文本放入模板
            prompt = promptService.getPdfCircuitPointDetailPrompt("文档链接：" + pdfUrl + "\n(如无法访问链接，请根据常见电路实验任务要点给出通用分析)", angleTitle);
            log.info("PDF电路分析详情工作流：未能抽取正文，回退为URL兜底");
        }

        LinkedList<ChatMessage> history = applyHistoryBudget(
            workContext,
            planContextBudget(workContext, prompt)
        );

        // 构建Agentic RAG查询（PDF分析点详情）
        String detailQuery = String.format(
            "根据实验任务文档，详细分析【%s】这个角度：\n\n%s",
            angleTitle,
            extractedText != null ? extractedText : "文档链接：" + pdfUrl
        );

        // 获取知识库列表（使用权限验证）
        List<String> knowledgeBaseIds = knowledgeBaseService.getUserAccessibleKnowledgeBaseIds(
            aiTaskMessage.getUserId() != null ? aiTaskMessage.getUserId() : 0L,
            null  // PDF详情工作流通常没有班级上下文
        );
        RagChatResponseVO ragResponse = ragEnhancementService.queryWithTimeout(
            knowledgeBaseIds,
            detailQuery,
            history,
            aiTaskMessage.getUserId(),
            aiTaskMessage.getClassId()
        );
        if (ragResponse != null && ragResponse.getAnswer() != null && !ragResponse.getAnswer().isBlank()) {
            detailQuery = "【知识库检索结果】\n" + ragResponse.getAnswer() + "\n\n" + detailQuery;
        }

        log.info("[PdfCircuitAnalysisDetail-Tool] 开始Tool-based对话生成, angle={}", angleTitle);

        // 构建系统提示词
        String systemPrompt = """
            你是一位专业的电路分析助教，负责帮助学生深入理解PDF实验任务的具体分析点。

            **重要规则**：
            - 当需要查询教材、课件、电路知识库中的内容时，使用searchKnowledgeBase工具
            - 对于实验任务的具体分析点，提供详细、深入的解答
            - 回答要准确、详细，结合理论知识和实际电路分析

            请基于PDF文档内容和知识库内容，提供详细的分析点解答。
            """;
        if (agenticRagTool == null) {
            systemPrompt = systemPrompt + "\n\n【注意】当前环境未启用知识库检索工具，请不要生成任何tool调用内容。";
        }

        // 使用streamAiService的toolBasedChat方法
        streamAiService.toolBasedChat(
            systemPrompt,
            detailQuery,
            agenticRagTool != null ? new Object[]{agenticRagTool} : new Object[]{},
            aiTaskMessage.getModel(),
            CircuitAnalysisAssistant.class,
            handler
        );
    }

    /**
     * 从 MinIO 预签名 URL 解析 objectName 并抽取 PDF 文本
     * 返回抽取的文本；若失败返回 null
     */
    private String tryExtractPdfTextFromMinioUrl(String url) {
        try {
            if (url == null || url.isBlank()) return null;
            URI uri = URI.create(url);
            String path = uri.getPath(); // 形如 /{bucket}/{object...}
            if (path == null || path.length() <= 1) return null;
            String[] parts = path.split("/", 3);
            if (parts.length < 3) return null; // 不符合 /bucket/object 结构
            String objectName = parts[2];
            if (!objectName.toLowerCase().endsWith(".pdf")) {
                // 仅在 PDF 时尝试抽取
                return null;
            }
            try (GetObjectResponse resp = minioService.getObjectStream(objectName)) {
                String text = PdfUtils.extractAllText(resp);
                return text == null ? null : text.trim();
            }
        } catch (Exception e) {
            log.warn("从URL抽取PDF正文失败，url={}，error={}", url, e.toString());
            return null;
        }
    }

    /**
     * 向上回溯查找PDF文档URL
     * @param workContext 工作上下文
     * @return PDF文档URL
     */
    private String findPdfUrl(WorkContext workContext) {
        Node parentNode = workContext.getParentNode();
        Long convId = parentNode.getConvId();
        List<Node> nodes = nodeMapper.getByConvId(convId);
        Map<Long, Node> nodeMap = nodes.stream().collect(ConcurrentHashMap::new, (map, node) -> map.put(node.getId(), node), ConcurrentHashMap::putAll);

        // 向上回溯查找QUERY节点
        Node currentNode = parentNode;
        while (currentNode != null && !currentNode.getType().equals(NodeType.ROOT)) {
            Node nextNode = nodeMap.get(currentNode.getParentId());

            // 如果下一个节点是QUERY节点，则获取PDF文档URL
            if (nextNode != null && nextNode.getType().equals(NodeType.QUERY)) {
                try {
                    TypeReference<Map<String, Object>> dataTypeRef = new TypeReference<>() {};
                    Map<String, Object> queryNodeData = objectMapper.readValue(nextNode.getData(), dataTypeRef);
                    return (String) queryNodeData.get("text");
                } catch (JsonProcessingException e) {
                    log.error("解析查询节点数据失败: {}", e.getMessage());
                    return null;
                }
            }

            currentNode = nextNode;
        }

        return null;
    }

    @Override
    public void createOtherNodesOrUpdateNodeData(WorkContext workContext) throws JsonProcessingException {
        RStream<String, Object> redisStream = workContext.getRedisStream();
        Node streamNode = workContext.getStreamNode();
        Map<String, Object> streamNodeData = workContext.getStreamNodeData();
        Response<AiMessage> streamResponse = workContext.getStreamResponse();
        String detailText = streamResponse.content().text();

        streamNodeData.put("text", detailText);
        streamNodeData.put("isGenerated", true);
        streamNodeData.put("isDone", true);

        Map<String, Object> payload = new ConcurrentHashMap<>();
        payload.put("nodeId", streamNode.getId());
        payload.put("text", detailText);
        payload.put("isGenerated", true);
        payload.put("isDone", true);
        redisStream.add(StreamAddArgs.entries(
            "type", AiTaskMessageType.DATA,
            "data", payload
        ));
    }
}

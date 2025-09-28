
package cn.yifan.drawsee.worker;

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
import cn.yifan.drawsee.service.base.MinioService;
import cn.yifan.drawsee.service.base.PromptService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.StreamingResponseHandler;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Service;

import java.util.LinkedList;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.net.URI;

import io.minio.GetObjectResponse;

import cn.yifan.drawsee.util.PdfUtils;

/**
 * @FileName PdfCircuitAnalysisWorkFlow
 * @Description 处理PDF电路实验任务文档分析的工作流，支持分点生成与分点详情展开
 * @Author yifan
 * @date 2025-09-25
 **/
@Slf4j
@Service
public class PdfCircuitAnalysisWorkFlow extends WorkFlow {
    private final PromptService promptService;
    private final MinioService minioService;

    public PdfCircuitAnalysisWorkFlow(
            UserMapper userMapper,
            AiService aiService,
            StreamAiService streamAiService,
            RedissonClient redissonClient,
            NodeMapper nodeMapper,
            ConversationMapper conversationMapper,
            AiTaskMapper aiTaskMapper,
            ObjectMapper objectMapper,
            PromptService promptService,
            MinioService minioService
    ) {
        super(userMapper, aiService, streamAiService, redissonClient, nodeMapper, conversationMapper, aiTaskMapper, objectMapper);
        this.promptService = promptService;
        this.minioService = minioService;
    }

    @Override
    public Boolean validateAndInit(WorkContext workContext) {
        log.info("PDF电路分析工作流开始验证和初始化，taskId: {}", workContext.getAiTaskMessage().getTaskId());
        Boolean isValid = super.validateAndInit(workContext);
        if (!isValid) {
            log.error("PDF电路分析工作流验证失败，taskId: {}", workContext.getAiTaskMessage().getTaskId());
            return false;
        }
        
        // 设置需要发送DONE消息
        workContext.setIsSendDone(true);
        log.info("PDF电路分析工作流验证和初始化成功，taskId: {}", workContext.getAiTaskMessage().getTaskId());
        return true;
    }

    /**
     * 创建PDF分析的查询节点，节点内容为PDF URL
     */
    @Override
    public Long createInitQueryNode(WorkContext workContext) throws JsonProcessingException {
        AiTaskMessage aiTaskMessage = workContext.getAiTaskMessage();
        Map<String, Object> queryNodeData = new ConcurrentHashMap<>();
        queryNodeData.put("title", NodeTitle.QUERY);
        queryNodeData.put("text", aiTaskMessage.getPrompt()); // PDF URL
        queryNodeData.put("mode", aiTaskMessage.getType());
        Node queryNode = new Node(
                NodeType.QUERY,
                objectMapper.writeValueAsString(queryNodeData),
                objectMapper.writeValueAsString(XYPosition.origin()),
                aiTaskMessage.getParentId(),
                aiTaskMessage.getUserId(),
                aiTaskMessage.getConvId(),
                true
        );
        insertAndPublishNoneStreamNode(workContext, queryNode, queryNodeData);
        LinkedList<ChatMessage> history = workContext.getHistory();
        if (history == null) {
            history = new LinkedList<>();
            workContext.setHistory(history);
        }
        // 添加PDF内容到历史
    String pdfContent = aiTaskMessage.getPrompt(); // 直接用URL或前端传递的内容
    history.add(new UserMessage(pdfContent));
        return queryNode.getId();
    }

    /**
     * 创建初始节点：只创建查询节点，不创建流式节点
     */
    @Override
    public void createInitNodes(WorkContext workContext) throws JsonProcessingException {
        log.info("PDF电路分析工作流开始创建初始节点，taskId: {}", workContext.getAiTaskMessage().getTaskId());
        Long queryNodeId = createInitQueryNode(workContext);
        setupVirtualStreamNode(workContext, queryNodeId);
        log.info("PDF电路分析工作流初始节点创建完成，taskId: {}", workContext.getAiTaskMessage().getTaskId());
    }

    /**
     * 设置虚拟流式节点，便于后续分点生成
     */
    private void setupVirtualStreamNode(WorkContext workContext, Long parentNodeId) throws JsonProcessingException {
        AiTaskMessage aiTaskMessage = workContext.getAiTaskMessage();
        Map<String, Object> streamNodeData = new ConcurrentHashMap<>();
        streamNodeData.put("title", NodeTitle.ANSWER_POINT);
        streamNodeData.put("text", "");
        Node virtualNode = new Node(
                null,
                objectMapper.writeValueAsString(streamNodeData),
                objectMapper.writeValueAsString(XYPosition.origin()),
                parentNodeId,
                aiTaskMessage.getUserId(),
                aiTaskMessage.getConvId(),
                false
        );
        virtualNode.setId(-1L);
        workContext.setStreamNode(virtualNode);
        workContext.setStreamNodeData(streamNodeData);
    }

    /**
     * 不创建流式节点，保持空实现
     */
    @Override
    public void createInitStreamNode(WorkContext workContext, Long parentNodeId) throws JsonProcessingException {
        // 空实现
    }

    /**
     * 分点生成，调用AI分析PDF内容，生成分点
     */
    @Override
    public void streamChat(WorkContext workContext, StreamingResponseHandler<AiMessage> handler) throws JsonProcessingException {
        log.info("PDF电路分析工作流开始流式聊天，taskId: {}", workContext.getAiTaskMessage().getTaskId());
        AiTaskMessage aiTaskMessage = workContext.getAiTaskMessage();
        LinkedList<ChatMessage> history = workContext.getHistory();
        
        // PDF电路分析工作流：基于PDF URL进行快速分析
        // 不涉及复杂的PDF内容提取，专注于生成分析分点
        String pdfUrl = aiTaskMessage.getPrompt();
        log.info("PDF电路分析工作流处理PDF URL: {}, taskId: {}", pdfUrl, aiTaskMessage.getTaskId());

        // 优先尝试：如果是 MinIO 预签名 URL，下载 PDF 并抽取正文作为 {{text}}
        String prompt;
        String extractedText = tryExtractPdfTextFromMinioUrl(pdfUrl);
        if (extractedText != null && !extractedText.isBlank()) {
            // 为避免上下文过长，做一次长度裁剪
            int maxChars = 6000;
            if (extractedText.length() > maxChars) {
                extractedText = extractedText.substring(0, maxChars);
            }
            prompt = promptService.getPdfCircuitAnalysisPrompt(extractedText);
            log.info("PDF电路分析工作流：使用抽取的PDF正文生成提示词，chars={}", extractedText.length());
        } else {
            // 回退：直接把 URL 作为文本放入模板（模型可能无法访问网络，仅供兜底）
            prompt = promptService.getPdfCircuitAnalysisPrompt("文档链接：" + pdfUrl + "\n(如无法访问链接，请根据常见电路实验任务要点给出通用分点)");
            log.info("PDF电路分析工作流：未能抽取正文，回退为URL兜底");
        }

        log.info("PDF电路分析工作流生成提示词完成，taskId: {}", aiTaskMessage.getTaskId());
        streamAiService.answerPointChat(history, prompt, aiTaskMessage.getModel(), handler);
        log.info("PDF电路分析工作流流式聊天调用完成，taskId: {}", aiTaskMessage.getTaskId());
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
     * 解析AI返回的分点，创建分点节点
     */
    @Override
    protected void createAnswerPointNodes(WorkContext workContext) throws JsonProcessingException {
        Node streamNode = workContext.getStreamNode();
        dev.langchain4j.model.output.Response<AiMessage> streamResponse = workContext.getStreamResponse();
        Long parentId = streamNode.getParentId();
        String responseText = streamResponse.content().text();
        processAnswerAngles(workContext, responseText, parentId);
    }

    /**
     * 处理分点文本，创建分点节点
     */
    private void processAnswerAngles(WorkContext workContext, String responseText, Long parentId) throws JsonProcessingException {
        AiTaskMessage aiTaskMessage = workContext.getAiTaskMessage();
        String[] lines = responseText.split("\n");
        String currentTitle = null;
        String currentDescription = null;
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i].trim();
            if (line.isEmpty()) {
                if (currentTitle != null && currentDescription != null) {
                    createAnswerPointNode(workContext, parentId, aiTaskMessage, currentTitle, currentDescription);
                    currentTitle = null;
                    currentDescription = null;
                }
                continue;
            }
            if (line.matches("^角度\\d+：.+")) {
                if (currentTitle != null && currentDescription != null) {
                    createAnswerPointNode(workContext, parentId, aiTaskMessage, currentTitle, currentDescription);
                }
                currentTitle = line.substring(line.indexOf("：") + 1).trim();
                currentDescription = null;
            } else if (currentTitle != null && currentDescription == null) {
                currentDescription = line;
            }
        }
        if (currentTitle != null && currentDescription != null) {
            createAnswerPointNode(workContext, parentId, aiTaskMessage, currentTitle, currentDescription);
        }
    }

    /**
     * 创建分点节点
     */
    private void createAnswerPointNode(WorkContext workContext, Long parentId, AiTaskMessage aiTaskMessage, String title, String description) throws JsonProcessingException {
        Map<String, Object> answerPointNodeData = new ConcurrentHashMap<>();
        answerPointNodeData.put("title", title);
        answerPointNodeData.put("text", description);
        answerPointNodeData.put("subtype", NodeType.PDF_CIRCUIT_POINT);
        Node answerPointNode = new Node(
                NodeType.PDF_CIRCUIT_POINT,
                objectMapper.writeValueAsString(answerPointNodeData),
                objectMapper.writeValueAsString(XYPosition.origin()),
                parentId,
                aiTaskMessage.getUserId(),
                aiTaskMessage.getConvId(),
                true
        );
        insertAndPublishNoneStreamNode(workContext, answerPointNode, answerPointNodeData);
    }

    /**
     * 创建其他节点或更新节点数据
     */
    @Override
    public void createOtherNodesOrUpdateNodeData(WorkContext workContext) throws JsonProcessingException {
        // 对于PDF电路分析任务，创建回答角度相关节点
        createAnswerPointNodes(workContext);
    }

    /**
     * 不更新流节点内容，跳过
     */
    @Override
    public void updateStreamNode(WorkContext workContext) throws JsonProcessingException {
        log.info("跳过虚拟流节点更新");
    }


}

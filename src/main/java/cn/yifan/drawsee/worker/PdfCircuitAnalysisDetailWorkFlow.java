package cn.yifan.drawsee.worker;

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
import cn.yifan.drawsee.util.PdfUtils;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.StreamingResponseHandler;
import io.minio.GetObjectResponse;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * @FileName PdfCircuitAnalysisDetailWorkFlow
 * @Description 处理PDF电路实验任务分析点详情任务的工作流
 * @Author yifan
 * @date 2025-10-08
 **/

@Slf4j
@Service
public class PdfCircuitAnalysisDetailWorkFlow extends WorkFlow {

    private final PromptService promptService;
    private final MinioService minioService;

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
        MinioService minioService
    ) {
        super(userMapper, aiService, streamAiService, redissonClient, nodeMapper, conversationMapper, aiTaskMapper, objectMapper);
        this.promptService = promptService;
        this.minioService = minioService;
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

        Node pdfCircuitDetailNode = new Node(
            NodeType.ANSWER,
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
        LinkedList<ChatMessage> history = workContext.getHistory();
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

        // 将系统提示词添加到历史消息中
        history.add(new UserMessage(prompt));

        // 调用AI进行详细分析，使用answerDetailChat
        streamAiService.answerDetailChat(history, "", angleTitle, model, handler);
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
}

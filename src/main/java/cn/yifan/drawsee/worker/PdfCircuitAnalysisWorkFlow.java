package cn.yifan.drawsee.worker;

import cn.yifan.drawsee.constant.NodeTitle;
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
import cn.yifan.drawsee.service.base.PdfMultimodalService;
import cn.yifan.drawsee.service.base.PromptService;
import cn.yifan.drawsee.service.base.PythonRagService;
import cn.yifan.drawsee.service.base.StreamAiService;
import cn.yifan.drawsee.service.base.WebSearchService;
import cn.yifan.drawsee.util.PdfUtils;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.StreamingResponseHandler;
import dev.langchain4j.model.output.Response;
import io.minio.GetObjectResponse;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RStream;
import org.redisson.api.RedissonClient;
import org.redisson.api.stream.StreamAddArgs;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * @FileName PdfCircuitAnalysisWorkFlow
 * @Description PDF电路实验任务文档分析工作流，处理PDF电路实验任务分析任务
 * @Author yifan
 * @date 2025-10-08
 **/

@Slf4j
@Service
public class PdfCircuitAnalysisWorkFlow extends WorkFlow {

    private final PromptService promptService;
    private final MinioService minioService;
    private final PdfMultimodalService pdfMultimodalService;
    private final WebSearchService webSearchService;
    private final PythonRagService pythonRagService;

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
            MinioService minioService,
            PdfMultimodalService pdfMultimodalService,
            WebSearchService webSearchService,
            PythonRagService pythonRagService
    ) {
        super(userMapper, aiService, streamAiService, redissonClient, nodeMapper, conversationMapper, aiTaskMapper, objectMapper);
        this.promptService = promptService;
        this.minioService = minioService;
        this.pdfMultimodalService = pdfMultimodalService;
        this.webSearchService = webSearchService;
        this.pythonRagService = pythonRagService;
    }

    @Override
    public Boolean validateAndInit(WorkContext workContext) {
        log.info("PDF电路分析工作流开始验证和初始化，taskId: {}", workContext.getAiTaskMessage().getTaskId());
        Boolean isValid = super.validateAndInit(workContext);
        if (!isValid) {
            log.error("PDF电路分析工作流验证失败，taskId: {}", workContext.getAiTaskMessage().getTaskId());
            return false;
        }

        // 设置不需要发送DONE消息（在createOtherNodesOrUpdateNodeData中发送）
        workContext.setIsSendDone(false);
        log.info("PDF电路分析工作流验证和初始化成功，taskId: {}", workContext.getAiTaskMessage().getTaskId());
        return true;
    }

    /**
     * 覆盖父类方法，为PDF电路实验任务分析流程提供优化的实现
     * 不创建父角度节点，而是仅保留QUERY节点作为后续分析点节点的父节点
     */
    @Override
    public void createInitNodes(WorkContext workContext) throws JsonProcessingException {
        log.info("PDF电路分析工作流开始创建初始节点，taskId: {}", workContext.getAiTaskMessage().getTaskId());
        // 只创建查询节点，不创建流式节点
        Long queryNodeId = createInitQueryNode(workContext);

        // 创建一个虚拟的流式节点用于后续处理，但不实际存储到数据库
        setupVirtualStreamNode(workContext, queryNodeId);
        log.info("PDF电路分析工作流初始节点创建完成，taskId: {}", workContext.getAiTaskMessage().getTaskId());
    }

    /**
     * 覆盖父类方法，创建PDF实验任务查询节点
     */
    @Override
    public Long createInitQueryNode(WorkContext workContext) throws JsonProcessingException {
        AiTaskMessage aiTaskMessage = workContext.getAiTaskMessage();

        // 创建PDF实验任务查询节点
        Map<String, Object> queryNodeData = new ConcurrentHashMap<>();
        queryNodeData.put("title", NodeTitle.PDF_CIRCUIT_QUERY);
        queryNodeData.put("text", aiTaskMessage.getPrompt()); // PDF文档URL
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

        // 添加PDF内容到历史
        LinkedList<ChatMessage> history = workContext.getHistory();
        if (history == null) {
            history = new LinkedList<>();
            workContext.setHistory(history);
        }
        String pdfContent = aiTaskMessage.getPrompt(); // PDF URL
        history.add(new UserMessage(pdfContent));

        return queryNode.getId();
    }

    /**
     * 设置一个虚拟的流式节点，用于后续的分析点节点创建
     * 该节点不会实际插入数据库，仅用于后续处理
     */
    private void setupVirtualStreamNode(WorkContext workContext, Long parentNodeId) throws JsonProcessingException {
        AiTaskMessage aiTaskMessage = workContext.getAiTaskMessage();

        // 创建流式节点数据，但不实际保存到数据库
        Map<String, Object> streamNodeData = new ConcurrentHashMap<>();
        streamNodeData.put("title", NodeTitle.ANSWER_POINT);
        streamNodeData.put("text", "");

        // 创建一个Node对象，但不实际插入数据库
        Node virtualNode = new Node(
            null,
            objectMapper.writeValueAsString(streamNodeData),
            objectMapper.writeValueAsString(XYPosition.origin()),
            parentNodeId,
            aiTaskMessage.getUserId(),
            aiTaskMessage.getConvId(),
            false // 标记为非活跃
        );

        // 为虚拟节点设置一个临时ID
        virtualNode.setId(-1L);

        // 设置到WorkContext中
        workContext.setStreamNode(virtualNode);
        workContext.setStreamNodeData(streamNodeData);
    }

    /**
     * 不创建流式节点，保持空实现
     */
    @Override
    public void createInitStreamNode(WorkContext workContext, Long parentNodeId) throws JsonProcessingException {
        // 空实现，实际节点创建在setupVirtualStreamNode方法中
    }

    @Override
    public void streamChat(WorkContext workContext, StreamingResponseHandler<AiMessage> handler) throws JsonProcessingException {
        log.info("PDF电路分析工作流开始流式聊天，taskId: {}", workContext.getAiTaskMessage().getTaskId());
        AiTaskMessage aiTaskMessage = workContext.getAiTaskMessage();
        LinkedList<ChatMessage> history = workContext.getHistory();
        String model = aiTaskMessage.getModel();

        // 获取PDF文档URL
        String pdfUrl = aiTaskMessage.getPrompt();
        log.info("PDF电路分析工作流处理PDF URL: {}, taskId: {}", pdfUrl, aiTaskMessage.getTaskId());

        // 使用增强的多模态分析
        String enhancedContent = performEnhancedPdfAnalysis(pdfUrl);

        if (enhancedContent == null || enhancedContent.isBlank()) {
            // 回退到简单文本提取
            enhancedContent = tryExtractPdfTextFromMinioUrl(pdfUrl);
            if (enhancedContent != null && !enhancedContent.isBlank()) {
                int maxChars = 6000;
                if (enhancedContent.length() > maxChars) {
                    enhancedContent = enhancedContent.substring(0, maxChars);
                }
            } else {
                // 最终回退
                enhancedContent = "文档链接：" + pdfUrl + "\n(如无法访问链接，请根据常见电路实验任务要点给出通用分点)";
                log.warn("PDF电路分析工作流：未能抽取内容，使用URL兜底");
            }
        }

        // 生成提示词
        String prompt = promptService.getPdfCircuitPointAnalysisPrompt(enhancedContent);
        log.info("PDF电路分析工作流生成提示词完成，内容长度: {}, taskId: {}",
                 enhancedContent.length(), aiTaskMessage.getTaskId());

        // 调用AI进行PDF电路实验任务分析
        history.add(new UserMessage(prompt));
        streamAiService.answerPointChat(history, "", model, handler);
        log.info("PDF电路分析工作流流式聊天调用完成，taskId: {}", aiTaskMessage.getTaskId());
    }

    /**
     * 执行增强的PDF分析，包括多模态识别和元件信息搜索
     *
     * 注意：用户上传的PDF实验任务文档使用传统多模态分析方案，不调用Python RAG服务
     * Python RAG服务仅用于检索后台导入的知识文档
     *
     * @param pdfUrl PDF文档URL
     * @return 增强后的文档内容
     */
    private String performEnhancedPdfAnalysis(String pdfUrl) {
        try {
            log.info("开始执行增强PDF分析: {}", pdfUrl);

            // 1. 多模态分析（文本+图片）
            PdfMultimodalService.MultimodalAnalysis analysis =
                pdfMultimodalService.analyzePdfFromUrl(pdfUrl, 3); // 分析最复杂的3页

            if (analysis == null) {
                log.warn("多模态分析失败，返回null");
                return null;
            }

            StringBuilder enhancedContent = new StringBuilder();

            // 2. 组合文本内容
            if (analysis.getTextContent() != null && !analysis.getTextContent().isBlank()) {
                enhancedContent.append("【文档文本内容】\n");
                enhancedContent.append(analysis.getTextContent());
                enhancedContent.append("\n\n");
            }

            // 3. 添加图片分析结果
            if (!analysis.getImageAnalysis().isEmpty()) {
                enhancedContent.append("【关键图表分析】\n");
                for (String imageAnalysis : analysis.getImageAnalysis()) {
                    enhancedContent.append(imageAnalysis);
                    enhancedContent.append("\n\n");
                }
            }

            // 4. 智能提取元件名称并搜索资料
            List<String> components = extractComponentNames(enhancedContent.toString());
            if (!components.isEmpty()) {
                log.info("检测到{}个元器件，开始搜索资料: {}", components.size(), components);
                Map<String, String> componentInfo = webSearchService.batchSearchComponents(components);

                if (!componentInfo.isEmpty()) {
                    enhancedContent.append("【元器件资料】\n");
                    for (Map.Entry<String, String> entry : componentInfo.entrySet()) {
                        enhancedContent.append(entry.getValue());
                        enhancedContent.append("\n\n");
                    }
                    log.info("成功获取{}个元器件的资料", componentInfo.size());
                }
            }

            // 5. RAG知识库增强（注入相关电路原理知识）
            String ragKnowledge = tryEnhanceWithKnowledgeBase(enhancedContent.toString());
            if (ragKnowledge != null && !ragKnowledge.isBlank()) {
                enhancedContent.append("【知识库相关电路原理】\n");
                enhancedContent.append(ragKnowledge);
                enhancedContent.append("\n\n");
                log.info("RAG知识库增强成功，新增内容长度: {}", ragKnowledge.length());
            }

            String result = enhancedContent.toString();

            // 6. 控制总长度
            int maxChars = 12000;  // 增加到12000以容纳RAG内容
            if (result.length() > maxChars) {
                result = result.substring(0, maxChars) + "\n...(内容过长已截断)";
            }

            log.info("增强PDF分析完成，最终内容长度: {}", result.length());
            return result;

        } catch (Exception e) {
            log.error("增强PDF分析失败", e);
            return null;
        }
    }

    /**
     * 从文档内容中智能提取元器件名称
     * @param content 文档内容
     * @return 元器件名称列表
     */
    private List<String> extractComponentNames(String content) {

        // 常见元器件模式匹配
        String[] patterns = {
            // 集成电路芯片（如555、LM358、74HC04）
            "\\b(\\d{3,4}[A-Z]{0,2})\\b",
            "\\b([A-Z]{2,3}\\d{3,4}[A-Z]?)\\b",
            // 带封装的芯片（如NE555、CD4017）
            "\\b([A-Z]{2}\\d{3,4})\\b",
            // 三极管、场效应管（如2N3904、IRF540）
            "\\b(\\d[A-Z]\\d{4})\\b",
            "\\b([A-Z]{3}\\d{3,4})\\b"
        };

        java.util.Set<String> componentSet = new java.util.HashSet<>();

        for (String pattern : patterns) {
            java.util.regex.Pattern p = java.util.regex.Pattern.compile(pattern);
            java.util.regex.Matcher m = p.matcher(content);
            while (m.find()) {
                String component = m.group(1);
                // 过滤掉一些误识别（如纯数字、时间等）
                if (isLikelyComponent(component)) {
                    componentSet.add(component);
                }
            }
        }

        List<String> components = new java.util.ArrayList<>(componentSet);

        // 限制数量，避免搜索过多
        if (components.size() > 5) {
            components = components.subList(0, 5);
        }

        return components;
    }

    /**
     * 判断字符串是否可能是元器件型号
     */
    private boolean isLikelyComponent(String text) {
        // 排除纯数字
        if (text.matches("^\\d+$")) {
            return false;
        }
        // 排除常见误识别词
        String[] excludes = {"AND", "NOT", "FOR", "THE", "ALL", "OUT", "VCC", "GND"};
        for (String exclude : excludes) {
            if (text.equalsIgnoreCase(exclude)) {
                return false;
            }
        }
        // 必须包含数字
        return text.matches(".*\\d.*");
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
                return text.trim();
            }
        } catch (Exception e) {
            log.warn("从URL抽取PDF正文失败，url={}，error={}", url, e.toString());
            return null;
        }
    }

    /**
     * 从知识库检索相关电路原理知识
     * @param pdfContent PDF分析内容
     * @return RAG增强的知识内容，失败返回null
     */
    private String tryEnhanceWithKnowledgeBase(String pdfContent) {
        try {
            // 从PDF内容中提取关键电路术语作为查询
            String query = extractCircuitQuery(pdfContent);
            if (query == null || query.isBlank()) {
                log.info("无法从PDF内容中提取电路查询关键词，跳过RAG增强");
                return null;
            }

            // 获取可访问的知识库（使用公开知识库）
            // 注意：PDF工作流通常不绑定特定班级，使用全局公开知识库
            List<String> knowledgeBaseIds = getAccessiblePublicKnowledgeBases();

            if (knowledgeBaseIds.isEmpty()) {
                log.info("没有可访问的知识库，跳过RAG增强");
                return null;
            }

            log.info("使用RAG检索知识库电路原理: 知识库数量={}, 查询关键词={}", knowledgeBaseIds.size(), query);

            // 调用RAG服务检索相关电路知识
            var ragResponse = pythonRagService.ragQuery(
                query,
                knowledgeBaseIds,
                null,  // classId - PDF工作流可能没有班级上下文
                0L,    // userId - 使用系统用户
                3      // Top-K: 返回3个最相关的电路图
            );

            if (ragResponse == null) {
                log.info("RAG服务返回null，跳过知识库增强");
                return null;
            }

            // 解析Python服务响应
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> results = (List<Map<String, Object>>) ragResponse.get("results");

            if (results == null || results.isEmpty()) {
                log.info("RAG检索无结果");
                return null;
            }

            // 格式化检索结果
            StringBuilder ragContent = new StringBuilder();
            int index = 1;
            for (Map<String, Object> result : results) {
                String caption = (String) result.get("caption");
                Object pageNum = result.get("page_number");
                Object score = result.get("score");

                if (caption != null && !caption.isBlank()) {
                    ragContent.append(String.format(
                        "【相关电路%d】(相似度: %.2f, 页码: %s)\\n%s\\n\\n",
                        index++,
                        score != null ? ((Number) score).doubleValue() : 0.0,
                        pageNum != null ? pageNum.toString() : "未知",
                        caption
                    ));
                }
            }

            String result = ragContent.toString().trim();
            if (result.isBlank()) {
                return null;
            }

            // 限制RAG内容长度
            int maxRagChars = 2000;
            if (result.length() > maxRagChars) {
                result = result.substring(0, maxRagChars) + "\\n...(相关电路知识过长已截断)";
            }

            log.info("RAG知识库增强成功: 检索到{}个相关电路, 内容长度={}", results.size(), result.length());
            return result;

        } catch (Exception e) {
            log.warn("RAG知识库增强失败: {}", e.getMessage());
            return null;
        }
    }

    /**
     * 从PDF内容中提取电路查询关键词
     */
    private String extractCircuitQuery(String pdfContent) {
        if (pdfContent == null || pdfContent.isBlank()) {
            return null;
        }

        // 提取前500个字符作为查询上下文
        String queryContext = pdfContent.length() > 500
            ? pdfContent.substring(0, 500)
            : pdfContent;

        // 移除多余空白字符
        queryContext = queryContext.replaceAll("\\s+", " ").trim();

        return queryContext;
    }

    /**
     * 获取可访问的公开知识库列表
     */
    private List<String> getAccessiblePublicKnowledgeBases() {
        // 这里可以从KnowledgeBaseMapper查询所有公开发布的知识库
        // 为简化实现，先返回空列表（表示使用所有可用知识库）
        // TODO: 集成KnowledgeBaseMapper查询逻辑
        return new java.util.ArrayList<>();
    }

    @Override
    public void createOtherNodesOrUpdateNodeData(WorkContext workContext) throws JsonProcessingException {
        RStream<String, Object> redisStream = workContext.getRedisStream();
        AiTaskMessage aiTaskMessage = workContext.getAiTaskMessage();
        Node streamNode = workContext.getStreamNode();
        Response<AiMessage> streamResponse = workContext.getStreamResponse();

        // 获取父节点ID（查询节点ID）
        Long parentId = streamNode.getParentId();

        // 解析分析点结果
        String responseText = streamResponse.content().text();

        try {
            // 解析回答角度（分析点）
            processPdfCircuitAnalysisPoints(workContext, responseText, parentId);
        } catch (Exception e) {
            log.error("解析PDF电路实验任务分析点失败: {}", responseText, e);
        }

        // 更新进度信息
        Map<String, Object> data = new ConcurrentHashMap<>();
        data.put("progress", "PDF实验任务分析点生成完成");
        redisStream.add(StreamAddArgs.entries(
            "type", AiTaskMessageType.DATA,
            "data", data
        ));

        // 发送结束消息
        redisStream.add(StreamAddArgs.entries(
            "type", AiTaskMessageType.DONE,
            "data", ""
        ));
    }

    /**
     * 处理PDF电路实验任务分析点，创建分析点节点
     */
    private void processPdfCircuitAnalysisPoints(WorkContext workContext, String responseText, Long parentId) throws JsonProcessingException {
        AiTaskMessage aiTaskMessage = workContext.getAiTaskMessage();

        // 使用文本方式解析
        String[] lines = responseText.split("\n");
        String currentTitle = null;
        String currentDescription = null;

        for (String s : lines) {
            String line = s.trim();

            if (line.isEmpty()) {
                // 如果是空行，且已有标题和描述，创建节点
                if (currentTitle != null && currentDescription != null) {
                    createPdfCircuitPointNode(workContext, parentId, aiTaskMessage, currentTitle, currentDescription);
                    currentTitle = null;
                    currentDescription = null;
                }
                continue;
            }

            // 匹配"角度X：[标题]"格式
            if (line.matches("^角度\\d+：.+")) {
                // 如果已有标题和描述，先创建之前的节点
                if (currentTitle != null && currentDescription != null) {
                    createPdfCircuitPointNode(workContext, parentId, aiTaskMessage, currentTitle, currentDescription);
                }

                // 提取标题
                currentTitle = line.substring(line.indexOf("：") + 1).trim();
                currentDescription = null;
            }
            // 如果有标题但没有描述，当前行作为描述
            else if (currentTitle != null && currentDescription == null) {
                currentDescription = line;
            }
        }

        // 处理最后一个角度
        if (currentTitle != null && currentDescription != null) {
            createPdfCircuitPointNode(workContext, parentId, aiTaskMessage, currentTitle, currentDescription);
        }
    }

    /**
     * 创建PDF电路实验任务分析点节点
     */
    private void createPdfCircuitPointNode(WorkContext workContext, Long parentId, AiTaskMessage aiTaskMessage,
                                          String title, String description) throws JsonProcessingException {
        Map<String, Object> pdfCircuitPointNodeData = new ConcurrentHashMap<>();
        pdfCircuitPointNodeData.put("title", title);
        pdfCircuitPointNodeData.put("text", description);
        pdfCircuitPointNodeData.put("subtype", NodeSubType.PDF_CIRCUIT_POINT);

        Node pdfCircuitPointNode = new Node(
            NodeType.PDF_CIRCUIT_POINT,
            objectMapper.writeValueAsString(pdfCircuitPointNodeData),
            objectMapper.writeValueAsString(XYPosition.origin()),
            parentId,
            aiTaskMessage.getUserId(),
            aiTaskMessage.getConvId(),
            true
        );

        try {
            insertAndPublishNoneStreamNode(workContext, pdfCircuitPointNode, pdfCircuitPointNodeData);
        } catch (Exception e) {
            log.error("创建PDF电路实验任务分析点节点失败: {}", e.getMessage(), e);
        }
    }

    @Override
    public void updateStreamNode(WorkContext workContext) throws JsonProcessingException {
        // 由于使用虚拟节点，不需要更新节点内容
        log.info("跳过虚拟流节点更新");
    }
}

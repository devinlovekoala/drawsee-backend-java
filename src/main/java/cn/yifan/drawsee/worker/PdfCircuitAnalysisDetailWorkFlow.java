package cn.yifan.drawsee.worker;

import cn.yifan.drawsee.constant.AiTaskMessageType;
import cn.yifan.drawsee.constant.NodeType;
import cn.yifan.drawsee.mapper.AiTaskMapper;
import cn.yifan.drawsee.mapper.ConversationMapper;
import cn.yifan.drawsee.mapper.NodeMapper;
import cn.yifan.drawsee.mapper.UserMapper;
import cn.yifan.drawsee.pojo.XYPosition;
import cn.yifan.drawsee.pojo.entity.Node;
import cn.yifan.drawsee.pojo.entity.UserDocument;
import cn.yifan.drawsee.pojo.rabbit.AiTaskMessage;
import cn.yifan.drawsee.service.business.UserDocumentService;
import cn.yifan.drawsee.service.base.AiService;
import cn.yifan.drawsee.service.base.MinioService;
import cn.yifan.drawsee.service.base.StreamAiService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.ImageContent;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.StreamingResponseHandler;
import dev.langchain4j.model.output.Response;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RStream;
import org.redisson.api.RedissonClient;
import org.redisson.api.stream.StreamAddArgs;
import org.springframework.stereotype.Service;

import java.util.LinkedList;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * PDF电路分析详情工作流
 * 处理用户上传的PDF实验文档，进行AI分析
 * 
 * @author devin
 * @date 2025-01-28
 */
@Slf4j
@Service
public class PdfCircuitAnalysisDetailWorkFlow extends WorkFlow {

    private final UserDocumentService userDocumentService;
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
            UserDocumentService userDocumentService,
            MinioService minioService
    ) {
        super(userMapper, aiService, streamAiService, redissonClient, nodeMapper, conversationMapper, aiTaskMapper, objectMapper);
        this.userDocumentService = userDocumentService;
        this.minioService = minioService;
    }

    @Override
    public Boolean validateAndInit(WorkContext workContext) {
        workContext.setIsSendDone(false);
        return super.validateAndInit(workContext);
    }

    @Override
    public void createInitNodes(WorkContext workContext) throws JsonProcessingException {
        AiTaskMessage aiTaskMessage = workContext.getAiTaskMessage();
        
        // 从prompt中解析文档UUID
        String documentUuid = aiTaskMessage.getPrompt();
        log.info("开始处理PDF电路分析任务，文档UUID: {}", documentUuid);
        
        // 获取文档信息
        UserDocument document = userDocumentService.getDocumentByUuid(documentUuid, aiTaskMessage.getUserId());
        
        // 创建查询节点
        Map<String, Object> queryNodeData = new ConcurrentHashMap<>();
        queryNodeData.put("title", "实验文档分析");
        queryNodeData.put("text", "请分析以下实验文档并提供详细的电路分析");
        queryNodeData.put("documentId", document.getId());
        queryNodeData.put("documentTitle", document.getTitle());
        queryNodeData.put("documentType", document.getDocumentType());
        
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
        
        // 创建AI回答节点
        Map<String, Object> streamNodeData = new ConcurrentHashMap<>();
        streamNodeData.put("title", "电路分析回答");
        streamNodeData.put("text", "");
        
        Node streamNode = new Node(
            NodeType.ANSWER,
            objectMapper.writeValueAsString(streamNodeData),
            objectMapper.writeValueAsString(XYPosition.origin()),
            queryNode.getId(),
            aiTaskMessage.getUserId(),
            aiTaskMessage.getConvId(),
            true
        );
        
        insertAndPublishStreamNode(workContext, streamNode, streamNodeData);
        
        // 将文档信息存储到workContext中供后续使用
        workContext.putExtraData("document", document);
    }

    @Override
    public void streamChat(WorkContext workContext, StreamingResponseHandler<AiMessage> handler) throws JsonProcessingException {
        AiTaskMessage aiTaskMessage = workContext.getAiTaskMessage();
        LinkedList<ChatMessage> history = workContext.getHistory();
        String model = aiTaskMessage.getModel();
        RStream<String, Object> redisStream = workContext.getRedisStream();
        UserDocument document = (UserDocument) workContext.getExtraData("document");
        // 补充：获取流节点ID，所有进度类DATA事件都指向该节点，便于前端定位
        Long streamNodeId = workContext.getStreamNode() != null ? workContext.getStreamNode().getId() : null;

        try {
            // 模型兜底：如果未选择视觉模型，则切换为默认视觉模型
            if (model == null || !model.toLowerCase().contains("vision")) {
                model = "doubaoVision";
            }

            // 推送开始事件，便于前端显示进度
            {
                java.util.Map<String, Object> start = new java.util.concurrent.ConcurrentHashMap<>();
                start.put("stage", "init");
                start.put("status", "START");
                start.put("documentType", document.getDocumentType());
                if (streamNodeId != null) start.put("nodeId", streamNodeId);
                redisStream.add(StreamAddArgs.entries("type", AiTaskMessageType.DATA, "data", start));
            }

            if ("pdf".equalsIgnoreCase(document.getDocumentType())) {
                // 下载PDF为字节数组
                io.minio.GetObjectResponse response = minioService.getObjectStream(document.getObjectPath());
                byte[] pdfBytes;
                try (java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream()) {
                    byte[] buf = new byte[8192];
                    int len;
                    while ((len = response.read(buf)) != -1) { baos.write(buf, 0, len); }
                    pdfBytes = baos.toByteArray();
                } finally { response.close(); }

                // 1) 复杂度采样关键页（最多10页），再限制用于视觉的图片最多8张
                java.util.List<Integer> topPages = cn.yifan.drawsee.util.PdfUtils.selectTopComplexPages(new java.io.ByteArrayInputStream(pdfBytes), 200, 10);
                topPages.sort(java.util.Comparator.naturalOrder());
                java.util.List<Integer> visionPages = topPages.size() > 8 ? topPages.subList(0, 8) : topPages;
                workContext.putExtraData("visionPages", visionPages);

                // 渲染选中页并上传到 Minio，收集 URL
                java.util.List<java.awt.image.BufferedImage> images = cn.yifan.drawsee.util.PdfUtils.renderPages(
                    new java.io.ByteArrayInputStream(pdfBytes), 220, idx -> visionPages.contains(idx)
                );
                java.util.List<String> imageUrls = new java.util.ArrayList<>();
                int idxCounter = 0;
                for (java.awt.image.BufferedImage img : images) {
                    int pageIndex = visionPages.get(idxCounter);
                    String obj = cn.yifan.drawsee.constant.MinioObjectPath.DOCUMENT_PAGE_PATH + document.getId() + "/p" + (pageIndex + 1) + ".png";
                    minioService.uploadImage(img, obj);
                    imageUrls.add(minioService.getObjectUrl(obj));
                    idxCounter++;
                }

                // 发送开始进度
                java.util.Map<String, Object> startData = new java.util.concurrent.ConcurrentHashMap<>();
                startData.put("stage", "vision");
                startData.put("status", "START");
                startData.put("pages", visionPages);
                startData.put("imageCount", imageUrls.size());
                if (streamNodeId != null) startData.put("nodeId", streamNodeId);
                redisStream.add(StreamAddArgs.entries("type", AiTaskMessageType.DATA, "data", startData));

                // 分批进度
                int batchSize = 4;
                java.util.List<String> batchSummaries = new java.util.ArrayList<>();
                int totalBatches = (imageUrls.size() + batchSize - 1) / batchSize;
                for (int i = 0; i < imageUrls.size(); i += batchSize) {
                    int end = Math.min(i + batchSize, imageUrls.size());
                    java.util.List<String> sub = imageUrls.subList(i, end);
                    int batchNo = (i / batchSize) + 1;
                    java.util.Map<String, Object> batchBegin = new java.util.concurrent.ConcurrentHashMap<>();
                    batchBegin.put("stage", "vision");
                    batchBegin.put("status", "BATCH_START");
                    batchBegin.put("batchNo", batchNo);
                    batchBegin.put("totalBatches", totalBatches);
                    if (streamNodeId != null) batchBegin.put("nodeId", streamNodeId);
                    redisStream.add(StreamAddArgs.entries("type", AiTaskMessageType.DATA, "data", batchBegin));

                    String instruction = "请从这些页面图像中提取电路/波形/表格等关键信息，输出要点（尽量结构化、简洁）。";

                    final java.util.concurrent.CountDownLatch latch = new java.util.concurrent.CountDownLatch(1);
                    final java.util.concurrent.atomic.AtomicReference<String> batchTextRef = new java.util.concurrent.atomic.AtomicReference<>("");
                    final java.util.concurrent.atomic.AtomicReference<String> batchErrRef = new java.util.concurrent.atomic.AtomicReference<>(null);

                    streamAiService.visionChat(
                        new LinkedList<>(history),
                        sub,
                        instruction,
                        model,
                        new StreamingResponseHandler<AiMessage>() {
                            @Override public void onNext(String token) { /* 批次中间不写TEXT，避免混流 */ }
                            @Override public void onError(Throwable error) {
                                batchErrRef.set(error.getMessage());
                                java.util.Map<String, Object> err = new java.util.concurrent.ConcurrentHashMap<>();
                                err.put("stage", "vision"); err.put("status", "BATCH_ERROR"); err.put("batchNo", batchNo); err.put("message", error.getMessage());
                                if (streamNodeId != null) err.put("nodeId", streamNodeId);
                                redisStream.add(StreamAddArgs.entries("type", AiTaskMessageType.DATA, "data", err));
                                latch.countDown();
                            }
                            @Override public void onComplete(dev.langchain4j.model.output.Response<AiMessage> response) {
                                String text = response.content().text();
                                batchTextRef.set(text);
                                batchSummaries.add(text);
                                java.util.Map<String, Object> done = new java.util.concurrent.ConcurrentHashMap<>();
                                done.put("stage", "vision"); done.put("status", "BATCH_DONE"); done.put("batchNo", batchNo); done.put("keyPoints", text);
                                if (streamNodeId != null) done.put("nodeId", streamNodeId);
                                redisStream.add(StreamAddArgs.entries("type", AiTaskMessageType.DATA, "data", done));
                                latch.countDown();
                            }
                        }
                    );

                    try {
                        boolean ok = latch.await(120, java.util.concurrent.TimeUnit.SECONDS);
                        if (!ok && batchErrRef.get() == null) {
                            java.util.Map<String, Object> err = new java.util.concurrent.ConcurrentHashMap<>();
                            err.put("stage", "vision"); err.put("status", "BATCH_ERROR"); err.put("batchNo", batchNo); err.put("message", "vision batch timeout");
                            if (streamNodeId != null) err.put("nodeId", streamNodeId);
                            redisStream.add(StreamAddArgs.entries("type", AiTaskMessageType.DATA, "data", err));
                        }
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                    }
                }

                // 2) 文本抽取
                java.util.List<cn.yifan.drawsee.util.PdfUtils.PageText> pageTexts = cn.yifan.drawsee.util.PdfUtils.extractPageTexts(new java.io.ByteArrayInputStream(pdfBytes));
                StringBuffer textSb = new StringBuffer();
                int maxChars = 4000;
                for (cn.yifan.drawsee.util.PdfUtils.PageText pt : pageTexts) {
                    String seg = "[P" + pt.pageNo + "]\n" + pt.text + "\n\n";
                    if (textSb.length() + seg.length() > maxChars) break;
                    textSb.append(seg);
                }
                String extractedText = textSb.toString();
                workContext.putExtraData("extractedText", extractedText);
                workContext.putExtraData("visionBatchSummaries", batchSummaries);

                // 发送进入总结阶段的进度
                java.util.Map<String, Object> summaryStage = new java.util.concurrent.ConcurrentHashMap<>();
                summaryStage.put("stage", "summary");
                summaryStage.put("status", "START");
                if (streamNodeId != null) summaryStage.put("nodeId", streamNodeId);
                redisStream.add(StreamAddArgs.entries("type", AiTaskMessageType.DATA, "data", summaryStage));

                // 3) 一致性校验与二轮总结（流式）
                String mergedContext = "视觉要点汇总：\n" + String.join("\n\n", batchSummaries) + "\n\n文本抽取片段（部分）：\n" + extractedText + "\n\n请基于两侧信息进行一致性校验，输出结构化结论（器件、连接、测量点、波形、结论、引用页码）。";
                history.add(new UserMessage(mergedContext));
                streamAiService.generalChat(history, "请执行一致性校验与最终总结", model, handler);
            } else {
                // 非PDF按原逻辑兜底
                UserMessage userMessage = createUserMessageWithDocument(document);
                history.add(userMessage);
                streamAiService.generalChat(history, "请分析这个实验文档", model, handler);
            }
        } catch (Exception e) {
            log.error("PDF文档分析失败: ", e);
            // 推送错误事件，避免前端长时间等待
            java.util.Map<String, Object> err = new java.util.concurrent.ConcurrentHashMap<>();
            err.put("stage", "error"); err.put("status", "FAILED"); err.put("message", e.getMessage());
            if (streamNodeId != null) err.put("nodeId", streamNodeId);
            redisStream.add(StreamAddArgs.entries("type", AiTaskMessageType.DATA, "data", err));
            redisStream.add(StreamAddArgs.entries("type", AiTaskMessageType.DONE, "data", ""));
            throw new RuntimeException("PDF文档分析失败: " + e.getMessage());
        }
    }

    /**
     * 创建包含文档的用户消息
     */
    private UserMessage createUserMessageWithDocument(UserDocument document) throws Exception {
        // 获取文档分析提示词 - 暂时使用简单的提示词
        String prompt = "请详细分析以下实验文档，重点关注电路设计、实验步骤、预期结果等方面：";
        
        if ("pdf".equals(document.getDocumentType())) {
            // 对于PDF文档，使用文档URL进行分析
            String documentUrl = minioService.getObjectUrl(document.getObjectPath());
            return UserMessage.from(prompt + "\n\n文档链接: " + documentUrl);
        } else if ("image".equals(document.getDocumentType())) {
            // 对于图片文档，使用视觉分析
            String documentUrl = minioService.getObjectUrl(document.getObjectPath());
            ImageContent imageContent = ImageContent.from(documentUrl);
            return UserMessage.from(prompt, imageContent);
        } else {
            // 其他类型文档暂时使用文本分析
            return UserMessage.from(prompt + "\n\n请分析文档: " + document.getTitle());
        }
    }

    @Override
    public void createOtherNodesOrUpdateNodeData(WorkContext workContext) throws JsonProcessingException {
        RStream<String, Object> redisStream = workContext.getRedisStream();
        Response<AiMessage> streamResponse = workContext.getStreamResponse();
        Long streamNodeId = workContext.getStreamNode() != null ? workContext.getStreamNode().getId() : null;
        
        // 更新进度信息
        Map<String, Object> data = new ConcurrentHashMap<>();
        data.put("progress", "PDF电路分析完成");
        data.put("analysisResult", streamResponse.content().text());
        if (streamNodeId != null) data.put("nodeId", streamNodeId);
        
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

    @Override
    public void updateStreamNode(WorkContext workContext) throws JsonProcessingException {
        // 使用默认的流节点更新逻辑
        super.updateStreamNode(workContext);
    }
}

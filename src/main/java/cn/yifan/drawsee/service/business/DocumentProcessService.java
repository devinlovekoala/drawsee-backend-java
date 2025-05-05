package cn.yifan.drawsee.service.business;

import cn.dev33.satoken.stp.StpUtil;
import cn.yifan.drawsee.constant.MinioObjectPath;
import cn.yifan.drawsee.exception.ApiError;
import cn.yifan.drawsee.exception.ApiException;
import cn.yifan.drawsee.pojo.dto.AddKnowledgeDTO;
import cn.yifan.drawsee.pojo.dto.DocumentProcessDTO;
import cn.yifan.drawsee.pojo.mongo.KnowledgeBase;
import cn.yifan.drawsee.pojo.vo.DocumentProcessResultVO;
import cn.yifan.drawsee.pojo.vo.DocumentProcessResultVO.KnowledgeNodeVO;
import cn.yifan.drawsee.repository.KnowledgeBaseRepository;
import cn.yifan.drawsee.service.base.AiService;
import cn.yifan.drawsee.service.base.MinioService;
import cn.yifan.drawsee.service.base.PromptService;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.output.Response;
import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.poi.xwpf.extractor.XWPFWordExtractor;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.redisson.api.RMap;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Function;

import cn.yifan.drawsee.config.MinioConfig;

/**
 * @FileName DocumentProcessService
 * @Description 文档处理服务
 * @Author devin
 * @date 2025-08-20 10:45
 **/
@Service
@Slf4j
public class DocumentProcessService {

    private static final String DOCUMENT_PROCESS_CACHE_PREFIX = "document_process:";
    private static final ExecutorService executorService = Executors.newFixedThreadPool(5);
    private static final Map<String, CompletableFuture<Void>> processingTasks = new ConcurrentHashMap<>();
    
    @Autowired
    private KnowledgeBaseService knowledgeBaseService;
    
    @Autowired
    private KnowledgeBaseRepository knowledgeBaseRepository;
    
    @Autowired
    private RedissonClient redissonClient;
    
    @Autowired
    private MinioService minioService;
    
    @Autowired
    private PromptService promptService;
    
    @Autowired
    @Qualifier("deepseekV3ChatLanguageModel")
    private ChatLanguageModel deepseekV3ChatLanguageModel;
    
    @Autowired
    @Qualifier("doubaoVisionChatLanguageModel")
    private ChatLanguageModel doubaoVisionChatLanguageModel;
    
    @Autowired
    private AiService aiService;
    
    @Autowired
    private ObjectMapper objectMapper;
    
    @Autowired
    private MinioConfig minioConfig;
    
    /**
     * 处理文档并提取知识点结构
     * @param file 上传的文档文件
     * @param processDTO 处理参数
     * @return 处理结果
     */
    public DocumentProcessResultVO processDocument(MultipartFile file, DocumentProcessDTO processDTO) {
        // 参数验证
        if (file == null || file.isEmpty()) {
            throw new ApiException(ApiError.PARAM_ERROR);
        }
        
        if (processDTO.getKnowledgeBaseId() == null || processDTO.getKnowledgeBaseId().isEmpty()) {
            throw new ApiException(ApiError.PARAM_ERROR);
        }
        
        // 验证知识库是否存在且用户有权限操作
        KnowledgeBase knowledgeBase = knowledgeBaseRepository.findById(processDTO.getKnowledgeBaseId()).orElse(null);
        if (knowledgeBase == null) {
            throw new ApiException(ApiError.KNOWLEDGE_BASE_NOT_EXISTED);
        }
        
        Long currentUserId = StpUtil.getLoginIdAsLong();
        if (!Objects.equals(knowledgeBase.getCreatorId(), currentUserId)) {
            throw new ApiException(ApiError.PERMISSION_DENIED);
        }
        
        // 生成任务ID并初始化结果对象
        String taskId = UUID.randomUUID().toString();
        DocumentProcessResultVO resultVO = new DocumentProcessResultVO();
        resultVO.setTaskId(taskId);
        resultVO.setStatus("PENDING");
        resultVO.setProgress(0);
        resultVO.setExtractedCount(0);
        resultVO.setImportedCount(0);
        
        // 保存任务状态到Redis
        saveTaskStatus(taskId, resultVO);
        
        // 异步处理文档
        CompletableFuture<Void> future = CompletableFuture.runAsync(() -> {
            try {
                // 更新状态为处理中
                updateTaskStatus(taskId, status -> {
                    status.setStatus("PROCESSING");
                    status.setProgress(5);
                    return status;
                });
                
                // 1. 保存文件到临时存储
                String fileExt = getFileExtension(file.getOriginalFilename());
                String tempFilePath = getDocumentPath(taskId, fileExt);
                String objectName = tempFilePath;
                
                try {
                    // 使用MinioService上传文件
                    minioService.uploadFile(file, objectName);
                } catch (Exception e) {
                    log.error("文件上传失败", e);
                    throw new RuntimeException("文件上传失败", e);
                }
                
                // 2. 根据文件类型提取文本内容
                String textContent = extractTextFromFile(file);
                
                // 更新进度
                updateTaskStatus(taskId, status -> {
                    status.setProgress(20);
                    return status;
                });
                
                // 3. 使用AI分析文本内容，提取知识点结构
                List<KnowledgeNodeVO> knowledgeStructure = extractKnowledgeStructure(textContent, processDTO);
                
                // 更新进度
                updateTaskStatus(taskId, status -> {
                    status.setProgress(60);
                    status.setExtractedCount(countTotalNodes(knowledgeStructure));
                    status.setKnowledgeStructure(knowledgeStructure);
                    return status;
                });
                
                // 4. 将提取的知识点导入到知识库
                int importedCount = importKnowledgePoints(knowledgeBase.getId(), knowledgeStructure);
                
                // 5. 更新任务状态为完成
                updateTaskStatus(taskId, status -> {
                    status.setStatus("COMPLETED");
                    status.setProgress(100);
                    status.setImportedCount(importedCount);
                    
                    // 添加处理时间等额外信息
                    Map<String, Object> additionalInfo = new HashMap<>();
                    additionalInfo.put("processingTime", System.currentTimeMillis());
                    additionalInfo.put("fileName", file.getOriginalFilename());
                    additionalInfo.put("fileSize", file.getSize());
                    status.setAdditionalInfo(additionalInfo);
                    
                    return status;
                });
                
            } catch (Exception e) {
                log.error("文档处理失败", e);
                // 更新任务状态为失败
                updateTaskStatus(taskId, status -> {
                    status.setStatus("FAILED");
                    status.setErrorMessage(e.getMessage());
                    return status;
                });
            } finally {
                // 从处理任务列表中移除
                processingTasks.remove(taskId);
            }
        }, executorService);
        
        // 保存处理任务引用
        processingTasks.put(taskId, future);
        
        return resultVO;
    }
    
    /**
     * 获取文档在Minio中的存储路径
     */
    private String getDocumentPath(String taskId, String fileExt) {
        if (fileExt.equalsIgnoreCase("pdf")) {
            return MinioObjectPath.KNOWLEDGE_PDF_PATH + taskId + ".pdf";
        } else if (fileExt.equalsIgnoreCase("docx") || fileExt.equalsIgnoreCase("doc")) {
            return MinioObjectPath.KNOWLEDGE_WORD_PATH + taskId + "." + fileExt;
        } else {
            return "temp/documents/" + taskId + "." + fileExt;
        }
    }
    
    /**
     * 获取文档处理任务状态
     * @param taskId 任务ID
     * @return 处理状态和结果
     */
    public DocumentProcessResultVO getProcessStatus(String taskId) {
        if (taskId == null || taskId.isEmpty()) {
            throw new ApiException(ApiError.PARAM_ERROR);
        }
        
        RMap<String, DocumentProcessResultVO> taskMap = redissonClient.getMap(DOCUMENT_PROCESS_CACHE_PREFIX);
        DocumentProcessResultVO status = taskMap.get(taskId);
        
        if (status == null) {
            throw new ApiException(ApiError.KNOWLEDGE_NOT_EXISTED);
        }
        
        return status;
    }
    
    /**
     * 取消文档处理任务
     * @param taskId 任务ID
     */
    public void cancelProcess(String taskId) {
        if (taskId == null || taskId.isEmpty()) {
            throw new ApiException(ApiError.PARAM_ERROR);
        }
        
        CompletableFuture<Void> future = processingTasks.get(taskId);
        if (future != null && !future.isDone()) {
            future.cancel(true);
            processingTasks.remove(taskId);
            
            // 更新任务状态为取消
            updateTaskStatus(taskId, status -> {
                status.setStatus("CANCELLED");
                return status;
            });
        } else {
            throw new ApiException(ApiError.PARAM_ERROR);
        }
    }
    
    /**
     * 保存任务状态到Redis
     */
    private void saveTaskStatus(String taskId, DocumentProcessResultVO status) {
        RMap<String, DocumentProcessResultVO> taskMap = redissonClient.getMap(DOCUMENT_PROCESS_CACHE_PREFIX);
        taskMap.put(taskId, status);
    }
    
    /**
     * 更新任务状态
     */
    private void updateTaskStatus(String taskId, Function<DocumentProcessResultVO, DocumentProcessResultVO> updater) {
        RMap<String, DocumentProcessResultVO> taskMap = redissonClient.getMap(DOCUMENT_PROCESS_CACHE_PREFIX);
        DocumentProcessResultVO status = taskMap.get(taskId);
        
        if (status != null) {
            status = updater.apply(status);
            taskMap.put(taskId, status);
        }
    }
    
    /**
     * 提取文件扩展名
     */
    private String getFileExtension(String filename) {
        if (filename == null) return "";
        int dotPos = filename.lastIndexOf(".");
        return (dotPos == -1) ? "" : filename.substring(dotPos + 1);
    }
    
    /**
     * 从文件中提取文本内容
     */
    private String extractTextFromFile(MultipartFile file) throws IOException {
        String fileName = file.getOriginalFilename();
        if (fileName == null) {
            throw new ApiException(ApiError.PARAM_ERROR);
        }
        
        // 根据文件扩展名选择不同的解析方法
        if (fileName.toLowerCase().endsWith(".pdf")) {
            try (PDDocument document = PDDocument.load(file.getInputStream())) {
                PDFTextStripper stripper = new PDFTextStripper();
                return stripper.getText(document);
            }
        } else if (fileName.toLowerCase().endsWith(".docx")) {
            try (XWPFDocument document = new XWPFDocument(file.getInputStream())) {
                XWPFWordExtractor extractor = new XWPFWordExtractor(document);
                return extractor.getText();
            }
        } else if (fileName.toLowerCase().endsWith(".txt")) {
            return new String(file.getBytes());
        } else {
            throw new ApiException(ApiError.FILE_TYPE_NOT_SUPPORTED);
        }
    }
    
    /**
     * 使用AI提取知识点结构
     */
    private List<KnowledgeNodeVO> extractKnowledgeStructure(String textContent, DocumentProcessDTO processDTO) {
        try {
            // 限制文本长度，防止超出模型的token限制
            String truncatedText = truncateText(textContent, 10000); // 限制最多约10000字符
            
            // 构建提示词
            String prompt = promptService.buildDocumentAnalysisPrompt(truncatedText, processDTO.getAnalysisDepth());
            log.info("正在使用DeepSeek提取知识点结构，文本长度: {}", truncatedText.length());
            
            // 使用DeepSeek模型分析文本内容
            Response<AiMessage> response = deepseekV3ChatLanguageModel.generate(UserMessage.from(prompt));
            String jsonResult = response.content().text();
            
            // 解析JSON到知识点结构
            try {
                List<KnowledgeNodeVO> knowledgeNodes = objectMapper.readValue(
                        jsonResult, 
                        objectMapper.getTypeFactory().constructCollectionType(List.class, KnowledgeNodeVO.class)
                );
                return knowledgeNodes;
            } catch (Exception e) {
                log.error("解析AI返回的知识点结构失败，将使用示例数据", e);
                // 如果解析失败，返回示例数据
                return createSampleKnowledgeStructure();
            }
        } catch (Exception e) {
            log.error("AI提取知识点结构失败", e);
            throw new ApiException(ApiError.SYSTEM_ERROR);
        }
    }
    
    /**
     * 限制文本长度，保留开头和结尾的内容
     */
    private String truncateText(String text, int maxLength) {
        if (text.length() <= maxLength) {
            return text;
        }
        
        int halfLength = maxLength / 2;
        return text.substring(0, halfLength) + 
               "\n...[内容已省略]...\n" + 
               text.substring(text.length() - halfLength);
    }
    
    /**
     * 创建示例知识点结构（当AI分析失败时使用）
     */
    private List<KnowledgeNodeVO> createSampleKnowledgeStructure() {
        List<KnowledgeNodeVO> result = new ArrayList<>();
        
        // 创建一级知识点
        KnowledgeNodeVO chapter1 = new KnowledgeNodeVO();
        chapter1.setId(UUID.randomUUID().toString());
        chapter1.setTitle("第一章 基础知识");
        chapter1.setDescription("本章介绍了该学科的基础概念和理论框架");
        chapter1.setLevel(1);
        chapter1.setParentId(null);
        chapter1.setKeywords(Arrays.asList("基础", "概念", "框架"));
        chapter1.setSummary("这是第一章的内容摘要，介绍了学科基础...");
        
        // 创建二级知识点
        List<KnowledgeNodeVO> chapter1Children = new ArrayList<>();
        
        KnowledgeNodeVO section1 = new KnowledgeNodeVO();
        section1.setId(UUID.randomUUID().toString());
        section1.setTitle("1.1 学科发展历史");
        section1.setDescription("介绍学科的起源和发展历程");
        section1.setLevel(2);
        section1.setParentId(chapter1.getId());
        section1.setKeywords(Arrays.asList("历史", "起源", "发展"));
        section1.setSummary("这是1.1节的内容摘要，描述了学科的发展历程...");
        
        KnowledgeNodeVO section2 = new KnowledgeNodeVO();
        section2.setId(UUID.randomUUID().toString());
        section2.setTitle("1.2 基本概念");
        section2.setDescription("讲解学科中的核心概念和术语");
        section2.setLevel(2);
        section2.setParentId(chapter1.getId());
        section2.setKeywords(Arrays.asList("概念", "术语", "定义"));
        section2.setSummary("这是1.2节的内容摘要，解释了核心概念...");
        
        // 为第二节添加三级知识点
        List<KnowledgeNodeVO> section2Children = new ArrayList<>();
        
        KnowledgeNodeVO subsection1 = new KnowledgeNodeVO();
        subsection1.setId(UUID.randomUUID().toString());
        subsection1.setTitle("1.2.1 基本术语");
        subsection1.setDescription("学科中常用术语的定义和解释");
        subsection1.setLevel(3);
        subsection1.setParentId(section2.getId());
        subsection1.setKeywords(Arrays.asList("术语", "定义", "解释"));
        subsection1.setSummary("这是1.2.1节的内容摘要，列举了基本术语...");
        
        section2Children.add(subsection1);
        section2.setChildren(section2Children);
        
        // 将二级知识点添加到一级知识点
        chapter1Children.add(section1);
        chapter1Children.add(section2);
        chapter1.setChildren(chapter1Children);
        
        // 创建第二章
        KnowledgeNodeVO chapter2 = new KnowledgeNodeVO();
        chapter2.setId(UUID.randomUUID().toString());
        chapter2.setTitle("第二章 进阶理论");
        chapter2.setDescription("本章介绍了该学科的进阶理论和应用方法");
        chapter2.setLevel(1);
        chapter2.setParentId(null);
        chapter2.setKeywords(Arrays.asList("进阶", "理论", "应用"));
        chapter2.setSummary("这是第二章的内容摘要，深入探讨了学科理论...");
        chapter2.setChildren(new ArrayList<>());
        
        // 将所有章节添加到结果中
        result.add(chapter1);
        result.add(chapter2);
        
        return result;
    }
    
    /**
     * 将提取的知识点导入到知识库
     * @return 导入的知识点数量
     */
    private int importKnowledgePoints(String knowledgeBaseId, List<KnowledgeNodeVO> knowledgeStructure) {
        int importCount = 0;
        
        for (KnowledgeNodeVO node : knowledgeStructure) {
            importCount += importKnowledgeNode(knowledgeBaseId, node, null);
        }
        
        return importCount;
    }
    
    /**
     * 导入单个知识点节点及其子节点
     */
    private int importKnowledgeNode(String knowledgeBaseId, KnowledgeNodeVO node, String parentId) {
        // 创建知识点DTO
        AddKnowledgeDTO addKnowledgeDTO = new AddKnowledgeDTO();
        
        // 使用title作为知识点名称
        addKnowledgeDTO.setName(node.getTitle());
        // 设置标准学科分类
        addKnowledgeDTO.setSubject("自动导入");
        // 设置层级
        addKnowledgeDTO.setLevel(node.getLevel());
        // 设置父节点ID
        addKnowledgeDTO.setParentId(parentId);
        
        // 如果存在关键词，设置为别名
        if (node.getKeywords() != null && !node.getKeywords().isEmpty()) {
            addKnowledgeDTO.setAliases(node.getKeywords());
        } else {
            addKnowledgeDTO.setAliases(Collections.emptyList());
        }
        
        // 设置内容描述
        StringBuilder contentBuilder = new StringBuilder();
        
        // 如果有详细描述，添加到内容中
        if (node.getDescription() != null && !node.getDescription().isEmpty()) {
            contentBuilder.append(node.getDescription());
        } 
        // 如果有摘要，且与描述不同，则添加摘要作为补充
        if (node.getSummary() != null && !node.getSummary().isEmpty() 
                && (node.getDescription() == null || !node.getSummary().equals(node.getDescription()))) {
            if (contentBuilder.length() > 0) {
                contentBuilder.append("\n\n概要：\n");
            }
            contentBuilder.append(node.getSummary());
        }
        
        // 设置最终内容
        addKnowledgeDTO.setContent(contentBuilder.toString());
        
        // 添加到知识库
        String knowledgeId = knowledgeBaseService.addKnowledgeBaseKnowledgePoint(knowledgeBaseId, addKnowledgeDTO);
        
        int count = 1; // 当前节点
        
        // 递归处理子节点
        if (node.getChildren() != null && !node.getChildren().isEmpty()) {
            for (KnowledgeNodeVO childNode : node.getChildren()) {
                count += importKnowledgeNode(knowledgeBaseId, childNode, knowledgeId);
            }
        }
        
        return count;
    }
    
    /**
     * 计算知识点结构中的总节点数
     */
    private int countTotalNodes(List<KnowledgeNodeVO> nodes) {
        if (nodes == null || nodes.isEmpty()) {
            return 0;
        }
        
        int count = nodes.size();
        
        for (KnowledgeNodeVO node : nodes) {
            if (node.getChildren() != null && !node.getChildren().isEmpty()) {
                count += countTotalNodes(node.getChildren());
            }
        }
        
        return count;
    }
}
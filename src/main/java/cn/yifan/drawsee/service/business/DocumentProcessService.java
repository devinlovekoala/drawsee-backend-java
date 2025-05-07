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
import org.redisson.api.RMap;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Function;

import cn.yifan.drawsee.config.MinioConfig;

/**
 * @FileName DocumentProcessService
 * @Description 文档处理服务（简化版）- 专注处理教材目录页PDF文件
 * @Author devin
 * @date 2025-08-20 10:45
 * @update 2025-09-01 13:00 简化为仅处理教材目录页PDF
 **/
@Service
@Slf4j
public class DocumentProcessService {

    private static final String DOCUMENT_PROCESS_CACHE_PREFIX = "document_process:";
    private static final ExecutorService executorService = Executors.newFixedThreadPool(3);
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
    private ObjectMapper objectMapper;
    
    /**
     * 处理教材目录页PDF文档并提取知识点结构
     * @param file 上传的目录页PDF文件
     * @param processDTO 处理参数
     * @return 处理结果
     */
    public DocumentProcessResultVO processDocument(MultipartFile file, DocumentProcessDTO processDTO) {
        // 参数验证
        if (file == null || file.isEmpty()) {
            throw new ApiException(ApiError.PARAM_ERROR);
        }
        
        // 验证文件类型必须是PDF
        String fileName = file.getOriginalFilename();
        if (fileName == null || !fileName.toLowerCase().endsWith(".pdf")) {
            throw new ApiException(ApiError.FILE_TYPE_NOT_SUPPORTED);
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
                    status.setProgress(10);
                    return status;
                });
                
                // 1. 保存文件到临时存储
                String tempFilePath = MinioObjectPath.KNOWLEDGE_PDF_PATH + taskId + ".pdf";
                
                try {
                    // 使用MinioService上传文件
                    minioService.uploadFile(file, tempFilePath);
                } catch (Exception e) {
                    log.error("文件上传失败", e);
                    throw new RuntimeException("文件上传失败", e);
                }
                
                // 2. 提取PDF目录页文本内容
                String textContent = extractPdfCatalogContent(file, processDTO);
                
                // 更新进度
                updateTaskStatus(taskId, status -> {
                    status.setProgress(30);
                    return status;
                });
                
                // 3. 使用AI分析目录文本内容，提取知识点结构
                List<KnowledgeNodeVO> knowledgeStructure = extractKnowledgeStructureFromCatalog(textContent, processDTO);
                
                // 更新进度
                updateTaskStatus(taskId, status -> {
                    status.setProgress(70);
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
                log.error("目录页处理失败", e);
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
     * 从PDF目录页文件中提取文本内容
     * 专门优化用于处理教材目录页
     * 支持根据pageRange参数提取特定页面范围的内容
     */
    private String extractPdfCatalogContent(MultipartFile file, DocumentProcessDTO processDTO) throws IOException {
        try (PDDocument document = PDDocument.load(file.getInputStream())) {
            PDFTextStripper stripper = new PDFTextStripper();
            
            // 目录页通常在前几页，我们可以限制提取范围
            // 默认提取前5页
            int startPage = 1;
            int endPage = Math.min(document.getNumberOfPages(), 5);
            
            // 根据请求中的pageRange参数设置页面范围
            if (processDTO != null && processDTO.getPageRange() != null) {
                int requestedStartPage = processDTO.getPageRange().getStart();
                int requestedEndPage = processDTO.getPageRange().getEnd();
                
                // 验证页面范围的有效性
                if (requestedStartPage > 0 && requestedEndPage >= requestedStartPage 
                    && requestedEndPage <= document.getNumberOfPages()) {
                    startPage = requestedStartPage;
                    endPage = requestedEndPage;
                    log.info("使用指定的页面范围: {} - {}，文档总页数: {}", 
                             startPage, endPage, document.getNumberOfPages());
                } else {
                    log.warn("请求的页面范围无效: {} - {}，文档总页数: {}，将使用默认范围: 1-5", 
                             requestedStartPage, requestedEndPage, document.getNumberOfPages());
                }
            } else {
                log.info("未指定页面范围，使用默认范围: 1-5");
            }
            
            stripper.setStartPage(startPage);
            stripper.setEndPage(endPage);
            
            String text = stripper.getText(document);
            log.info("提取的文本长度: {} 字符，来自页面 {}-{}", text.length(), startPage, endPage);
            
            return text;
        }
    }
    
    /**
     * 使用AI从目录文本提取知识点结构
     * 这个方法针对目录页进行优化
     */
    private List<KnowledgeNodeVO> extractKnowledgeStructureFromCatalog(String catalogText, DocumentProcessDTO processDTO) {
        try {
            // 构建目录页分析提示词，注入教材类型、年级等信息以提高分析准确性
            StringBuilder promptBuilder = new StringBuilder();
            promptBuilder.append("教材类型：").append(processDTO.getTextbookType() != null ? processDTO.getTextbookType() : "未知");
            promptBuilder.append("\n年级：").append(processDTO.getGrade() != null ? processDTO.getGrade() : "未知");
            promptBuilder.append("\n学期：").append(processDTO.getSemester() != null ? processDTO.getSemester() : "未知");
            promptBuilder.append("\n\n目录内容：\n").append(catalogText);
            
            String prompt = promptService.buildDocumentAnalysisPrompt(promptBuilder.toString(), processDTO.getAnalysisDepth());
            log.info("正在分析教材目录页，教材类型：{}，年级：{}，文本长度: {}", 
                    processDTO.getTextbookType(), 
                    processDTO.getGrade(), 
                    catalogText.length());
            
            // 使用DeepSeek模型分析目录内容
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
                log.error("解析AI返回的知识点结构失败", e);
                // 如果解析失败，返回空数据
                return new ArrayList<>();
            }
        } catch (Exception e) {
            log.error("AI提取知识点结构失败", e);
            throw new ApiException(ApiError.SYSTEM_ERROR);
        }
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
        addKnowledgeDTO.setSubject("教材目录");
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
        
        // 如果内容为空，则使用标题作为内容
        if (contentBuilder.length() == 0) {
            contentBuilder.append("来自教材目录: ").append(node.getTitle());
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
package cn.yifan.drawsee.controller;

import cn.dev33.satoken.annotation.SaCheckLogin;
import cn.yifan.drawsee.constant.UserRole;
import cn.yifan.drawsee.pojo.Result;
import cn.yifan.drawsee.pojo.dto.CreateKnowledgeBaseDTO;
import cn.yifan.drawsee.pojo.dto.JoinKnowledgeBaseDTO;
import cn.yifan.drawsee.pojo.dto.UploadResourceDTO;
import cn.yifan.drawsee.pojo.vo.KnowledgeBaseVO;
import cn.yifan.drawsee.pojo.vo.ResourceCountVO;
import cn.yifan.drawsee.service.business.KnowledgeBaseService;
import cn.yifan.drawsee.service.business.UserRoleService;
import cn.yifan.drawsee.service.business.DocumentProcessService;
import cn.yifan.drawsee.pojo.dto.DocumentProcessDTO;
import cn.yifan.drawsee.pojo.vo.DocumentProcessResultVO;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.validation.annotation.Validated;

import java.util.List;
import java.util.Map;
import java.util.HashMap;

/**
 * @FileName KnowledgeBaseController
 * @Description 知识库控制器类，作为通用入口
 * @Author devin
 * @date 2025-03-28 11:10
 * @update 2025-08-15 17:30 重构为通用入口，根据用户角色引导到专用控制器
 **/

@RestController
@SaCheckLogin
public class KnowledgeBaseController {
    
    private static final Logger logger = LoggerFactory.getLogger(KnowledgeBaseController.class);

    @Autowired
    private KnowledgeBaseService knowledgeBaseService;
    
    @Autowired
    private UserRoleService userRoleService;

    @Autowired
    private DocumentProcessService documentProcessService;

    /**
     * 创建知识库 - 根据用户角色引导到对应控制器
     * 管理员: /admin/knowledge/base
     * 教师: /teacher/knowledge/base
     */
    @PostMapping("/knowledge/base")
    public Result<String> createKnowledgeBase(@RequestBody @Valid CreateKnowledgeBaseDTO createKnowledgeBaseDTO) {
        String id;
        String role = userRoleService.getCurrentUserRole();
        
        if (UserRole.ADMIN.equals(role)) {
            id = knowledgeBaseService.createKnowledgeBaseByAdmin(createKnowledgeBaseDTO, true);
        } else if (UserRole.TEACHER.equals(role)) {
            id = knowledgeBaseService.createKnowledgeBase(createKnowledgeBaseDTO);
        } else {
            // 普通用户无权创建知识库
            throw new cn.yifan.drawsee.exception.ApiException(cn.yifan.drawsee.exception.ApiError.PERMISSION_DENIED);
        }
        
        return Result.success(id);
    }

    /**
     * 加入知识库 - 通用方法，所有登录用户都可以使用
     */
    @PostMapping("/knowledge/base/join")
    public Result<String> joinKnowledgeBase(@RequestBody @Valid JoinKnowledgeBaseDTO joinKnowledgeBaseDTO) {
        String id = knowledgeBaseService.joinKnowledgeBase(joinKnowledgeBaseDTO);
        return Result.success(id);
    }

    /**
     * 获取我创建的知识库列表 - 通用方法
     */
    @GetMapping("/knowledge/base/created")
    public Result<List<KnowledgeBaseVO>> getMyCreatedKnowledgeBases() {
        List<KnowledgeBaseVO> result = knowledgeBaseService.getMyCreatedKnowledgeBases();
        return Result.success(result);
    }

    /**
     * 获取我加入的知识库列表 - 通用方法
     */
    @GetMapping("/knowledge/base/joined")
    public Result<List<KnowledgeBaseVO>> getMyJoinedKnowledgeBases() {
        List<KnowledgeBaseVO> result = knowledgeBaseService.getMyJoinedKnowledgeBases();
        return Result.success(result);
    }
    
    /**
     * 获取知识库详情 - 通用方法
     */
    @GetMapping("/knowledge/base/{id}")
    public Result<KnowledgeBaseVO> getKnowledgeBaseDetail(@PathVariable("id") String id) {
        KnowledgeBaseVO result = knowledgeBaseService.getKnowledgeBaseDetail(id);
        return Result.success(result);
    }
    
    /**
     * 上传资源 - 通用方法，根据用户角色校验权限
     */
    @PostMapping(value = "/knowledge/base/resource/upload", consumes = "multipart/form-data")
    public Result<String> uploadResource(
            @RequestParam("file") MultipartFile file,
            @RequestParam("knowledgeId") String knowledgeId,
            @RequestParam("resourceType") String resourceType) {
        
        if (file.isEmpty()) {
            throw new IllegalArgumentException("上传的文件不能为空");
        }
        
        logger.info("处理文件上传：knowledgeId={}, resourceType={}, fileName={}, fileSize={}", 
            knowledgeId, resourceType, file.getOriginalFilename(), file.getSize());
        
        UploadResourceDTO uploadResourceDTO = new UploadResourceDTO(knowledgeId, resourceType);
        String resourceUrl = knowledgeBaseService.uploadResource(file, uploadResourceDTO);
        return Result.success(resourceUrl);
    }
    
    /**
     * 添加B站资源 - 通用方法，根据用户角色校验权限
     */
    @PostMapping(value = "/knowledge/base/resource/bilibili")
    public Result<String> addBilibiliResource(@RequestBody Map<String, String> bilibiliResource) {
        String knowledgeId = bilibiliResource.get("knowledgeId");
        String url = bilibiliResource.get("url");
        String resourceType = bilibiliResource.get("resourceType");
        
        if (knowledgeId == null || url == null || !resourceType.equals("bilibili")) {
            throw new IllegalArgumentException("无效的B站资源请求");
        }
        
        logger.info("处理B站资源添加：knowledgeId={}, url={}", knowledgeId, url);
        
        UploadResourceDTO uploadResourceDTO = new UploadResourceDTO(knowledgeId, resourceType);
        String resourceUrl = knowledgeBaseService.addBilibiliResource(url, uploadResourceDTO);
        return Result.success(resourceUrl);
    }
    
    /**
     * 获取知识点资源统计信息 - 通用方法
     */
    @GetMapping("/knowledge/resource/count/{knowledgeId}")
    public Result<ResourceCountVO> getKnowledgeResourceCount(@PathVariable("knowledgeId") String knowledgeId) {
        logger.info("获取知识点资源统计: knowledgeId={}", knowledgeId);
        ResourceCountVO resourceCount = knowledgeBaseService.getKnowledgeResourceCount(knowledgeId);
        return Result.success(resourceCount);
    }
    
    /**
     * 上传资源
     */
    @PostMapping(value = {"/knowledge-base/resource/upload"}, consumes = "multipart/form-data")
    public Result<String> uploadResourceAlternative(
            @RequestParam("file") MultipartFile file,
            @RequestParam("knowledgeId") String knowledgeId,
            @RequestParam("resourceType") String resourceType) {
            
        return uploadResource(file, knowledgeId, resourceType);
    }
    
    /**
     * 添加B站资源
     */
    @PostMapping(value = {"/knowledge-base/resource/bilibili"})
    public Result<String> addBilibiliResourceAlternative(@RequestBody Map<String, String> bilibiliResource) {
        return addBilibiliResource(bilibiliResource);
    }
    
    /**
     * 获取知识点资源统计信息 - 兼容API路径
     */
    @GetMapping({"/knowledge-base/resource/count/{knowledgeId}"})
    public Result<ResourceCountVO> getKnowledgeResourceCountAlternative(@PathVariable("knowledgeId") String knowledgeId) {
        return getKnowledgeResourceCount(knowledgeId);
    }

    /**
     * 上传并处理教材目录页，提取知识点结构
     * 特别说明：仅支持PDF格式的教材目录页文件，优化用于分析各学科各年级的教材目录
     * 
     * @param file 上传的目录页PDF文件
     * @param processDTO 处理参数 (@see DocumentProcessDTO)
     * @param knowledgeId 可选参数，知识库ID (兼容旧前端，与knowledgeBaseId二选一即可)
     * @param startPage 可选参数，目录开始页码（默认为1）
     * @param endPage 可选参数，目录结束页码（默认为5）
     * @return 处理结果，包含提取的知识点层级结构
     */
    @PostMapping({"/document/process", "/knowledge-base/document/process", "/api/document/process"})
    public Result<DocumentProcessResultVO> processDocument(
            @RequestParam("file") MultipartFile file,
            @Validated DocumentProcessDTO processDTO,
            @RequestParam(value = "knowledgeId", required = false) String knowledgeId,
            @RequestParam(value = "startPage", required = false) Integer startPage,
            @RequestParam(value = "endPage", required = false) Integer endPage) {
        // 验证文件类型必须是PDF
        String fileName = file.getOriginalFilename();
        if (fileName == null || file.isEmpty() || !fileName.toLowerCase().endsWith(".pdf")) {
            throw new cn.yifan.drawsee.exception.ApiException(cn.yifan.drawsee.exception.ApiError.FILE_TYPE_NOT_SUPPORTED);
        }
        
        // 如果前端传入了knowledgeId但没有设置knowledgeBaseId，则使用knowledgeId作为knowledgeBaseId
        if (knowledgeId != null && !knowledgeId.isEmpty() && 
            (processDTO.getKnowledgeBaseId() == null || processDTO.getKnowledgeBaseId().isEmpty())) {
            processDTO.setKnowledgeBaseId(knowledgeId);
        }
        
        // 确保知识库ID不为空
        if (processDTO.getKnowledgeBaseId() == null || processDTO.getKnowledgeBaseId().isEmpty()) {
            throw new cn.yifan.drawsee.exception.ApiException(
                cn.yifan.drawsee.exception.ApiError.PARAM_ERROR);
        }
        
        // 设置页面范围参数
        if (startPage != null || endPage != null) {
            // 初始化页面范围对象（如果尚未设置）
            if (processDTO.getPageRange() == null) {
                processDTO.setPageRange(new DocumentProcessDTO.PageRange());
            }
            
            // 设置开始页码（如果提供了）
            if (startPage != null && startPage > 0) {
                processDTO.getPageRange().setStart(startPage);
            }
            
            // 设置结束页码（如果提供了）
            if (endPage != null && endPage >= processDTO.getPageRange().getStart()) {
                processDTO.getPageRange().setEnd(endPage);
            }
        }
        
        logger.info("接收到教材目录页处理请求，文件名: {}, 知识库ID: {}, 教材类型: {}, 年级: {}, 学期: {}, 页面范围: {}-{}", 
                fileName, processDTO.getKnowledgeBaseId(), processDTO.getTextbookType(), 
                processDTO.getGrade(), processDTO.getSemester(),
                processDTO.getPageRange() != null ? processDTO.getPageRange().getStart() : 1,
                processDTO.getPageRange() != null ? processDTO.getPageRange().getEnd() : 5);
        
        DocumentProcessResultVO resultVO = documentProcessService.processDocument(file, processDTO);
        return Result.success(resultVO);
    }

    /**
     * 获取文档处理任务状态
     * 
     * @param taskId 任务ID
     * @return 任务状态
     */
    @GetMapping("/document/process/status")
    public Result<DocumentProcessResultVO> getProcessStatus(@RequestParam String taskId) {
        DocumentProcessResultVO status = documentProcessService.getProcessStatus(taskId);
        return Result.success(status);
    }

    /**
     * 取消文档处理任务
     * 
     * @param taskId 任务ID
     * @return 操作结果
     */
    @PostMapping("/document/process/cancel")
    public Result<Void> cancelProcess(@RequestParam String taskId) {
        documentProcessService.cancelProcess(taskId);
        return Result.success(null);
    }

    /**
     * 检测PDF文件的页数
     * 这个API用于支持前端页面范围选择功能
     * 
     * @param file 上传的PDF文件
     * @return 页数信息，包含pageCount字段
     */
    @PostMapping("/document/detect-pages")
    public Result<Map<String, Integer>> detectPdfPages(@RequestParam("file") MultipartFile file) {
        // 验证文件类型必须是PDF
        String fileName = file.getOriginalFilename();
        if (fileName == null || file.isEmpty() || !fileName.toLowerCase().endsWith(".pdf")) {
            throw new cn.yifan.drawsee.exception.ApiException(cn.yifan.drawsee.exception.ApiError.FILE_TYPE_NOT_SUPPORTED);
        }
        
        logger.info("检测PDF页数: {}", fileName);
        
        try {
            // 使用PDFBox加载文档并获取页数
            try (org.apache.pdfbox.pdmodel.PDDocument document = 
                     org.apache.pdfbox.pdmodel.PDDocument.load(file.getInputStream())) {
                
                int pageCount = document.getNumberOfPages();
                
                // 返回页数信息
                Map<String, Integer> result = new HashMap<>();
                result.put("pageCount", pageCount);
                
                return Result.success(result);
            }
        } catch (Exception e) {
            logger.error("检测PDF页数失败", e);
            throw new cn.yifan.drawsee.exception.ApiException(cn.yifan.drawsee.exception.ApiError.SYSTEM_ERROR);
        }
    }
} 
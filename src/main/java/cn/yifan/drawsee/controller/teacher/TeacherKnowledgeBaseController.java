package cn.yifan.drawsee.controller.teacher;

import cn.dev33.satoken.annotation.SaCheckLogin;
import cn.dev33.satoken.annotation.SaCheckRole;
import cn.yifan.drawsee.constant.UserRole;
import cn.yifan.drawsee.pojo.dto.CreateKnowledgeBaseDTO;
import cn.yifan.drawsee.pojo.dto.JoinKnowledgeBaseDTO;
import cn.yifan.drawsee.pojo.dto.UploadResourceDTO;
import cn.yifan.drawsee.pojo.vo.KnowledgeBaseVO;
import cn.yifan.drawsee.pojo.vo.ResourceCountVO;
import cn.yifan.drawsee.pojo.vo.R;
import cn.yifan.drawsee.pojo.vo.rag.RagKnowledgeVO;
import cn.yifan.drawsee.service.business.KnowledgeBaseService;
import cn.yifan.drawsee.service.business.KnowledgeResourceService;
import cn.yifan.drawsee.service.business.RagFlowService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import jakarta.validation.Valid;
import java.util.List;
import java.util.Map;

/**
 * @FileName TeacherKnowledgeBaseController
 * @Description 教师的知识库控制器
 * @Author devin
 * @date 2025-08-15 17:35
 * @update 2025-10-05 14:40 更新资源管理相关API
 **/

@Slf4j
@RestController
@RequestMapping("/api/teacher/knowledge-base")
@SaCheckRole(UserRole.TEACHER)
public class TeacherKnowledgeBaseController {

    @Autowired
    private KnowledgeBaseService knowledgeBaseService;

    @Autowired
    private RagFlowService ragFlowService;
    
    @Autowired
    private KnowledgeResourceService knowledgeResourceService;

    /**
     * 创建知识库
     */
    @SaCheckLogin
    @PostMapping("/create")
    public R<String> createKnowledgeBase(@RequestBody @Valid CreateKnowledgeBaseDTO createKnowledgeBaseDTO) {
        try {
            String knowledgeBaseId = knowledgeBaseService.createKnowledgeBase(createKnowledgeBaseDTO);
            return R.ok(knowledgeBaseId);
        } catch (Exception e) {
            log.error("创建知识库失败", e);
            return R.fail(e.getMessage());
        }
    }

    /**
     * 创建RAG知识库
     * 
     * @param createKnowledgeBaseDTO 知识库DTO
     * @return 知识库ID
     */
    @SaCheckLogin
    @PostMapping("/create-rag")
    public R<String> createRagKnowledgeBase(@RequestBody @Valid CreateKnowledgeBaseDTO createKnowledgeBaseDTO) {
        try {
            // 创建启用RAG的知识库
            String knowledgeBaseId = knowledgeBaseService.createRagEnabledKnowledgeBase(createKnowledgeBaseDTO);
            return R.ok(knowledgeBaseId);
        } catch (Exception e) {
            log.error("创建RAG知识库失败", e);
            return R.fail(e.getMessage());
        }
    }

    /**
     * 更新知识库
     * 
     * @param id 知识库ID
     * @param createKnowledgeBaseDTO 知识库DTO
     * @return 是否成功
     */
    @SaCheckLogin
    @PutMapping("/{id}")
    public R<Boolean> updateKnowledgeBase(
            @PathVariable("id") String id,
            @RequestBody @Valid CreateKnowledgeBaseDTO createKnowledgeBaseDTO) {
        try {
            // 设置ID到DTO中
            createKnowledgeBaseDTO.setId(id);
            // 调用服务层方法进行更新
            knowledgeBaseService.updateKnowledgeBase(createKnowledgeBaseDTO);
            return R.ok(true);
        } catch (Exception e) {
            log.error("更新知识库失败", e);
            return R.fail(e.getMessage());
        }
    }

    /**
     * 删除知识库
     * 
     * @param knowledgeBaseId 知识库ID
     * @return 是否成功
     */
    @SaCheckLogin
    @DeleteMapping("/{knowledgeBaseId}")
    public R<Boolean> deleteKnowledgeBase(@PathVariable String knowledgeBaseId) {
        try {
            knowledgeBaseService.deleteKnowledgeBase(knowledgeBaseId);
            return R.ok(true);
        } catch (Exception e) {
            log.error("删除知识库失败", e);
            return R.fail(e.getMessage());
        }
    }

    /**
     * 获取知识库详情
     * 
     * @param id 知识库ID
     * @return 知识库详情
     */
    @SaCheckLogin
    @GetMapping("/{id}")
    public R<KnowledgeBaseVO> getKnowledgeBaseDetail(@PathVariable String id) {
        try {
            KnowledgeBaseVO knowledgeBase = knowledgeBaseService.getKnowledgeBaseDetail(id);
            return R.ok(knowledgeBase);
        } catch (Exception e) {
            log.error("获取知识库详情失败", e);
            return R.fail(e.getMessage());
        }
    }

    /**
     * 获取当前教师的知识库列表
     * 
     * @return 知识库列表
     */
    @SaCheckLogin
    @GetMapping("/list")
    public R<List<KnowledgeBaseVO>> getTeacherKnowledgeBases() {
        try {
            List<KnowledgeBaseVO> knowledgeBases = knowledgeBaseService.getMyCreatedKnowledgeBases();
            return R.ok(knowledgeBases);
        } catch (Exception e) {
            log.error("获取教师知识库列表失败", e);
            return R.fail(e.getMessage());
        }
    }

    /**
     * 获取当前教师的RAG知识库列表
     * 
     * @return 知识库列表
     */
    @SaCheckLogin
    @GetMapping("/rag/list")
    public R<List<RagKnowledgeVO>> getTeacherRagKnowledgeBases(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size) {
        try {
            // 获取当前教师的RAG知识库列表
            List<RagKnowledgeVO> ragKnowledgeBases = ragFlowService.listKnowledges(page, size).getKnowledges();
            return R.ok(ragKnowledgeBases);
        } catch (Exception e) {
            log.error("获取教师RAG知识库列表失败", e);
            return R.fail(e.getMessage());
        }
    }

    /**
     * 上传文档资源到知识库（PDF/Word文档）
     * 
     * @param file 文件资源
     * @param uploadResourceDTO 上传资源DTO
     * @return 资源ID
     */
    @SaCheckLogin
    @PostMapping(value = "/resource/document", consumes = "multipart/form-data")
    public R<String> uploadDocumentResource(
            @RequestParam("file") MultipartFile file,
            @ModelAttribute UploadResourceDTO uploadResourceDTO) {
        try {
            // 验证文件类型（PDF或Word文档）
            String filename = file.getOriginalFilename();
            if (filename == null || (!filename.toLowerCase().endsWith(".pdf") && 
                !filename.toLowerCase().endsWith(".doc") && 
                !filename.toLowerCase().endsWith(".docx"))) {
                return R.fail("只支持PDF或Word文档格式");
            }
            
            // 使用KnowledgeResourceService上传资源
            String resourceId = knowledgeResourceService.uploadDocumentResource(uploadResourceDTO, file);
            return R.ok(resourceId);
        } catch (Exception e) {
            log.error("上传文档资源失败", e);
            return R.fail(e.getMessage());
        }
    }

    /**
     * 上传视频资源到知识库
     * 
     * @param file 视频文件
     * @param uploadResourceDTO 上传资源DTO
     * @return 资源ID
     */
    @SaCheckLogin
    @PostMapping(value = "/resource/video", consumes = "multipart/form-data")
    public R<String> uploadVideoResource(
            @RequestParam("file") MultipartFile file,
            @ModelAttribute UploadResourceDTO uploadResourceDTO) {
        try {
            // 验证文件类型（视频文件）
            String filename = file.getOriginalFilename();
            if (filename == null || (!filename.toLowerCase().endsWith(".mp4") && 
                !filename.toLowerCase().endsWith(".mov") && 
                !filename.toLowerCase().endsWith(".avi") && 
                !filename.toLowerCase().endsWith(".mkv"))) {
                return R.fail("只支持MP4、MOV、AVI或MKV视频格式");
            }
            
            // 使用KnowledgeResourceService上传视频资源
            String resourceId = knowledgeResourceService.uploadVideoResource(uploadResourceDTO, file);
            return R.ok(resourceId);
        } catch (Exception e) {
            log.error("上传视频资源失败", e);
            return R.fail(e.getMessage());
        }
    }

    /**
     * 添加B站资源到知识库
     * 
     * @param url B站视频链接
     * @param uploadResourceDTO 上传资源DTO
     * @return 资源ID
     */
    @SaCheckLogin
    @PostMapping(value = "/resource/bilibili")
    public R<String> addBilibiliResource(
            @RequestParam("url") String url,
            @ModelAttribute UploadResourceDTO uploadResourceDTO) {
        try {
            // 添加B站资源
            String resourceId = knowledgeBaseService.addBilibiliResource(url, uploadResourceDTO);
            return R.ok(resourceId);
        } catch (Exception e) {
            log.error("添加B站资源失败", e);
            return R.fail(e.getMessage());
        }
    }

    /**
     * 批量上传资源到知识库
     * 
     * @param files 多个文件资源
     * @param knowledgeBaseId 知识库ID
     * @param title 资源标题前缀
     * @param description 资源描述
     * @return 上传成功的资源数量
     */
    @SaCheckLogin
    @PostMapping(value = "/resource/batch-upload", consumes = "multipart/form-data")
    public R<Integer> batchUploadResources(
            @RequestParam("files") MultipartFile[] files,
            @RequestParam("knowledgeBaseId") String knowledgeBaseId,
            @RequestParam("title") String title,
            @RequestParam("description") String description) {
        try {
            int successCount = 0;
            for (int i = 0; i < files.length; i++) {
                MultipartFile file = files[i];
                // 为每个文件创建上传DTO
                UploadResourceDTO dto = new UploadResourceDTO();
                dto.setKnowledgeBaseId(knowledgeBaseId);
                dto.setTitle(title + "-" + (i + 1));
                dto.setDescription(description);
                
                try {
                    // 根据文件类型判断使用哪种上传方法
                    String filename = file.getOriginalFilename();
                    if (filename == null) continue;
                    
                    if (filename.toLowerCase().endsWith(".pdf") || 
                        filename.toLowerCase().endsWith(".doc") || 
                        filename.toLowerCase().endsWith(".docx")) {
                        knowledgeResourceService.uploadDocumentResource(dto, file);
                        successCount++;
                    } else if (filename.toLowerCase().endsWith(".mp4") || 
                               filename.toLowerCase().endsWith(".mov") || 
                               filename.toLowerCase().endsWith(".avi") ||
                               filename.toLowerCase().endsWith(".mkv")) {
                        knowledgeResourceService.uploadVideoResource(dto, file);
                        successCount++;
                    }
                } catch (Exception e) {
                    log.error("批量上传中单个文件失败: {}", file.getOriginalFilename(), e);
                    // 继续处理下一个文件
                }
            }
            
            return R.ok(successCount);
        } catch (Exception e) {
            log.error("批量上传资源失败", e);
            return R.fail(e.getMessage());
        }
    }

    /**
     * 获取知识库资源计数
     * 
     * @param knowledgeBaseId 知识库ID
     * @return 资源计数
     */
    @SaCheckLogin
    @GetMapping("/resource/count/{knowledgeBaseId}")
    public R<ResourceCountVO> getKnowledgeResourceCount(@PathVariable("knowledgeBaseId") String knowledgeBaseId) {
        try {
            // 获取知识库资源计数
            Map<String, Integer> countMap = knowledgeResourceService.getResourceCountByTypes(knowledgeBaseId);
            
            // 构建响应VO
            ResourceCountVO countVO = ResourceCountVO.builder()
                .totalCount(countMap.values().stream().mapToInt(Integer::intValue).sum())
                .pdfCount(countMap.getOrDefault("PDF", 0))
                .wordCount(countMap.getOrDefault("WORD", 0))
                .videoCount(countMap.getOrDefault("VIDEO", 0))
                .bilibiliCount(countMap.getOrDefault("BILIBILI", 0))
                .build();
            
            return R.ok(countVO);
        } catch (Exception e) {
            log.error("获取知识库资源计数失败", e);
            return R.fail(e.getMessage());
        }
    }
    
    /**
     * 删除知识库资源
     * 
     * @param resourceId 资源ID
     * @return 是否删除成功
     */
    @SaCheckLogin
    @DeleteMapping("/resource/{resourceId}")
    public R<Boolean> deleteResource(@PathVariable("resourceId") String resourceId) {
        try {
            boolean success = knowledgeResourceService.deleteResource(resourceId);
            return R.ok(success);
        } catch (Exception e) {
            log.error("删除知识库资源失败", e);
            return R.fail(e.getMessage());
        }
    }
    
    /**
     * 获取知识库的资源列表
     * 
     * @param knowledgeBaseId 知识库ID
     * @return 资源列表
     */
    @SaCheckLogin
    @GetMapping("/resource/list/{knowledgeBaseId}")
    public R<List<Map<String, Object>>> getResourceList(@PathVariable("knowledgeBaseId") String knowledgeBaseId) {
        try {
            List<Map<String, Object>> resources = knowledgeResourceService.getResourceList(knowledgeBaseId);
            return R.ok(resources);
        } catch (Exception e) {
            log.error("获取知识库资源列表失败", e);
            return R.fail(e.getMessage());
        }
    }
} 
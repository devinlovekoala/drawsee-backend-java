package cn.yifan.drawsee.controller;

import cn.dev33.satoken.annotation.SaCheckRole;
import cn.yifan.drawsee.constant.UserRole;
import cn.yifan.drawsee.pojo.dto.AddKnowledgeDTO;
import cn.yifan.drawsee.pojo.dto.CreateKnowledgeBaseDTO;
import cn.yifan.drawsee.pojo.dto.UpdateKnowledgeDTO;
import cn.yifan.drawsee.pojo.dto.UploadResourceDTO;
import cn.yifan.drawsee.pojo.mongo.Knowledge;
import cn.yifan.drawsee.pojo.vo.KnowledgeBaseVO;
import cn.yifan.drawsee.pojo.vo.ResourceCountVO;
import cn.yifan.drawsee.service.business.KnowledgeBaseService;
import cn.yifan.drawsee.service.business.KnowledgeBaseServiceExtension;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

/**
 * @FileName AdminKnowledgeBaseController
 * @Description 管理员知识库控制器类
 * @Author devin
 * @date 2025-08-15 15:30
 **/

@RestController
@RequestMapping("/admin/knowledge/base")
@SaCheckRole(UserRole.ADMIN)
public class AdminKnowledgeBaseController {

    private static final Logger logger = LoggerFactory.getLogger(AdminKnowledgeBaseController.class);

    @Autowired
    private KnowledgeBaseService knowledgeBaseService;
    
    @Autowired
    private KnowledgeBaseServiceExtension knowledgeBaseServiceExtension;

    /**
     * 管理员创建知识库
     * @param createKnowledgeBaseDTO 创建知识库DTO
     * @param isPublished 是否立即发布
     * @return 知识库ID
     */
    @PostMapping
    public String createKnowledgeBase(
            @RequestBody @Valid CreateKnowledgeBaseDTO createKnowledgeBaseDTO,
            @RequestParam(defaultValue = "true") boolean isPublished
    ) {
        return knowledgeBaseService.createKnowledgeBaseByAdmin(createKnowledgeBaseDTO, isPublished);
    }

    /**
     * 管理员获取所有知识库列表，包括所有管理员和教师创建的知识库
     * @return 知识库列表
     */
    @GetMapping("/all")
    public List<KnowledgeBaseVO> getAllKnowledgeBases() {
        return knowledgeBaseService.getAllKnowledgeBases();
    }
    
    /**
     * 管理员获取自己创建的知识库列表
     * @return 知识库列表
     */
    @GetMapping
    public List<KnowledgeBaseVO> getMyCreatedKnowledgeBases() {
        return knowledgeBaseService.getMyCreatedKnowledgeBases();
    }
    
    /**
     * 管理员获取特定知识库详情
     * @param id 知识库ID
     * @return 知识库详情
     */
    @GetMapping("/{id}")
    public KnowledgeBaseVO getKnowledgeBaseDetail(@PathVariable("id") String id) {
        return knowledgeBaseService.getKnowledgeBaseDetail(id);
    }
    
    /**
     * 管理员删除知识库（仅限自己创建的）
     * @param id 知识库ID
     */
    @DeleteMapping("/{id}")
    public void deleteKnowledgeBase(@PathVariable("id") String id) {
        knowledgeBaseService.deleteKnowledgeBase(id);
    }
    
    /**
     * 管理员获取知识库中的知识点列表
     * @param id 知识库ID
     * @return 知识点列表
     */
    @GetMapping("/{id}/knowledge")
    public List<Knowledge> getKnowledgeBaseKnowledgePoints(@PathVariable("id") String id) {
        return knowledgeBaseService.getKnowledgeBaseKnowledgePoints(id);
    }
    
    /**
     * 管理员添加知识点（仅限自己创建的知识库）
     * @param knowledgeBaseId 知识库ID
     * @param addKnowledgeDTO 添加知识点DTO
     * @return 知识点ID
     */
    @PostMapping("/{id}/knowledge")
    public String addKnowledgePoint(
            @PathVariable("id") String knowledgeBaseId,
            @RequestBody @Valid AddKnowledgeDTO addKnowledgeDTO) {
        return knowledgeBaseServiceExtension.addKnowledgeBaseKnowledgePoint(knowledgeBaseId, addKnowledgeDTO);
    }
    
    /**
     * 管理员更新知识点（仅限自己创建的知识库中的知识点）
     * @param knowledgeBaseId 知识库ID
     * @param knowledgeId 知识点ID
     * @param updateKnowledgeDTO 更新知识点DTO
     */
    @PutMapping("/{knowledgeBaseId}/knowledge/{knowledgeId}")
    public void updateKnowledgePoint(
            @PathVariable("knowledgeBaseId") String knowledgeBaseId,
            @PathVariable("knowledgeId") String knowledgeId,
            @RequestBody @Valid UpdateKnowledgeDTO updateKnowledgeDTO) {
        knowledgeBaseServiceExtension.updateKnowledgeBaseKnowledgePoint(knowledgeBaseId, knowledgeId, updateKnowledgeDTO);
    }
    
    /**
     * 管理员删除知识点（仅限自己创建的知识库中的知识点）
     * @param knowledgeBaseId 知识库ID
     * @param knowledgeId 知识点ID
     */
    @DeleteMapping("/{knowledgeBaseId}/knowledge/{knowledgeId}")
    public void deleteKnowledgePoint(
            @PathVariable("knowledgeBaseId") String knowledgeBaseId,
            @PathVariable("knowledgeId") String knowledgeId) {
        knowledgeBaseServiceExtension.deleteKnowledgeBaseKnowledgePoint(knowledgeBaseId, knowledgeId);
    }
    
    /**
     * 获取知识点资源统计
     * @param knowledgeId 知识点ID
     * @return 资源统计信息
     */
    @GetMapping("/knowledge/resource/count/{knowledgeId}")
    public ResourceCountVO getKnowledgeResourceCount(@PathVariable("knowledgeId") String knowledgeId) {
        return knowledgeBaseService.getKnowledgeResourceCount(knowledgeId);
    }
    
    /**
     * 管理员上传资源（仅限自己创建的知识库中的知识点）
     * @param file 文件
     * @param knowledgeId 知识点ID
     * @param resourceType 资源类型
     * @return 资源URL
     */
    @PostMapping(value = "/resource/upload", consumes = "multipart/form-data")
    public String uploadResource(
            @RequestParam("file") MultipartFile file,
            @RequestParam("knowledgeId") String knowledgeId,
            @RequestParam("resourceType") String resourceType) {
        
        UploadResourceDTO uploadResourceDTO = new UploadResourceDTO(knowledgeId, resourceType);
        return knowledgeBaseService.uploadResource(file, uploadResourceDTO);
    }
    
    /**
     * 管理员添加B站资源（仅限自己创建的知识库中的知识点）
     * @param bilibiliResource B站资源请求体
     * @return 资源URL
     */
    @PostMapping(value = "/resource/bilibili")
    public String addBilibiliResource(@RequestBody Map<String, String> bilibiliResource) {
        String knowledgeId = bilibiliResource.get("knowledgeId");
        String url = bilibiliResource.get("url");
        String resourceType = "bilibili";
        
        UploadResourceDTO uploadResourceDTO = new UploadResourceDTO(knowledgeId, resourceType);
        return knowledgeBaseService.addBilibiliResource(url, uploadResourceDTO);
    }

} 
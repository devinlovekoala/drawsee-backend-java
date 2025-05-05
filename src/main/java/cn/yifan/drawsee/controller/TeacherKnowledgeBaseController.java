package cn.yifan.drawsee.controller;

import cn.dev33.satoken.annotation.SaCheckRole;
import cn.yifan.drawsee.constant.UserRole;
import cn.yifan.drawsee.pojo.dto.AddKnowledgeDTO;
import cn.yifan.drawsee.pojo.dto.CreateKnowledgeBaseDTO;
import cn.yifan.drawsee.pojo.dto.UpdateKnowledgeDTO;
import cn.yifan.drawsee.pojo.dto.UploadResourceDTO;
import cn.yifan.drawsee.pojo.mongo.Knowledge;
import cn.yifan.drawsee.pojo.vo.KnowledgeBaseVO;
import cn.yifan.drawsee.service.business.KnowledgeBaseService;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

/**
 * @FileName TeacherKnowledgeBaseController
 * @Description 教师知识库控制器类，提供教师创建和管理知识库的接口
 * @Author devin
 * @date 2025-08-15 16:45
 **/

@RestController
@RequestMapping("/teacher/knowledge/base")
@SaCheckRole(UserRole.TEACHER)
public class TeacherKnowledgeBaseController {

    private static final Logger logger = LoggerFactory.getLogger(TeacherKnowledgeBaseController.class);

    @Autowired
    private KnowledgeBaseService knowledgeBaseService;

    /**
     * 教师创建知识库
     * @param createKnowledgeBaseDTO 创建知识库DTO
     * @return 知识库ID
     */
    @PostMapping
    public String createKnowledgeBase(@RequestBody @Valid CreateKnowledgeBaseDTO createKnowledgeBaseDTO) {
        return knowledgeBaseService.createKnowledgeBase(createKnowledgeBaseDTO);
    }
    
    /**
     * 获取教师创建的知识库列表
     * @return 知识库列表
     */
    @GetMapping("/created")
    public List<KnowledgeBaseVO> getCreatedKnowledgeBases() {
        return knowledgeBaseService.getMyCreatedKnowledgeBases();
    }

    /**
     * 获取教师加入的知识库列表
     * @return 知识库列表
     */
    @GetMapping("/joined")
    public List<KnowledgeBaseVO> getJoinedKnowledgeBases() {
        return knowledgeBaseService.getMyJoinedKnowledgeBases();
    }

    /**
     * 获取知识库详情
     * @param id 知识库ID
     * @return 知识库详情
     */
    @GetMapping("/{id}")
    public KnowledgeBaseVO getKnowledgeBaseDetail(@PathVariable("id") String id) {
        return knowledgeBaseService.getKnowledgeBaseDetail(id);
    }

    /**
     * 获取知识库中的知识点列表
     * @param id 知识库ID
     * @return 知识点列表
     */
    @GetMapping("/{id}/knowledge")
    public List<Knowledge> getKnowledgeBaseKnowledgePoints(@PathVariable("id") String id) {
        return knowledgeBaseService.getKnowledgeBaseKnowledgePoints(id);
    }

    /**
     * 添加知识点
     * @param knowledgeBaseId 知识库ID
     * @param addKnowledgeDTO 添加知识点DTO
     * @return 知识点ID
     */
    @PostMapping("/{id}/knowledge")
    public String addKnowledgePoint(
            @PathVariable("id") String knowledgeBaseId,
            @RequestBody @Valid AddKnowledgeDTO addKnowledgeDTO) {
        return knowledgeBaseService.addKnowledgeBaseKnowledgePoint(knowledgeBaseId, addKnowledgeDTO);
    }

    /**
     * 更新知识点
     * @param knowledgeBaseId 知识库ID
     * @param knowledgeId 知识点ID
     * @param updateKnowledgeDTO 更新知识点DTO
     */
    @PutMapping("/{knowledgeBaseId}/knowledge/{knowledgeId}")
    public void updateKnowledgePoint(
            @PathVariable("knowledgeBaseId") String knowledgeBaseId,
            @PathVariable("knowledgeId") String knowledgeId,
            @RequestBody @Valid UpdateKnowledgeDTO updateKnowledgeDTO) {
        knowledgeBaseService.updateKnowledgeBaseKnowledgePoint(knowledgeBaseId, knowledgeId, updateKnowledgeDTO);
    }

    /**
     * 删除知识点
     * @param knowledgeBaseId 知识库ID
     * @param knowledgeId 知识点ID
     */
    @DeleteMapping("/{knowledgeBaseId}/knowledge/{knowledgeId}")
    public void deleteKnowledgePoint(
            @PathVariable("knowledgeBaseId") String knowledgeBaseId,
            @PathVariable("knowledgeId") String knowledgeId) {
        knowledgeBaseService.deleteKnowledgeBaseKnowledgePoint(knowledgeBaseId, knowledgeId);
    }

    /**
     * 上传资源
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
     * 添加B站资源
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
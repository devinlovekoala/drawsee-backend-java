package cn.yifan.drawsee.controller;

import cn.dev33.satoken.annotation.SaCheckLogin;
import cn.yifan.drawsee.pojo.Result;

import cn.yifan.drawsee.pojo.dto.CreateKnowledgeBaseDTO;
import cn.yifan.drawsee.pojo.dto.JoinKnowledgeBaseDTO;
import cn.yifan.drawsee.pojo.dto.UploadResourceDTO;
import cn.yifan.drawsee.pojo.vo.KnowledgeBaseVO;
import cn.yifan.drawsee.pojo.vo.ResourceCountVO;
import cn.yifan.drawsee.service.business.KnowledgeBaseService;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

/**
 * @FileName KnowledgeBaseController
 * @Description 知识库控制器类
 * @Author devin
 * @date 2025-03-28 11:10
 **/

@RestController
// 临时注释掉登录验证，以便测试 @SaCheckLogin
public class KnowledgeBaseController {
    
    private static final Logger logger = LoggerFactory.getLogger(KnowledgeBaseController.class);

    @Autowired
    private KnowledgeBaseService knowledgeBaseService;

    /**
     * 创建知识库
     * @param createKnowledgeBaseDTO 创建知识库DTO
     * @return 知识库ID
     */
    @PostMapping("/knowledge/base")
    public Result<String> createKnowledgeBase(@RequestBody @Valid CreateKnowledgeBaseDTO createKnowledgeBaseDTO) {
        String id = knowledgeBaseService.createKnowledgeBase(createKnowledgeBaseDTO);
        return Result.success(id);
    }

    /**
     * 加入知识库
     * @param joinKnowledgeBaseDTO 加入知识库DTO
     * @return 知识库ID
     */
    @PostMapping("/knowledge/base/join")
    public Result<String> joinKnowledgeBase(@RequestBody @Valid JoinKnowledgeBaseDTO joinKnowledgeBaseDTO) {
        String id = knowledgeBaseService.joinKnowledgeBase(joinKnowledgeBaseDTO);
        return Result.success(id);
    }

    /**
     * 获取我创建的知识库列表
     * @return 知识库列表
     */
    @GetMapping("/knowledge/base/created")
    public Result<List<KnowledgeBaseVO>> getMyCreatedKnowledgeBases() {
        List<KnowledgeBaseVO> result = knowledgeBaseService.getMyCreatedKnowledgeBases();
        return Result.success(result);
    }

    /**
     * 获取我加入的知识库列表
     * @return 知识库列表
     */
    @GetMapping("/knowledge/base/joined")
    public Result<List<KnowledgeBaseVO>> getMyJoinedKnowledgeBases() {
        List<KnowledgeBaseVO> result = knowledgeBaseService.getMyJoinedKnowledgeBases();
        return Result.success(result);
    }
    
    /**
     * 获取知识库详情
     * @param id 知识库ID
     * @return 知识库详情
     */
    @GetMapping("/knowledge/base/{id}")
    public Result<KnowledgeBaseVO> getKnowledgeBaseDetail(@PathVariable("id") String id) {
        KnowledgeBaseVO result = knowledgeBaseService.getKnowledgeBaseDetail(id);
        return Result.success(result);
    }
    
    /**
     * 上传资源 - 专用接口
     * 仅处理multipart/form-data类型的文件上传请求
     * 
     * @param file 文件
     * @param knowledgeId 知识点ID
     * @param resourceType 资源类型
     * @return 资源URL
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
     * 添加B站资源 - 专用接口
     * 处理B站链接资源添加
     * 
     * @param bilibiliResource B站资源请求体
     * @return 资源URL
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
     * 上传资源 - 兼容API路径
     * @param file 文件
     * @param knowledgeId 知识点ID
     * @param resourceType 资源类型
     * @return 资源URL
     */
    @PostMapping(value = {"/api/knowledge-base/resource/upload", "/knowledge-base/resource/upload"}, consumes = "multipart/form-data")
    public Result<String> uploadResourceAlternative(
            @RequestParam("file") MultipartFile file,
            @RequestParam("knowledgeId") String knowledgeId,
            @RequestParam("resourceType") String resourceType) {
            
        return uploadResource(file, knowledgeId, resourceType);
    }
    
    /**
     * 添加B站资源 - 兼容API路径
     * @param bilibiliResource B站资源请求体
     * @return 资源URL
     */
    @PostMapping(value = {"/api/knowledge-base/resource/bilibili", "/knowledge-base/resource/bilibili"})
    public Result<String> addBilibiliResourceAlternative(@RequestBody Map<String, String> bilibiliResource) {
        return addBilibiliResource(bilibiliResource);
    }
    
    /**
     * 获取知识点资源统计信息
     * @param knowledgeId 知识点ID
     * @return 资源统计信息
     */
    @GetMapping("/knowledge/resource/count/{knowledgeId}")
    public Result<ResourceCountVO> getKnowledgeResourceCount(@PathVariable("knowledgeId") String knowledgeId) {
        logger.info("获取知识点资源统计: knowledgeId={}", knowledgeId);
        ResourceCountVO resourceCount = knowledgeBaseService.getKnowledgeResourceCount(knowledgeId);
        return Result.success(resourceCount);
    }
    
    /**
     * 获取知识点资源统计信息 - 兼容API路径
     * @param knowledgeId 知识点ID
     * @return 资源统计信息
     */
    @GetMapping({"/api/knowledge/resource/count/{knowledgeId}", "/knowledge-base/resource/count/{knowledgeId}"})
    public Result<ResourceCountVO> getKnowledgeResourceCountAlternative(@PathVariable("knowledgeId") String knowledgeId) {
        return getKnowledgeResourceCount(knowledgeId);
    }
} 
package cn.yifan.drawsee.service.business;

import cn.dev33.satoken.stp.StpUtil;
import cn.yifan.drawsee.exception.ApiError;
import cn.yifan.drawsee.exception.ApiException;
import cn.yifan.drawsee.mapper.KnowledgeBaseMapper;
import cn.yifan.drawsee.mapper.KnowledgeResourceMapper;
import cn.yifan.drawsee.pojo.dto.UploadResourceDTO;
import cn.yifan.drawsee.pojo.entity.KnowledgeBase;
import cn.yifan.drawsee.pojo.entity.KnowledgeResource;
import cn.yifan.drawsee.service.base.MinioService;
import cn.yifan.drawsee.util.UUIDUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.util.*;

/**
 * 知识资源服务
 */
@Slf4j
@Service
public class KnowledgeResourceService {

    @Autowired
    private KnowledgeResourceMapper knowledgeResourceMapper;
    
    @Autowired
    private KnowledgeBaseMapper knowledgeBaseMapper;
    
    @Autowired
    private MinioService minioService;

    /**
     * 上传资源文件
     */
    @Transactional
    public String uploadResource(UploadResourceDTO uploadResourceDTO, MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new ApiException(ApiError.PARAM_ERROR, "文件不能为空");
        }

        String resourceType = uploadResourceDTO.getResourceType();
        String originalFilename = file.getOriginalFilename();
        log.info("开始上传资源: resourceType={}, originalFilename={}, contentType={}, size={}",
                resourceType, originalFilename, file.getContentType(), file.getSize());
        
        if (!isValidResourceType(resourceType, originalFilename)) {
            log.error("文件类型不支持: {}, {}", resourceType, originalFilename);
            throw new ApiException(ApiError.FILE_TYPE_NOT_SUPPORTED, "文件不能为空");
        }

        // 验证知识库是否存在
        KnowledgeBase knowledgeBase = knowledgeBaseMapper.getById(uploadResourceDTO.getKnowledgeBaseId());
        if (knowledgeBase == null) {
            log.error("知识库不存在: {}", uploadResourceDTO.getKnowledgeBaseId());
            throw new ApiException(ApiError.KNOWLEDGE_BASE_NOT_EXISTED, "文件不能为空");
        }
        
        // 上传文件
        String objectName = getObjectName(resourceType, knowledgeBase.getId(), originalFilename);
        log.info("准备上传文件到Minio, objectName={}", objectName);
        
        try {
            // 使用通用文件上传方法
            String resourceUrl = minioService.uploadFile(file, objectName);
            log.info("文件上传成功, resourceUrl={}", resourceUrl);
            
            // 创建资源记录
            KnowledgeResource resource = new KnowledgeResource();
            resource.setId(UUIDUtils.generateUUID());
            resource.setKnowledgeBaseId(knowledgeBase.getId());
            resource.setResourceType(resourceType);
            resource.setTitle(uploadResourceDTO.getTitle());
            resource.setDescription(uploadResourceDTO.getDescription());
            resource.setUrl(resourceUrl);
            resource.setLocalPath(objectName);
            resource.setSize(file.getSize());
            resource.setCreatorId(StpUtil.getLoginIdAsLong());
            resource.setCreatedAt(new Date());
            resource.setUpdatedAt(new Date());
            resource.setIsDeleted(false);
            
            // 保存资源记录
            knowledgeResourceMapper.insert(resource);
            log.info("资源记录创建成功, resourceId={}", resource.getId());
            
            return resourceUrl;
        } catch (Exception e) {
            log.error("文件上传失败", e);
            throw new ApiException(ApiError.UPLOAD_FAILED, "文件不能为空");
        }
    }

    /**
     * 添加B站视频资源
     */
    @Transactional
    public String addBilibiliResource(String knowledgeBaseId, String title, String description, String bilibiliUrl, String coverUrl) {
        // 验证知识库是否存在
        KnowledgeBase knowledgeBase = knowledgeBaseMapper.getById(knowledgeBaseId);
        if (knowledgeBase == null) {
            log.error("知识库不存在: {}", knowledgeBaseId);
            throw new ApiException(ApiError.KNOWLEDGE_BASE_NOT_EXISTED, "文件不能为空");
        }
        
        // 验证B站URL格式
        if (!isBilibiliUrl(bilibiliUrl)) {
            log.error("无效的B站链接: {}", bilibiliUrl);
            throw new ApiException(ApiError.INVALID_BILIBILI_URL, "文件不能为空");
        }
        
        try {
            // 创建资源记录
            KnowledgeResource resource = new KnowledgeResource();
            resource.setId(UUIDUtils.generateUUID());
            resource.setKnowledgeBaseId(knowledgeBaseId);
            resource.setResourceType("VIDEO");
            resource.setTitle(title);
            resource.setDescription(description);
            resource.setCoverUrl(coverUrl);
            resource.setUrl(bilibiliUrl);
            resource.setCreatorId(StpUtil.getLoginIdAsLong());
            resource.setCreatedAt(new Date());
            resource.setUpdatedAt(new Date());
            resource.setIsDeleted(false);
            
            // 保存资源记录
            knowledgeResourceMapper.insert(resource);
            log.info("B站视频资源记录创建成功, resourceId={}", resource.getId());
            
            return resource.getId();
        } catch (Exception e) {
            log.error("添加B站资源失败", e);
            throw new ApiException(ApiError.RESOURCE_ADD_FAILED, "文件不能为空");
        }
    }

    /**
     * 获取知识库资源列表
     */
    public List<KnowledgeResource> getResourcesByKnowledgeBaseId(String knowledgeBaseId) {
        if (!StringUtils.hasText(knowledgeBaseId)) {
            return Collections.emptyList();
        }
        return knowledgeResourceMapper.getByKnowledgeBaseId(knowledgeBaseId, false);
    }

    /**
     * 获取知识库特定类型资源列表
     */
    public List<KnowledgeResource> getResourcesByKnowledgeBaseIdAndType(String knowledgeBaseId, String resourceType) {
        if (!StringUtils.hasText(knowledgeBaseId) || !StringUtils.hasText(resourceType)) {
            return Collections.emptyList();
        }
        return knowledgeResourceMapper.getByKnowledgeBaseIdAndType(knowledgeBaseId, resourceType, false);
    }

    /**
     * 根据ID获取资源
     */
    public KnowledgeResource getResourceById(String resourceId) {
        if (!StringUtils.hasText(resourceId)) {
            return null;
        }
        return knowledgeResourceMapper.getById(resourceId);
    }

    /**
     * 根据URL查询资源
     */
    public KnowledgeResource getResourceByUrl(String url) {
        if (!StringUtils.hasText(url)) {
            return null;
        }
        return knowledgeResourceMapper.getByUrl(url, false);
    }

    /**
     * 删除资源
     */
    @Transactional
    public boolean deleteResource(String resourceId) {
        if (!StringUtils.hasText(resourceId)) {
            return false;
        }
        
        KnowledgeResource resource = knowledgeResourceMapper.getById(resourceId);
        if (resource == null || resource.getIsDeleted()) {
            log.error("资源不存在或已被删除: {}", resourceId);
            return false;
        }
        
        // 逻辑删除资源
        resource.setIsDeleted(true);
        resource.setUpdatedAt(new Date());
        return knowledgeResourceMapper.update(resource) > 0;
    }

    /**
     * 更新资源
     */
    @Transactional
    public boolean updateResource(KnowledgeResource resource) {
        if (resource == null || !StringUtils.hasText(resource.getId())) {
            return false;
        }
        
        // 设置更新时间
        resource.setUpdatedAt(new Date());
        
        return knowledgeResourceMapper.update(resource) > 0;
    }

    /**
     * 统计知识库资源数量
     */
    public Map<String, Integer> countResourcesByKnowledgeBaseId(String knowledgeBaseId) {
        if (!StringUtils.hasText(knowledgeBaseId)) {
            return Collections.emptyMap();
        }
        
        List<Map<String, Object>> results = knowledgeResourceMapper.countResourcesByKnowledgeBaseId(knowledgeBaseId);
        Map<String, Integer> countMap = new HashMap<>();
        
        for (Map<String, Object> result : results) {
            String type = (String) result.get("resource_type");
            Integer count = ((Number) result.get("count")).intValue();
            countMap.put(type, count);
        }
        
        return countMap;
    }

    /**
     * 获取对象存储路径
     */
    private String getObjectName(String resourceType, String knowledgeBaseId, String originalFilename) {
        return "knowledge/" + knowledgeBaseId + "/" + resourceType.toLowerCase() + "/" + 
               System.currentTimeMillis() + "_" + originalFilename;
    }

    /**
     * 验证资源类型是否有效
     */
    private boolean isValidResourceType(String resourceType, String filename) {
        // 根据业务需求实现文件类型验证逻辑
        return true;
    }

    /**
     * 验证是否为B站URL
     */
    private boolean isBilibiliUrl(String url) {
        return url != null && (url.contains("bilibili.com") || url.contains("b23.tv"));
    }

    /**
     * 上传文档资源（PDF/Word）
     */
    @Transactional
    public String uploadDocumentResource(UploadResourceDTO uploadResourceDTO, MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new ApiException(ApiError.PARAM_ERROR, "文件不能为空");
        }

        String originalFilename = file.getOriginalFilename();
        if (originalFilename == null) {
            throw new ApiException(ApiError.PARAM_ERROR, "文件名不能为空");
        }
        
        String resourceType;
        if (originalFilename.toLowerCase().endsWith(".pdf")) {
            resourceType = "PDF";
        } else if (originalFilename.toLowerCase().endsWith(".doc") || 
                   originalFilename.toLowerCase().endsWith(".docx")) {
            resourceType = "WORD";
        } else {
            throw new ApiException(ApiError.FILE_TYPE_NOT_SUPPORTED, "只支持PDF或Word文档");
        }
        
        log.info("开始上传文档资源: resourceType={}, originalFilename={}, contentType={}, size={}",
                resourceType, originalFilename, file.getContentType(), file.getSize());
        
        // 验证知识库是否存在
        KnowledgeBase knowledgeBase = knowledgeBaseMapper.getById(uploadResourceDTO.getKnowledgeBaseId());
        if (knowledgeBase == null) {
            log.error("知识库不存在: {}", uploadResourceDTO.getKnowledgeBaseId());
            throw new ApiException(ApiError.KNOWLEDGE_BASE_NOT_EXISTED, "文件不能为空");
        }
        
        // 上传文件
        String objectName = getObjectName(resourceType, knowledgeBase.getId(), originalFilename);
        log.info("准备上传文档到Minio, objectName={}", objectName);
        
        try {
            // 使用通用文件上传方法
            String resourceUrl = minioService.uploadFile(file, objectName);
            log.info("文档上传成功, resourceUrl={}", resourceUrl);
            
            // 创建资源记录
            KnowledgeResource resource = new KnowledgeResource();
            resource.setId(UUIDUtils.generateUUID());
            resource.setKnowledgeBaseId(knowledgeBase.getId());
            resource.setResourceType(resourceType);
            resource.setTitle(uploadResourceDTO.getTitle());
            resource.setDescription(uploadResourceDTO.getDescription());
            resource.setUrl(resourceUrl);
            resource.setLocalPath(objectName);
            resource.setSize(file.getSize());
            resource.setCreatorId(StpUtil.getLoginIdAsLong());
            resource.setCreatedAt(new Date());
            resource.setUpdatedAt(new Date());
            resource.setIsDeleted(false);
            
            // 保存资源记录
            knowledgeResourceMapper.insert(resource);
            log.info("文档资源记录创建成功, resourceId={}", resource.getId());
            
            return resource.getId();
        } catch (Exception e) {
            log.error("文档上传失败", e);
            throw new ApiException(ApiError.UPLOAD_FAILED, "文件不能为空");
        }
    }
    
    /**
     * 上传视频资源
     */
    @Transactional
    public String uploadVideoResource(UploadResourceDTO uploadResourceDTO, MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new ApiException(ApiError.PARAM_ERROR, "文件不能为空");
        }

        String originalFilename = file.getOriginalFilename();
        if (originalFilename == null) {
            throw new ApiException(ApiError.PARAM_ERROR, "文件名不能为空");
        }
        
        // 验证视频文件类型
        if (!originalFilename.toLowerCase().endsWith(".mp4") && 
            !originalFilename.toLowerCase().endsWith(".mov") && 
            !originalFilename.toLowerCase().endsWith(".avi") && 
            !originalFilename.toLowerCase().endsWith(".mkv")) {
            throw new ApiException(ApiError.FILE_TYPE_NOT_SUPPORTED, "只支持MP4、MOV、AVI或MKV视频格式");
        }
        
        String resourceType = "VIDEO";
        log.info("开始上传视频资源: resourceType={}, originalFilename={}, contentType={}, size={}",
                resourceType, originalFilename, file.getContentType(), file.getSize());
        
        // 验证知识库是否存在
        KnowledgeBase knowledgeBase = knowledgeBaseMapper.getById(uploadResourceDTO.getKnowledgeBaseId());
        if (knowledgeBase == null) {
            log.error("知识库不存在: {}", uploadResourceDTO.getKnowledgeBaseId());
            throw new ApiException(ApiError.KNOWLEDGE_BASE_NOT_EXISTED, "文件不能为空");
        }
        
        // 上传文件
        String objectName = getObjectName(resourceType, knowledgeBase.getId(), originalFilename);
        log.info("准备上传视频到Minio, objectName={}", objectName);
        
        try {
            // 使用通用文件上传方法
            String resourceUrl = minioService.uploadFile(file, objectName);
            log.info("视频上传成功, resourceUrl={}", resourceUrl);
            
            // 创建资源记录
            KnowledgeResource resource = new KnowledgeResource();
            resource.setId(UUIDUtils.generateUUID());
            resource.setKnowledgeBaseId(knowledgeBase.getId());
            resource.setResourceType(resourceType);
            resource.setTitle(uploadResourceDTO.getTitle());
            resource.setDescription(uploadResourceDTO.getDescription());
            resource.setUrl(resourceUrl);
            resource.setLocalPath(objectName);
            resource.setSize(file.getSize());
            resource.setCreatorId(StpUtil.getLoginIdAsLong());
            resource.setCreatedAt(new Date());
            resource.setUpdatedAt(new Date());
            resource.setIsDeleted(false);
            
            // 保存资源记录
            knowledgeResourceMapper.insert(resource);
            log.info("视频资源记录创建成功, resourceId={}", resource.getId());
            
            return resource.getId();
        } catch (Exception e) {
            log.error("视频上传失败", e);
            throw new ApiException(ApiError.UPLOAD_FAILED, "文件不能为空");
        }
    }
    
    /**
     * 获取知识库资源类型计数
     */
    public Map<String, Integer> getResourceCountByTypes(String knowledgeBaseId) {
        if (!StringUtils.hasText(knowledgeBaseId)) {
            return Collections.emptyMap();
        }
        
        // 获取所有资源
        List<KnowledgeResource> resources = knowledgeResourceMapper.getByKnowledgeBaseId(knowledgeBaseId, false);
        
        // 按类型统计资源数量
        Map<String, Integer> countMap = new HashMap<>();
        countMap.put("PDF", 0);
        countMap.put("WORD", 0);
        countMap.put("VIDEO", 0);
        countMap.put("BILIBILI", 0);
        
        for (KnowledgeResource resource : resources) {
            String type = resource.getResourceType();
            if (type != null) {
                // 处理B站视频特殊情况
                if (type.equals("VIDEO") && isBilibiliUrl(resource.getUrl())) {
                    countMap.put("BILIBILI", countMap.getOrDefault("BILIBILI", 0) + 1);
                } else {
                    countMap.put(type, countMap.getOrDefault(type, 0) + 1);
                }
            }
        }
        
        return countMap;
    }
    
    /**
     * 获取知识库的资源列表
     */
    public List<Map<String, Object>> getResourceList(String knowledgeBaseId) {
        if (!StringUtils.hasText(knowledgeBaseId)) {
            return Collections.emptyList();
        }
        
        // 获取所有资源
        List<KnowledgeResource> resources = knowledgeResourceMapper.getByKnowledgeBaseId(knowledgeBaseId, false);
        
        // 转换为前端需要的格式
        List<Map<String, Object>> result = new ArrayList<>();
        for (KnowledgeResource resource : resources) {
            Map<String, Object> resourceMap = new HashMap<>();
            resourceMap.put("id", resource.getId());
            resourceMap.put("title", resource.getTitle());
            resourceMap.put("description", resource.getDescription());
            resourceMap.put("url", resource.getUrl());
            resourceMap.put("size", resource.getSize());
            resourceMap.put("createdAt", resource.getCreatedAt());
            
            // 确定资源类型
            String type = resource.getResourceType();
            if (type.equals("VIDEO") && isBilibiliUrl(resource.getUrl())) {
                resourceMap.put("type", "BILIBILI");
            } else {
                resourceMap.put("type", type);
            }
            
            // 添加封面图URL（如果有）
            if (StringUtils.hasText(resource.getCoverUrl())) {
                resourceMap.put("coverUrl", resource.getCoverUrl());
            }
            
            result.add(resourceMap);
        }
        
        return result;
    }
} 
package cn.yifan.drawsee.service.business;

import cn.dev33.satoken.stp.StpUtil;
import cn.yifan.drawsee.exception.ApiError;
import cn.yifan.drawsee.exception.ApiException;
import cn.yifan.drawsee.pojo.dto.AddKnowledgeDTO;
import cn.yifan.drawsee.pojo.dto.CreateKnowledgeBaseDTO;
import cn.yifan.drawsee.pojo.dto.JoinKnowledgeBaseDTO;
import cn.yifan.drawsee.pojo.dto.UpdateKnowledgeDTO;
import cn.yifan.drawsee.pojo.dto.UploadResourceDTO;
import cn.yifan.drawsee.pojo.mongo.Knowledge;
import cn.yifan.drawsee.pojo.mongo.KnowledgeBase;
import cn.yifan.drawsee.pojo.mongo.KnowledgePosition;
import cn.yifan.drawsee.pojo.mongo.KnowledgeResource;
import cn.yifan.drawsee.pojo.vo.KnowledgeBaseVO;
import cn.yifan.drawsee.pojo.vo.ResourceCountVO;
import cn.yifan.drawsee.constant.KnowledgeResourceType;
import cn.yifan.drawsee.repository.KnowledgeBaseRepository;
import cn.yifan.drawsee.repository.KnowledgeRepository;
import cn.yifan.drawsee.service.base.MinioService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Random;

/**
 * @FileName KnowledgeBaseService
 * @Description 知识库服务类
 * @Author devin
 * @date 2025-03-28 11:05
 **/

@Service
public class KnowledgeBaseService extends AbstractKnowledgeBaseService {
    
    private static final Logger logger = LoggerFactory.getLogger(KnowledgeBaseService.class);
    
    @Autowired
    private TeacherService teacherService;
    
    @Autowired
    private MinioService minioService;

    /**
     * 创建知识库
     * @param createKnowledgeBaseDTO 创建知识库DTO
     * @return 知识库ID
     */
    public String createKnowledgeBase(CreateKnowledgeBaseDTO createKnowledgeBaseDTO) {
        // 验证用户是否为教师
        teacherService.validateTeacher();
        
        // 检查知识库名称是否已存在
        KnowledgeBase existKnowledgeBase = knowledgeBaseRepository.findByName(createKnowledgeBaseDTO.getName());
        if (existKnowledgeBase != null) {
            throw new ApiException(ApiError.KNOWLEDGE_BASE_HAD_EXISTED);
        }
        
        // 生成邀请码
        String invitationCode = generateInvitationCode();
        
        // 创建知识库
        KnowledgeBase knowledgeBase = new KnowledgeBase();
        knowledgeBase.setName(createKnowledgeBaseDTO.getName());
        knowledgeBase.setDescription(createKnowledgeBaseDTO.getDescription());
        knowledgeBase.setSubject(createKnowledgeBaseDTO.getSubject());
        knowledgeBase.setInvitationCode(invitationCode);
        knowledgeBase.setCreatorId(StpUtil.getLoginIdAsLong());
        knowledgeBase.setCreatedAt(new Date());
        knowledgeBase.setUpdatedAt(new Date());
        knowledgeBase.setKnowledgeIds(new ArrayList<>());
        knowledgeBase.setMembers(new ArrayList<>());
        knowledgeBase.getMembers().add(StpUtil.getLoginIdAsLong());
        knowledgeBase.setIsDeleted(false);
        
        knowledgeBaseRepository.save(knowledgeBase);
        
        return knowledgeBase.getId();
    }
    
    /**
     * 管理员创建知识库 - 无需教师角色验证
     * @param createKnowledgeBaseDTO 创建知识库DTO
     * @param isPublished 是否发布
     * @return 知识库ID
     */
    public String createKnowledgeBaseByAdmin(CreateKnowledgeBaseDTO createKnowledgeBaseDTO, boolean isPublished) {
        // 检查知识库名称是否已存在
        KnowledgeBase existKnowledgeBase = knowledgeBaseRepository.findByName(createKnowledgeBaseDTO.getName());
        if (existKnowledgeBase != null) {
            throw new ApiException(ApiError.KNOWLEDGE_BASE_HAD_EXISTED);
        }
        
        // 生成邀请码
        String invitationCode = generateInvitationCode();
        
        // 创建知识库
        KnowledgeBase knowledgeBase = new KnowledgeBase();
        knowledgeBase.setName(createKnowledgeBaseDTO.getName());
        knowledgeBase.setDescription(createKnowledgeBaseDTO.getDescription());
        knowledgeBase.setSubject(createKnowledgeBaseDTO.getSubject());
        knowledgeBase.setInvitationCode(invitationCode);
        knowledgeBase.setCreatorId(StpUtil.getLoginIdAsLong());
        knowledgeBase.setCreatedAt(new Date());
        knowledgeBase.setUpdatedAt(new Date());
        knowledgeBase.setKnowledgeIds(new ArrayList<>());
        knowledgeBase.setMembers(new ArrayList<>());
        knowledgeBase.getMembers().add(StpUtil.getLoginIdAsLong());
        knowledgeBase.setIsDeleted(false);
        knowledgeBase.setIsPublished(isPublished);
        
        knowledgeBaseRepository.save(knowledgeBase);
        
        return knowledgeBase.getId();
    }
    
    /**
     * 加入知识库
     * @param joinKnowledgeBaseDTO 加入知识库DTO
     * @return 知识库ID
     */
    public String joinKnowledgeBase(JoinKnowledgeBaseDTO joinKnowledgeBaseDTO) {
        // 检查邀请码是否存在
        KnowledgeBase knowledgeBase = knowledgeBaseRepository.findByInvitationCode(joinKnowledgeBaseDTO.getInvitationCode());
        if (knowledgeBase == null) {
            throw new ApiException(ApiError.INVALID_INVITATION_CODE);
        }
        
        Long userId = StpUtil.getLoginIdAsLong();
        
        // 检查用户是否已加入该知识库
        if (knowledgeBase.getMembers().contains(userId)) {
            throw new ApiException(ApiError.KNOWLEDGE_BASE_ALREADY_JOINED);
        }
        
        // 加入知识库
        knowledgeBase.getMembers().add(userId);
        knowledgeBase.setUpdatedAt(new Date());
        knowledgeBaseRepository.save(knowledgeBase);
        
        return knowledgeBase.getId();
    }
    
    /**
     * 获取我创建的知识库列表
     * @return 知识库列表
     */
    public List<KnowledgeBaseVO> getMyCreatedKnowledgeBases() {
        Long userId = StpUtil.getLoginIdAsLong();
        List<KnowledgeBase> knowledgeBases = knowledgeBaseRepository.findAllByCreatorId(userId);
        return convertToVOList(knowledgeBases);
    }
    
    /**
     * 获取我加入的知识库列表
     * @return 知识库列表
     */
    public List<KnowledgeBaseVO> getMyJoinedKnowledgeBases() {
        Long userId = StpUtil.getLoginIdAsLong();
        List<KnowledgeBase> knowledgeBases = knowledgeBaseRepository.findByMembersContaining(userId);
        return convertToVOList(knowledgeBases);
    }
    
    /**
     * 获取知识库详情
     * @param id 知识库ID
     * @return 知识库详情
     */
    public KnowledgeBaseVO getKnowledgeBaseDetail(String id) {
        KnowledgeBase knowledgeBase = knowledgeBaseRepository.findById(id).orElse(null);
        if (knowledgeBase == null) {
            throw new ApiException(ApiError.KNOWLEDGE_BASE_NOT_EXISTED);
        }
        
        KnowledgeBaseVO knowledgeBaseVO = new KnowledgeBaseVO();
        BeanUtils.copyProperties(knowledgeBase, knowledgeBaseVO);
        knowledgeBaseVO.setMemberCount(knowledgeBase.getMembers().size());
        
        return knowledgeBaseVO;
    }
    
    /**
     * 为知识点上传资源
     * @param file 文件
     * @param uploadResourceDTO 上传资源DTO
     * @return 资源URL
     */
    public String uploadResource(MultipartFile file, UploadResourceDTO uploadResourceDTO) {
        // 验证文件类型
        String resourceType = uploadResourceDTO.getResourceType();
        String originalFilename = file.getOriginalFilename();
        logger.info("开始上传资源: resourceType={}, originalFilename={}, contentType={}, size={}",
                resourceType, originalFilename, file.getContentType(), file.getSize());
        
        if (!isValidResourceType(resourceType, originalFilename)) {
            logger.error("文件类型不支持: {}, {}", resourceType, originalFilename);
            throw new ApiException(ApiError.FILE_TYPE_NOT_SUPPORTED);
        }
        
        // 验证知识点是否存在
        Knowledge knowledge = knowledgeRepository.findById(uploadResourceDTO.getKnowledgeId()).orElse(null);
        if (knowledge == null) {
            logger.error("知识点不存在: {}", uploadResourceDTO.getKnowledgeId());
            throw new ApiException(ApiError.KNOWLEDGE_NOT_EXISTED);
        }
        
        // 上传文件
        String objectName = getObjectName(resourceType, knowledge.getId(), originalFilename);
        logger.info("准备上传文件到Minio, objectName={}", objectName);
        
        try {
            // 使用通用文件上传方法
            String resourceUrl = minioService.uploadFile(file, objectName);
            logger.info("文件上传成功, resourceUrl={}", resourceUrl);
            
            // 更新知识点资源
            KnowledgeResource knowledgeResource = new KnowledgeResource();
            knowledgeResource.setType(resourceType);
            knowledgeResource.setValue(resourceUrl);
            
            if (knowledge.getResources() == null) {
                knowledge.setResources(new ArrayList<>());
            }
            
            knowledge.getResources().add(knowledgeResource);
            knowledgeRepository.save(knowledge);
            logger.info("知识点资源更新成功, knowledgeId={}", knowledge.getId());
            
            return resourceUrl;
        } catch (Exception e) {
            logger.error("文件上传失败", e);
            throw new ApiException(ApiError.UPLOAD_FAILED);
        }
    }
    
    /**
     * 为知识点添加B站资源
     * @param url B站URL
     * @param uploadResourceDTO 上传资源DTO
     * @return 资源URL
     */
    public String addBilibiliResource(String url, UploadResourceDTO uploadResourceDTO) {
        logger.info("添加B站资源: knowledgeId={}, url={}", uploadResourceDTO.getKnowledgeId(), url);
        
        // 验证知识点是否存在
        Knowledge knowledge = knowledgeRepository.findById(uploadResourceDTO.getKnowledgeId()).orElse(null);
        if (knowledge == null) {
            logger.error("知识点不存在: {}", uploadResourceDTO.getKnowledgeId());
            throw new ApiException(ApiError.KNOWLEDGE_NOT_EXISTED);
        }
        
        // 验证URL是否为B站链接
        if (!url.contains("bilibili.com")) {
            logger.error("非法的B站链接: {}", url);
            throw new ApiException(ApiError.INVALID_BILIBILI_URL);
        }
        
        try {
            // 更新知识点资源
            KnowledgeResource knowledgeResource = new KnowledgeResource();
            knowledgeResource.setType("bilibili");
            knowledgeResource.setValue(url);
            
            if (knowledge.getResources() == null) {
                knowledge.setResources(new ArrayList<>());
            }
            
            knowledge.getResources().add(knowledgeResource);
            knowledgeRepository.save(knowledge);
            logger.info("B站资源添加成功, knowledgeId={}", knowledge.getId());
            
            return url;
        } catch (Exception e) {
            logger.error("B站资源添加失败", e);
            throw new ApiException(ApiError.RESOURCE_ADD_FAILED);
        }
    }
    
    /**
     * 生成邀请码
     * @return 邀请码
     */
    private String generateInvitationCode() {
        String characters = "ACDEFGHJKLMNPQRSTUVWXYZ234679"; // 去除了容易混淆的字符
        Random random = new SecureRandom();
        StringBuilder sb = new StringBuilder(8);

        for (int i = 0; i < 8; i++) {
            sb.append(characters.charAt(random.nextInt(characters.length())));
        }

        String code = sb.toString();
        // 检查是否已存在（极小概率冲突时可重试）
        KnowledgeBase existKnowledgeBase = knowledgeBaseRepository.findByInvitationCode(code);
        if (existKnowledgeBase != null) {
            return generateInvitationCode();
        }
        return code;
    }
    
    /**
     * 将知识库列表转换为VO列表
     * @param knowledgeBases 知识库列表
     * @return VO列表
     */
    private List<KnowledgeBaseVO> convertToVOList(List<KnowledgeBase> knowledgeBases) {
        List<KnowledgeBaseVO> result = new ArrayList<>();
        for (KnowledgeBase knowledgeBase : knowledgeBases) {
            if (knowledgeBase.getIsDeleted()) {
                continue;
            }
            
            KnowledgeBaseVO vo = new KnowledgeBaseVO();
            BeanUtils.copyProperties(knowledgeBase, vo);
            vo.setMemberCount(knowledgeBase.getMembers().size());
            result.add(vo);
        }
        return result;
    }
    
    /**
     * 验证资源类型是否有效
     * @param resourceType 资源类型
     * @param filename 文件名
     * @return 是否有效
     */
    private boolean isValidResourceType(String resourceType, String filename) {
        if (filename == null) {
            return false;
        }
        
        String lowerFilename = filename.toLowerCase();
        switch (resourceType) {
            case "word" -> {
                return lowerFilename.endsWith(".doc") || lowerFilename.endsWith(".docx");
            }
            case "pdf" -> {
                return lowerFilename.endsWith(".pdf");
            }
            case "mp4" -> {
                return lowerFilename.endsWith(".mp4");
            }
            default -> {
                return false;
            }
        }
    }
    
    /**
     * 获取对象名称
     * @param resourceType 资源类型
     * @param knowledgeId 知识点ID
     * @param filename 文件名
     * @return 对象名称
     */
    private String getObjectName(String resourceType, String knowledgeId, String filename) {
        String basePath;
        switch (resourceType) {
            case "word" -> basePath = "knowledge/resource/word/";
            case "pdf" -> basePath = "knowledge/resource/pdf/";
            case "mp4" -> basePath = "knowledge/resource/mp4/";
            default -> basePath = "knowledge/resource/";
        }
        
        return basePath + knowledgeId + "/" + filename;
    }
    
    /**
     * 获取知识点资源统计信息
     * @param knowledgeId 知识点ID
     * @return 资源统计信息
     */
    public ResourceCountVO getKnowledgeResourceCount(String knowledgeId) {
        // 验证知识点是否存在
        Knowledge knowledge = knowledgeRepository.findById(knowledgeId).orElse(null);
        if (knowledge == null) {
            logger.error("知识点不存在: {}", knowledgeId);
            throw new ApiException(ApiError.KNOWLEDGE_NOT_EXISTED);
        }
        
        // 获取资源列表
        List<KnowledgeResource> resources = knowledge.getResources();
        if (resources == null || resources.isEmpty()) {
            // 如果没有资源，返回空统计
            return ResourceCountVO.builder()
                    .total(0)
                    .pdf(0)
                    .word(0)
                    .mp4(0)
                    .bilibili(0)
                    .build();
        }
        
        // 计算各类型资源数量
        int pdfCount = 0;
        int wordCount = 0;
        int mp4Count = 0;
        int bilibiliCount = 0;
        
        for (KnowledgeResource resource : resources) {
            String type = resource.getType();
            switch (type) {
                case KnowledgeResourceType.PDF -> pdfCount++;
                case KnowledgeResourceType.WORD -> wordCount++;
                case KnowledgeResourceType.MP4 -> mp4Count++;
                case KnowledgeResourceType.BILIBILI -> bilibiliCount++;
            }
        }
        
        // 构建返回对象
        return ResourceCountVO.builder()
                .total(resources.size())
                .pdf(pdfCount)
                .word(wordCount)
                .mp4(mp4Count)
                .bilibili(bilibiliCount)
                .build();
    }

    /**
     * 获取知识库中的知识点列表
     * @param knowledgeBaseId 知识库ID
     * @return 知识点列表
     */
    public List<Knowledge> getKnowledgeBaseKnowledgePoints(String knowledgeBaseId) {
        // 验证知识库是否存在
        KnowledgeBase knowledgeBase = validateKnowledgeBase(knowledgeBaseId);
        
        // 获取知识库中的知识点列表
        List<String> knowledgeIds = knowledgeBase.getKnowledgeIds();
        if (knowledgeIds == null || knowledgeIds.isEmpty()) {
            return new ArrayList<>();
        }
        
        return knowledgeRepository.findAllByIdIn(knowledgeIds);
    }

    /**
     * 获取知识库中的知识图谱数据
     * @param knowledgeBaseId 知识库ID
     * @return 知识图谱数据（节点和连接）
     */
    @SuppressWarnings("unchecked")
    public Map<String, Object> getKnowledgeBaseGraph(String knowledgeBaseId) {
        // 验证知识库是否存在
        KnowledgeBase knowledgeBase = validateKnowledgeBase(knowledgeBaseId);
        
        // 验证用户是否有权限访问该知识库
        validateUserAccess(knowledgeBase);
        
        // 获取知识库中的知识点列表
        List<Knowledge> knowledgePoints = getKnowledgeBaseKnowledgePoints(knowledgeBaseId);
        
        // 准备返回数据
        Map<String, Object> result = new HashMap<>();
        
        // 准备节点数据
        List<Map<String, Object>> nodes = new ArrayList<>();
        for (Knowledge knowledge : knowledgePoints) {
            Map<String, Object> node = new HashMap<>();
            node.put("id", knowledge.getId());
            node.put("type", "knowledge");
            
            Map<String, Object> data = new HashMap<>();
            data.put("label", knowledge.getName());
            data.put("level", knowledge.getLevel());
            data.put("subject", knowledge.getSubject());
            data.put("hasResources", knowledge.getResources() != null && !knowledge.getResources().isEmpty());
            
            node.put("data", data);
            nodes.add(node);
        }
        
        // 准备边数据
        List<Map<String, Object>> edges = new ArrayList<>();
        
        // 添加父子关系边
        for (Knowledge knowledge : knowledgePoints) {
            if (knowledge.getParentId() != null && !knowledge.getParentId().isEmpty()) {
                Map<String, Object> edge = new HashMap<>();
                edge.put("id", "e-" + knowledge.getParentId() + "-" + knowledge.getId());
                edge.put("source", knowledge.getParentId());
                edge.put("target", knowledge.getId());
                
                Map<String, Object> data = new HashMap<>();
                data.put("type", "parent-child");
                
                edge.put("data", data);
                edges.add(edge);
            }
        }
        
        result.put("nodes", nodes);
        result.put("edges", edges);
        
        return result;
    }
} 
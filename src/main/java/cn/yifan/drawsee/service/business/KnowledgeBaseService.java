package cn.yifan.drawsee.service.business;

import cn.dev33.satoken.stp.StpUtil;
import cn.yifan.drawsee.constant.UserRole;
import cn.yifan.drawsee.exception.ApiError;
import cn.yifan.drawsee.exception.ApiException;
import cn.yifan.drawsee.pojo.dto.CreateKnowledgeBaseDTO;
import cn.yifan.drawsee.pojo.dto.JoinKnowledgeBaseDTO;
import cn.yifan.drawsee.pojo.dto.UploadResourceDTO;
import cn.yifan.drawsee.pojo.dto.AddKnowledgeDTO;
import cn.yifan.drawsee.pojo.dto.UpdateKnowledgeDTO;
import cn.yifan.drawsee.pojo.mongo.Course;
import cn.yifan.drawsee.pojo.mongo.Knowledge;
import cn.yifan.drawsee.pojo.mongo.KnowledgeBase;
import cn.yifan.drawsee.pojo.mongo.KnowledgeResource;
import cn.yifan.drawsee.pojo.vo.KnowledgeBaseVO;
import cn.yifan.drawsee.pojo.vo.ResourceCountVO;
import cn.yifan.drawsee.constant.KnowledgeResourceType;
import cn.yifan.drawsee.repository.CourseRepository;
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
    
    @Autowired
    private CourseRepository courseRepository;

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
     * 为课程创建知识库
     * @param courseId 课程ID
     * @param createKnowledgeBaseDTO 创建知识库DTO
     * @return 知识库ID
     */
    public String createKnowledgeBaseForCourse(String courseId, CreateKnowledgeBaseDTO createKnowledgeBaseDTO) {
        Course course = courseRepository.findById(courseId).orElse(null);
        if (course == null) {
            throw new ApiException(ApiError.COURSE_NOT_EXISTED);
        }
        
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
        
        // 根据课程创建者角色设置知识库可见性
        // 管理员创建的课程对应的知识库对所有用户可见
        // 教师创建的课程对应的知识库仅对班级学生可见
        if (course.getCreatorRole() != null && course.getCreatorRole().equals(UserRole.ADMIN)) {
            knowledgeBase.setIsPublished(true); // 管理员创建的班级，知识库设为公开
        } else {
            knowledgeBase.setIsPublished(false); // 教师创建的班级，知识库设为仅班级可见
            // 将班级学生添加为知识库成员
            if (course.getStudentIds() != null && !course.getStudentIds().isEmpty()) {
                knowledgeBase.getMembers().addAll(course.getStudentIds());
            }
        }
        
        knowledgeBaseRepository.save(knowledgeBase);
        
        // 将知识库ID添加到课程中
        if (course.getKnowledgeBaseIds() == null) {
            course.setKnowledgeBaseIds(new ArrayList<>());
        }
        course.getKnowledgeBaseIds().add(knowledgeBase.getId());
        courseRepository.save(course);
        
        return knowledgeBase.getId();
    }

    /**
     * 将知识库实体转换为VO对象
     * @param knowledgeBase 知识库实体
     * @return 知识库VO对象
     */
    public KnowledgeBaseVO convertToVO(KnowledgeBase knowledgeBase) {
        if (knowledgeBase == null) {
            return null;
        }
        
        KnowledgeBaseVO vo = new KnowledgeBaseVO();
        BeanUtils.copyProperties(knowledgeBase, vo);
        
        // 设置知识点数量
        if (knowledgeBase.getKnowledgeIds() != null) {
            vo.setKnowledgeCount(knowledgeBase.getKnowledgeIds().size());
        } else {
            vo.setKnowledgeCount(0);
        }
        
        return vo;
    }

    /**
     * 管理员获取所有知识库列表
     * 注意：仅展示未删除的知识库
     * @return 知识库列表
     */
    public List<KnowledgeBaseVO> getAllKnowledgeBases() {
        List<KnowledgeBase> knowledgeBases = knowledgeBaseRepository.findAll();
        return convertToVOList(knowledgeBases);
    }

    /**
     * 删除知识库
     * @param knowledgeBaseId 知识库ID
     */
    public void deleteKnowledgeBase(String knowledgeBaseId) {
        // 验证知识库是否存在
        KnowledgeBase knowledgeBase = validateKnowledgeBase(knowledgeBaseId);
        
        // 检查当前用户是否有权限删除（仅限知识库创建者）
        Long userId = StpUtil.getLoginIdAsLong();
        if (!knowledgeBase.getCreatorId().equals(userId)) {
            throw new ApiException(ApiError.PERMISSION_DENIED);
        }
        
        // 逻辑删除知识库
        knowledgeBase.setIsDeleted(true);
        knowledgeBaseRepository.save(knowledgeBase);
        
        logger.info("知识库已删除: knowledgeBaseId={}, userId={}", knowledgeBaseId, userId);
    }

    /**
     * 获取当前用户可以访问的所有知识库列表
     * 包括：自己创建的和已加入的知识库
     * @return 知识库列表
     */
    public List<KnowledgeBaseVO> getKnowledgeBasesForCurrentUser() {
        Long userId = StpUtil.getLoginIdAsLong();
        
        // 查询用户创建的和加入的知识库
        List<KnowledgeBase> createdKnowledgeBases = knowledgeBaseRepository.findAllByCreatorId(userId);
        List<KnowledgeBase> joinedKnowledgeBases = knowledgeBaseRepository.findByMembersContaining(userId);
        
        // 合并去重
        Map<String, KnowledgeBase> knowledgeBaseMap = new HashMap<>();
        
        // 添加创建的知识库
        for (KnowledgeBase kb : createdKnowledgeBases) {
            if (!kb.getIsDeleted()) {
                knowledgeBaseMap.put(kb.getId(), kb);
            }
        }
        
        // 添加加入的知识库
        for (KnowledgeBase kb : joinedKnowledgeBases) {
            if (!kb.getIsDeleted() && !knowledgeBaseMap.containsKey(kb.getId())) {
                knowledgeBaseMap.put(kb.getId(), kb);
            }
        }
        
        // 转换为VO
        List<KnowledgeBaseVO> result = new ArrayList<>();
        for (KnowledgeBase kb : knowledgeBaseMap.values()) {
            KnowledgeBaseVO vo = new KnowledgeBaseVO();
            BeanUtils.copyProperties(kb, vo);
            vo.setMemberCount(kb.getMembers().size());
            if (kb.getKnowledgeIds() != null) {
                vo.setKnowledgeCount(kb.getKnowledgeIds().size());
            } else {
                vo.setKnowledgeCount(0);
            }
            result.add(vo);
        }
        
        return result;
    }

    /**
     * 在知识库中添加知识点
     * @param knowledgeBaseId 知识库ID
     * @param addKnowledgeDTO 添加知识点DTO
     * @return 知识点ID
     */
    public String addKnowledgeBaseKnowledgePoint(String knowledgeBaseId, AddKnowledgeDTO addKnowledgeDTO) {
        // 验证知识库是否存在
        KnowledgeBase knowledgeBase = validateKnowledgeBase(knowledgeBaseId);
        
        // 验证用户是否有编辑权限
        validateUserEditPermission(knowledgeBase);
        
        // 创建知识点
        Knowledge knowledge = new Knowledge();
        knowledge.setName(addKnowledgeDTO.getName());
        knowledge.setSubject(addKnowledgeDTO.getSubject());
        knowledge.setAliases(addKnowledgeDTO.getAliases());
        knowledge.setLevel(addKnowledgeDTO.getLevel());
        knowledge.setParentId(addKnowledgeDTO.getParentId());
        knowledge.setChildrenIds(new ArrayList<>());
        knowledge.setResources(new ArrayList<>());
        knowledge.setKnowledgeBaseId(knowledgeBaseId);
        
        // 保存知识点
        Knowledge savedKnowledge = knowledgeRepository.save(knowledge);
        
        // 如果有父节点，更新父节点的子节点列表
        if (addKnowledgeDTO.getParentId() != null && !addKnowledgeDTO.getParentId().isEmpty()) {
            Knowledge parent = knowledgeRepository.findById(addKnowledgeDTO.getParentId()).orElse(null);
            if (parent != null) {
                if (parent.getChildrenIds() == null) {
                    parent.setChildrenIds(new ArrayList<>());
                }
                parent.getChildrenIds().add(savedKnowledge.getId());
                knowledgeRepository.save(parent);
            }
        }
        
        // 更新知识库的知识点列表
        if (knowledgeBase.getKnowledgeIds() == null) {
            knowledgeBase.setKnowledgeIds(new ArrayList<>());
        }
        knowledgeBase.getKnowledgeIds().add(savedKnowledge.getId());
        knowledgeBase.setUpdatedAt(new Date());
        knowledgeBaseRepository.save(knowledgeBase);
        
        return savedKnowledge.getId();
    }

    /**
     * 更新知识库中的知识点
     * @param knowledgeBaseId 知识库ID
     * @param knowledgeId 知识点ID
     * @param updateKnowledgeDTO 更新知识点DTO
     */
    public void updateKnowledgeBaseKnowledgePoint(String knowledgeBaseId, String knowledgeId, UpdateKnowledgeDTO updateKnowledgeDTO) {
        // 验证知识库是否存在
        KnowledgeBase knowledgeBase = validateKnowledgeBase(knowledgeBaseId);
        
        // 验证用户是否有编辑权限
        validateUserEditPermission(knowledgeBase);
        
        // 验证知识点是否属于该知识库
        validateKnowledgeBelongsToBase(knowledgeBase, knowledgeId);
        
        // 验证知识点是否存在
        Knowledge knowledge = validateKnowledge(knowledgeId);
        
        // 更新知识点属性
        knowledge.setName(updateKnowledgeDTO.getName());
        knowledge.setSubject(updateKnowledgeDTO.getSubject());
        knowledge.setAliases(updateKnowledgeDTO.getAliases());
        knowledge.setLevel(updateKnowledgeDTO.getLevel());
        
        // 更新父节点关系
        String oldParentId = knowledge.getParentId();
        String newParentId = updateKnowledgeDTO.getParentId();
        
        // 如果父节点发生变化
        if ((oldParentId == null && newParentId != null) || 
            (oldParentId != null && !oldParentId.equals(newParentId))) {
            
            // 如果有旧父节点，从其子节点列表中移除
            if (oldParentId != null && !oldParentId.isEmpty()) {
                Knowledge oldParent = knowledgeRepository.findById(oldParentId).orElse(null);
                if (oldParent != null && oldParent.getChildrenIds() != null) {
                    oldParent.getChildrenIds().remove(knowledgeId);
                    knowledgeRepository.save(oldParent);
                }
            }
            
            // 如果有新父节点，添加到其子节点列表中
            if (newParentId != null && !newParentId.isEmpty()) {
                Knowledge newParent = knowledgeRepository.findById(newParentId).orElse(null);
                if (newParent != null) {
                    if (newParent.getChildrenIds() == null) {
                        newParent.setChildrenIds(new ArrayList<>());
                    }
                    newParent.getChildrenIds().add(knowledgeId);
                    knowledgeRepository.save(newParent);
                }
            }
            
            // 更新知识点的父节点ID
            knowledge.setParentId(newParentId);
        }
        
        // 保存更新后的知识点
        knowledgeRepository.save(knowledge);
    }

    /**
     * 删除知识库中的知识点
     * @param knowledgeBaseId 知识库ID
     * @param knowledgeId 知识点ID
     */
    public void deleteKnowledgeBaseKnowledgePoint(String knowledgeBaseId, String knowledgeId) {
        // 验证知识库是否存在
        KnowledgeBase knowledgeBase = validateKnowledgeBase(knowledgeBaseId);
        
        // 验证用户是否有编辑权限
        validateUserEditPermission(knowledgeBase);
        
        // 验证知识点是否属于该知识库
        validateKnowledgeBelongsToBase(knowledgeBase, knowledgeId);
        
        // 验证知识点是否存在
        Knowledge knowledge = validateKnowledge(knowledgeId);
        
        // 从知识库的知识点列表中移除
        knowledgeBase.getKnowledgeIds().remove(knowledgeId);
        knowledgeBaseRepository.save(knowledgeBase);
        
        // 如果有父节点，从父节点的子节点列表中移除
        String parentId = knowledge.getParentId();
        if (parentId != null && !parentId.isEmpty()) {
            Knowledge parent = knowledgeRepository.findById(parentId).orElse(null);
            if (parent != null && parent.getChildrenIds() != null) {
                parent.getChildrenIds().remove(knowledgeId);
                knowledgeRepository.save(parent);
            }
        }
        
        // 如果有子节点，更新子节点的父节点引用
        List<String> childrenIds = knowledge.getChildrenIds();
        if (childrenIds != null && !childrenIds.isEmpty()) {
            for (String childId : childrenIds) {
                Knowledge child = knowledgeRepository.findById(childId).orElse(null);
                if (child != null) {
                    child.setParentId(null);
                    knowledgeRepository.save(child);
                }
            }
        }
        
        // 删除知识点
        knowledgeRepository.delete(knowledge);
    }

    /**
     * 验证知识点是否属于该知识库
     * @param knowledgeBase 知识库对象
     * @param knowledgeId 知识点ID
     */
    public void validateKnowledgeBelongsToBase(KnowledgeBase knowledgeBase, String knowledgeId) {
        if (knowledgeBase.getKnowledgeIds() == null || !knowledgeBase.getKnowledgeIds().contains(knowledgeId)) {
            throw new ApiException(ApiError.KNOWLEDGE_NOT_IN_BASE);
        }
    }

    /**
     * 验证知识点是否存在
     * @param knowledgeId 知识点ID
     * @return 知识点对象
     */
    public Knowledge validateKnowledge(String knowledgeId) {
        Knowledge knowledge = knowledgeRepository.findById(knowledgeId).orElse(null);
        if (knowledge == null) {
            throw new ApiException(ApiError.KNOWLEDGE_NOT_EXISTED);
        }
        return knowledge;
    }

    /**
     * 验证用户是否有编辑权限（仅限知识库创建者）
     * @param knowledgeBase 知识库对象
     */
    public void validateUserEditPermission(KnowledgeBase knowledgeBase) {
        Long userId = StpUtil.getLoginIdAsLong();
        if (!knowledgeBase.getCreatorId().equals(userId)) {
            throw new ApiException(ApiError.PERMISSION_DENIED);
        }
    }

    /**
     * 验证用户是否有权限访问该知识库
     * @param knowledgeBase 知识库对象
     */
    public void validateUserAccess(KnowledgeBase knowledgeBase) {
        Long userId = StpUtil.getLoginIdAsLong();
        // 如果是公开的知识库或用户是知识库的成员，允许访问
        if (knowledgeBase.getIsPublished() || knowledgeBase.getMembers().contains(userId)) {
            return;
        }
        throw new ApiException(ApiError.PERMISSION_DENIED);
    }
} 
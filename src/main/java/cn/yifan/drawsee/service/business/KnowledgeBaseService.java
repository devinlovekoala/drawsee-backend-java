package cn.yifan.drawsee.service.business;

import cn.dev33.satoken.stp.StpUtil;
import cn.yifan.drawsee.pojo.entity.KnowledgeBase;
import cn.yifan.drawsee.exception.ApiError;
import cn.yifan.drawsee.exception.ApiException;
import cn.yifan.drawsee.pojo.dto.CreateKnowledgeBaseDTO;
import cn.yifan.drawsee.pojo.dto.JoinKnowledgeBaseDTO;
import cn.yifan.drawsee.pojo.dto.UploadResourceDTO;
import cn.yifan.drawsee.pojo.entity.KnowledgeResource;
import cn.yifan.drawsee.pojo.vo.KnowledgeBaseVO;
import cn.yifan.drawsee.constant.KnowledgeResourceType;
import cn.yifan.drawsee.service.base.MinioService;
import cn.yifan.drawsee.service.base.RAGFlowClient;
import cn.yifan.drawsee.mapper.KnowledgeResourceMapper;
import cn.yifan.drawsee.mapper.KnowledgeBaseMapper;
import cn.yifan.drawsee.util.UUIDUtils;
import cn.yifan.drawsee.pojo.entity.Course;
import cn.yifan.drawsee.mapper.CourseMapper;
import lombok.extern.slf4j.Slf4j;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import cn.yifan.drawsee.pojo.dto.rag.RagCreateKnowledgeDTO;
import cn.yifan.drawsee.pojo.vo.rag.RagKnowledgeVO;
import cn.yifan.drawsee.config.RagFlowConfig;
import java.util.stream.Collectors;
import java.util.Objects;

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
@Slf4j
public class KnowledgeBaseService extends AbstractKnowledgeBaseService {
    
    private static final Logger logger = LoggerFactory.getLogger(KnowledgeBaseService.class);
    
    @Autowired
    private TeacherService teacherService;
    
    @Autowired
    private MinioService minioService;
    
    @Autowired
    private KnowledgeResourceMapper knowledgeResourceMapper;

    @Autowired
    private RagFlowService ragFlowService;
    
    @Autowired
    private RagFlowConfig ragFlowConfig;
    
    @Autowired
    private RAGFlowClient ragFlowClient;

    @Autowired
    private KnowledgeBaseMapper knowledgeBaseMapper;
    
    @Autowired
    private CourseMapper courseMapper;

    /**
     * 创建知识库
     * @param createKnowledgeBaseDTO 创建知识库DTO
     * @return 知识库ID
     */
    @Transactional(rollbackFor = Exception.class)
    public String createKnowledgeBase(CreateKnowledgeBaseDTO createKnowledgeBaseDTO) {
        // 验证用户是否为教师
        teacherService.validateTeacher();
        
        // 检查知识库名称是否已存在
        KnowledgeBase existKnowledgeBase = knowledgeBaseMapper.getByName(createKnowledgeBaseDTO.getName());
        if (existKnowledgeBase != null) {
            throw new ApiException(ApiError.KNOWLEDGE_BASE_HAD_EXISTED, "文件不能为空");
        }
        
        // 生成邀请码
        String invitationCode = generateInvitationCode();
        
        // 创建知识库
        KnowledgeBase knowledgeBase = new KnowledgeBase();
        knowledgeBase.setId(UUIDUtils.generateUUID());
        knowledgeBase.setName(createKnowledgeBaseDTO.getName());
        knowledgeBase.setDescription(createKnowledgeBaseDTO.getDescription());
        knowledgeBase.setSubject(createKnowledgeBaseDTO.getSubject());
        knowledgeBase.setInvitationCode(invitationCode);
        knowledgeBase.setCreatorId(StpUtil.getLoginIdAsLong());
        knowledgeBase.setCreatedAt(new Date());
        knowledgeBase.setUpdatedAt(new Date());
        knowledgeBase.setMembers(new ArrayList<>());
        knowledgeBase.getMembers().add(StpUtil.getLoginIdAsLong());
        knowledgeBase.setIsDeleted(false);
        
        knowledgeBaseMapper.insert(knowledgeBase);
        
        return knowledgeBase.getId();
    }
    
    /**
     * 管理员创建知识库 - 无需教师角色验证
     * @param createKnowledgeBaseDTO 创建知识库DTO
     * @param isPublished 是否发布
     * @return 知识库ID
     */
    @Transactional(rollbackFor = Exception.class)
    public String createKnowledgeBaseByAdmin(CreateKnowledgeBaseDTO createKnowledgeBaseDTO, boolean isPublished) {
        // 检查知识库名称是否已存在
        KnowledgeBase existKnowledgeBase = knowledgeBaseMapper.getByName(createKnowledgeBaseDTO.getName());
        if (existKnowledgeBase != null) {
            throw new ApiException(ApiError.KNOWLEDGE_BASE_HAD_EXISTED, "文件不能为空");
        }
        
        // 生成邀请码
        String invitationCode = generateInvitationCode();
        
        // 创建知识库
        KnowledgeBase knowledgeBase = new KnowledgeBase();
        knowledgeBase.setId(UUIDUtils.generateUUID());
        knowledgeBase.setName(createKnowledgeBaseDTO.getName());
        knowledgeBase.setDescription(createKnowledgeBaseDTO.getDescription());
        knowledgeBase.setSubject(createKnowledgeBaseDTO.getSubject());
        knowledgeBase.setInvitationCode(invitationCode);
        knowledgeBase.setCreatorId(StpUtil.getLoginIdAsLong());
        knowledgeBase.setCreatedAt(new Date());
        knowledgeBase.setUpdatedAt(new Date());
        knowledgeBase.setMembers(new ArrayList<>());
        knowledgeBase.getMembers().add(StpUtil.getLoginIdAsLong());
        knowledgeBase.setIsDeleted(false);
        knowledgeBase.setIsPublished(isPublished);
        
        knowledgeBaseMapper.insert(knowledgeBase);
        
        return knowledgeBase.getId();
    }
    
    /**
     * 加入知识库
     * @param joinKnowledgeBaseDTO 加入知识库DTO
     * @return 知识库ID
     */
    @Transactional(rollbackFor = Exception.class)
    public String joinKnowledgeBase(JoinKnowledgeBaseDTO joinKnowledgeBaseDTO) {
        // 检查邀请码是否存在
        KnowledgeBase knowledgeBase = knowledgeBaseMapper.getByInvitationCode(joinKnowledgeBaseDTO.getInvitationCode());
        if (knowledgeBase == null) {
            throw new ApiException(ApiError.INVALID_INVITATION_CODE, "文件不能为空");
        }
        
        Long userId = StpUtil.getLoginIdAsLong();
        
        // 检查用户是否已加入该知识库
        if (knowledgeBase.getMembers().contains(userId)) {
            throw new ApiException(ApiError.KNOWLEDGE_BASE_ALREADY_JOINED, "文件不能为空");
        }
        
        // 加入知识库
        knowledgeBase.getMembers().add(userId);
        knowledgeBase.setUpdatedAt(new Date());
        knowledgeBaseMapper.update(knowledgeBase);
        
        return knowledgeBase.getId();
    }
    
    /**
     * 获取我创建的知识库列表
     * @return 知识库列表
     */
    public List<KnowledgeBaseVO> getMyCreatedKnowledgeBases() {
        Long userId = StpUtil.getLoginIdAsLong();
        List<KnowledgeBase> knowledgeBases = knowledgeBaseMapper.getByCreatorId(userId, false);
        return convertToVOList(knowledgeBases);
    }
    
    /**
     * 获取我加入的知识库列表
     * @return 知识库列表
     */
    public List<KnowledgeBaseVO> getMyJoinedKnowledgeBases() {
        Long userId = StpUtil.getLoginIdAsLong();
        List<KnowledgeBase> knowledgeBases = knowledgeBaseMapper.getByMemberId(userId, false);
        return convertToVOList(knowledgeBases);
    }
    
    /**
     * 获取知识库详情
     * @param id 知识库ID
     * @return 知识库详情
     */
    public KnowledgeBaseVO getKnowledgeBaseDetail(String id) {
        KnowledgeBase knowledgeBase = knowledgeBaseMapper.getById(id);
        if (knowledgeBase == null) {
            throw new ApiException(ApiError.KNOWLEDGE_BASE_NOT_EXISTED, "文件不能为空");
        }
        
        // 验证用户是否有权访问此知识库
        validateUserAccess(knowledgeBase);
        
        return convertToVO(knowledgeBase);
    }
    
    /**
     * 添加B站资源到知识库
     * @param url B站URL
     * @param uploadResourceDTO 上传资源DTO
     * @return 资源URL
     */
    @Transactional(rollbackFor = Exception.class)
    public String addBilibiliResource(String url, UploadResourceDTO uploadResourceDTO) {
        logger.info("添加B站资源: knowledgeBaseId={}, url={}", uploadResourceDTO.getKnowledgeBaseId(), url);
        
        // 验证知识库是否存在
        KnowledgeBase knowledgeBase = knowledgeBaseMapper.getById(uploadResourceDTO.getKnowledgeBaseId());
        if (knowledgeBase == null) {
            logger.error("知识库不存在: {}", uploadResourceDTO.getKnowledgeBaseId());
            throw new ApiException(ApiError.KNOWLEDGE_BASE_NOT_EXISTED, "文件不能为空");
        }
        
        // 验证URL是否为B站链接
        if (!url.contains("bilibili.com") && !url.contains("b23.tv")) {
            logger.error("非法的B站链接: {}", url);
            throw new ApiException(ApiError.INVALID_BILIBILI_URL, "文件不能为空");
        }
        
        try {
            // 创建资源记录
            KnowledgeResource resource = new KnowledgeResource();
            resource.setId(UUIDUtils.generateUUID());
            resource.setKnowledgeBaseId(knowledgeBase.getId());
            resource.setResourceType(KnowledgeResourceType.BILIBILI);
            resource.setTitle(uploadResourceDTO.getTitle());
            resource.setDescription(uploadResourceDTO.getDescription());
            resource.setUrl(url);
            resource.setCreatorId(StpUtil.getLoginIdAsLong());
            resource.setCreatedAt(new Date());
            resource.setUpdatedAt(new Date());
            resource.setIsDeleted(false);
            
            // 保存资源记录
            knowledgeResourceMapper.insert(resource);
            logger.info("B站资源添加成功, knowledgeBaseId={}", knowledgeBase.getId());
            
            return url;
        } catch (Exception e) {
            logger.error("B站资源添加失败", e);
            throw new ApiException(ApiError.RESOURCE_ADD_FAILED, "文件不能为空");
        }
    }
    
    /**
     * 生成邀请码
     * @return 邀请码
     */
    private String generateInvitationCode() {
        String chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
        Random random = new SecureRandom();
        StringBuilder sb = new StringBuilder(6);
        for (int i = 0; i < 6; i++) {
            sb.append(chars.charAt(random.nextInt(chars.length())));
        }
        return sb.toString();
    }
    
    /**
     * 将知识库列表转换为前端VO列表
     * @param knowledgeBases 知识库列表
     * @return 前端VO列表
     */
    private List<KnowledgeBaseVO> convertToVOList(List<KnowledgeBase> knowledgeBases) {
        if (knowledgeBases == null) {
            return new ArrayList<>();
        }
        
        return knowledgeBases.stream()
                .filter(kb -> !kb.getIsDeleted())
                .map(this::convertToVO)
                .collect(Collectors.toList());
    }
    
    /**
     * 将知识库实体转换为前端VO
     * @param knowledgeBase 知识库实体
     * @return 前端VO
     */
    public KnowledgeBaseVO convertToVO(KnowledgeBase knowledgeBase) {
        if (knowledgeBase == null) {
            return null;
        }
        
        KnowledgeBaseVO vo = new KnowledgeBaseVO();
        BeanUtils.copyProperties(knowledgeBase, vo);
        
        // 手动处理members字段，确保类型转换正确
        if (knowledgeBase.getMembers() != null) {
            List<Long> longMembers = new ArrayList<>();
            for (Object member : knowledgeBase.getMembers()) {
                if (member != null) {
                    // 安全转换为Long
                    longMembers.add(Long.valueOf(member.toString()));
                }
            }
            vo.setMembers(longMembers);
        }
        
        return vo;
    }

    /**
     * 获取所有知识库列表（管理员使用）
     * @return 知识库列表
     */
    public List<KnowledgeBaseVO> getAllKnowledgeBases() {
        List<KnowledgeBase> knowledgeBases = knowledgeBaseMapper.getAll(false);
        return convertToVOList(knowledgeBases);
    }

    /**
     * 删除知识库
     * @param knowledgeBaseId 知识库ID
     */
    @Transactional(rollbackFor = Exception.class)
    public void deleteKnowledgeBase(String knowledgeBaseId) {
        KnowledgeBase knowledgeBase = validateKnowledgeBase(knowledgeBaseId);
        validateUserEditPermission(knowledgeBase);
        
        // 逻辑删除
        knowledgeBase.setIsDeleted(true);
        knowledgeBase.setUpdatedAt(new Date());
        knowledgeBaseMapper.update(knowledgeBase);
        
        // 如果是RAG知识库，也删除RAGFlow中的知识库
        if (knowledgeBase.getRagEnabled() && knowledgeBase.getRagKnowledgeId() != null) {
            try {
                ragFlowService.deleteKnowledgeBase(knowledgeBase.getRagKnowledgeId());
            } catch (Exception e) {
                logger.error("删除RAGFlow知识库失败: {}", e.getMessage());
            }
        }
    }
    
    /**
     * 获取当前用户的所有知识库（包括创建的和加入的）
     * @return 知识库列表
     */
    public List<KnowledgeBaseVO> getKnowledgeBasesForCurrentUser() {
        Long userId = StpUtil.getLoginIdAsLong();
        
        // 获取创建的知识库
        List<KnowledgeBase> createdKnowledgeBases = knowledgeBaseMapper.getByCreatorId(userId, false);
        
        // 获取加入的知识库
        List<KnowledgeBase> joinedKnowledgeBases = knowledgeBaseMapper.getByMemberId(userId, false);
        
        // 合并去重
        Map<String, KnowledgeBase> knowledgeBaseMap = new HashMap<>();
        
        if (createdKnowledgeBases != null) {
        for (KnowledgeBase kb : createdKnowledgeBases) {
            if (!kb.getIsDeleted()) {
                knowledgeBaseMap.put(kb.getId(), kb);
                }
            }
        }
        
        if (joinedKnowledgeBases != null) {
        for (KnowledgeBase kb : joinedKnowledgeBases) {
            if (!kb.getIsDeleted() && !knowledgeBaseMap.containsKey(kb.getId())) {
                knowledgeBaseMap.put(kb.getId(), kb);
            }
        }
        }
        
        // 转换为VO列表
        return knowledgeBaseMap.values().stream()
                .map(this::convertToVO)
                .collect(Collectors.toList());
    }

    /**
     * 创建支持RAG的知识库
     * @param createKnowledgeBaseDTO 创建知识库DTO
     * @return 知识库ID
     */
    @Transactional(rollbackFor = Exception.class)
    public String createRagEnabledKnowledgeBase(CreateKnowledgeBaseDTO createKnowledgeBaseDTO) {
        // 验证用户是否为教师
        teacherService.validateTeacher();
        
        // 检查知识库名称是否已存在
        KnowledgeBase existKnowledgeBase = knowledgeBaseMapper.getByName(createKnowledgeBaseDTO.getName());
        if (existKnowledgeBase != null) {
            throw new ApiException(ApiError.KNOWLEDGE_BASE_HAD_EXISTED, "文件不能为空");
        }
        
        // 检查RAGFlow服务是否可用
        if (!ragFlowConfig.isEnabled()) {
            throw new ApiException(ApiError.RAG_SERVICE_DISABLED, "文件不能为空");
        }
        
        // 生成邀请码
        String invitationCode = generateInvitationCode();
        
        // 创建知识库
        KnowledgeBase knowledgeBase = new KnowledgeBase();
        knowledgeBase.setId(UUIDUtils.generateUUID());
        knowledgeBase.setName(createKnowledgeBaseDTO.getName());
        knowledgeBase.setDescription(createKnowledgeBaseDTO.getDescription());
        knowledgeBase.setSubject(createKnowledgeBaseDTO.getSubject());
        knowledgeBase.setInvitationCode(invitationCode);
        knowledgeBase.setCreatorId(StpUtil.getLoginIdAsLong());
        knowledgeBase.setCreatedAt(new Date());
        knowledgeBase.setUpdatedAt(new Date());
        knowledgeBase.setMembers(new ArrayList<>());
        knowledgeBase.getMembers().add(StpUtil.getLoginIdAsLong());
        knowledgeBase.setIsDeleted(false);
        knowledgeBase.setRagEnabled(true);
        knowledgeBase.setRagDocumentCount(0);
        knowledgeBase.setSyncToRagFlow(true);
        knowledgeBase.setRagSyncStatus("pending");
        
        // 创建RAGFlow知识库
        try {
            RagCreateKnowledgeDTO ragDto = new RagCreateKnowledgeDTO();
            ragDto.setName(createKnowledgeBaseDTO.getName());
            ragDto.setDescription(createKnowledgeBaseDTO.getDescription());
            
            RagKnowledgeVO ragKnowledge = ragFlowService.createKnowledgeBase(ragDto);
            if (ragKnowledge != null) {
                knowledgeBase.setRagKnowledgeId(ragKnowledge.getId());
                knowledgeBase.setRagSyncStatus("synced");
                knowledgeBase.setLastSyncTime(new Date());
                logger.info("成功创建RAGFlow知识库: {}", ragKnowledge.getId());
            } else {
                knowledgeBase.setRagSyncStatus("failed");
                logger.error("创建RAGFlow知识库失败");
            }
        } catch (Exception e) {
            knowledgeBase.setRagSyncStatus("failed");
            logger.error("创建RAGFlow知识库异常: {}", e.getMessage(), e);
        }
        
        // 保存知识库
        knowledgeBaseMapper.insert(knowledgeBase);
        
        return knowledgeBase.getId();
    }
    
    /**
     * 更新知识库
     * @param createKnowledgeBaseDTO 知识库更新DTO
     * @return 更新后的知识库ID
     */
    @Transactional(rollbackFor = Exception.class)
    public String updateKnowledgeBase(CreateKnowledgeBaseDTO createKnowledgeBaseDTO) {
        // 修改这里，假设createKnowledgeBaseDTO没有getId()方法，改用从URL或参数中获取ID
        String knowledgeBaseId = createKnowledgeBaseDTO.getId(); // 如果DTO中有此方法，保留
        // 或者添加一个参数来传递ID
        // String knowledgeBaseId = knowledgeBaseId;
        
        // 验证知识库存在
        KnowledgeBase knowledgeBase = validateKnowledgeBase(knowledgeBaseId);
        
        // 验证用户有权限编辑
        validateUserEditPermission(knowledgeBase);
        
        // 验证新名称不与其他知识库重复（排除自身）
        if (!knowledgeBase.getName().equals(createKnowledgeBaseDTO.getName())) {
            KnowledgeBase existKnowledgeBase = knowledgeBaseMapper.getByName(createKnowledgeBaseDTO.getName());
            if (existKnowledgeBase != null && !existKnowledgeBase.getId().equals(knowledgeBase.getId())) {
                throw new ApiException(ApiError.KNOWLEDGE_BASE_HAD_EXISTED, "文件不能为空");
            }
        }
        
        // 更新知识库信息
        knowledgeBase.setName(createKnowledgeBaseDTO.getName());
        knowledgeBase.setDescription(createKnowledgeBaseDTO.getDescription());
        knowledgeBase.setSubject(createKnowledgeBaseDTO.getSubject());
            knowledgeBase.setUpdatedAt(new Date());
        
        // 保存到数据库
        knowledgeBaseMapper.update(knowledgeBase);
        
        // 如果启用了RAG，同步更新RAGFlow知识库
        if (knowledgeBase.getRagEnabled() && knowledgeBase.getRagKnowledgeId() != null) {
            try {
                // 使用RAGFlowClient更新知识库
                boolean updateResult = ragFlowClient.updateKnowledgeBase(
                    knowledgeBase.getRagKnowledgeId(),
                    createKnowledgeBaseDTO.getName(),
                    createKnowledgeBaseDTO.getDescription()
                );
                
                if (!updateResult) {
                    logger.warn("同步更新RAGFlow知识库失败，但本地知识库已更新: {}", knowledgeBase.getId());
                }
            } catch (Exception e) {
                logger.error("同步更新RAGFlow知识库时出错: {}", e.getMessage(), e);
                // 不阻止更新，继续返回成功
            }
        }
        
        return knowledgeBase.getId();
    }

    /**
     * 为课程创建知识库
     * @param courseId 课程ID
     * @param createKnowledgeBaseDTO 创建知识库DTO
     * @return 知识库ID
     */
    @Transactional(rollbackFor = Exception.class)
    public String createKnowledgeBaseForCourse(String courseId, CreateKnowledgeBaseDTO createKnowledgeBaseDTO) {
        // 验证用户是否为教师
        teacherService.validateTeacher();
        
        // 检查知识库名称是否已存在
        KnowledgeBase existKnowledgeBase = knowledgeBaseMapper.getByName(createKnowledgeBaseDTO.getName());
        if (existKnowledgeBase != null) {
            throw new ApiException(ApiError.KNOWLEDGE_BASE_HAD_EXISTED, "文件不能为空");
        }
        
        // 生成邀请码
        String invitationCode = generateInvitationCode();
        
        // 创建知识库
        KnowledgeBase knowledgeBase = new KnowledgeBase();
        knowledgeBase.setId(UUIDUtils.generateUUID());
        knowledgeBase.setName(createKnowledgeBaseDTO.getName());
        knowledgeBase.setDescription(createKnowledgeBaseDTO.getDescription());
        knowledgeBase.setSubject(createKnowledgeBaseDTO.getSubject());
        knowledgeBase.setInvitationCode(invitationCode);
        knowledgeBase.setCreatorId(StpUtil.getLoginIdAsLong());
        knowledgeBase.setCreatedAt(new Date());
        knowledgeBase.setUpdatedAt(new Date());
        knowledgeBase.setMembers(new ArrayList<>());
        knowledgeBase.getMembers().add(StpUtil.getLoginIdAsLong());
        knowledgeBase.setIsDeleted(false);
        
        knowledgeBaseMapper.insert(knowledgeBase);
        
        // 将知识库关联到课程
        Course course = courseMapper.getById(courseId);
        if (course == null) {
            throw new ApiException(ApiError.COURSE_NOT_EXISTED, "文件不能为空");
        }
        
        // 检查当前用户是否有权限编辑课程
        Long userId = StpUtil.getLoginIdAsLong();
        if (!course.getCreatorId().equals(userId)) {
            throw new ApiException(ApiError.PERMISSION_DENIED, "文件不能为空");
        }
        
        // 将知识库ID添加到课程的知识库列表中
        if (course.getKnowledgeBaseIds() == null) {
            course.setKnowledgeBaseIds(new ArrayList<>());
        }
        course.getKnowledgeBaseIds().add(knowledgeBase.getId());
        course.setUpdatedAt(new Date());
        
        // 更新课程
        courseMapper.update(course);
        
        return knowledgeBase.getId();
    }
} 
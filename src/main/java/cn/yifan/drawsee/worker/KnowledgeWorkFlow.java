package cn.yifan.drawsee.worker;

import cn.dev33.satoken.stp.StpUtil;
import cn.yifan.drawsee.constant.NodeTitle;
import cn.yifan.drawsee.constant.NodeType;
import cn.yifan.drawsee.constant.RedisKey;
import cn.yifan.drawsee.mapper.AiTaskMapper;
import cn.yifan.drawsee.mapper.ClassMapper;
import cn.yifan.drawsee.mapper.ConversationMapper;
import cn.yifan.drawsee.mapper.NodeMapper;
import cn.yifan.drawsee.mapper.UserMapper;
import cn.yifan.drawsee.pojo.XYPosition;
import cn.yifan.drawsee.pojo.entity.Node;
import cn.yifan.drawsee.pojo.mongo.Course;
import cn.yifan.drawsee.pojo.mongo.Knowledge;
import cn.yifan.drawsee.pojo.mongo.KnowledgeBase;
import cn.yifan.drawsee.pojo.rabbit.AiTaskMessage;
import cn.yifan.drawsee.repository.CourseRepository;
import cn.yifan.drawsee.repository.KnowledgeBaseRepository;
import cn.yifan.drawsee.repository.KnowledgeRepository;
import cn.yifan.drawsee.service.base.AiService;
import cn.yifan.drawsee.service.base.StreamAiService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RList;
import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.time.Duration;

/**
 * @FileName KnowledgeWorkFlow
 * @Description
 * @Author yifan
 * @date 2025-03-09 13:35
 **/

@Slf4j
@Service
public class KnowledgeWorkFlow extends WorkFlow {

    private final CourseRepository courseRepository;
    private final KnowledgeBaseRepository knowledgeBaseRepository;
    private final ClassMapper classMapper;

    public KnowledgeWorkFlow(
        UserMapper userMapper,
        AiService aiService,
        StreamAiService streamAiService,
        RedissonClient redissonClient,
        KnowledgeRepository knowledgeRepository,
        NodeMapper nodeMapper,
        ConversationMapper conversationMapper,
        AiTaskMapper aiTaskMapper,
        ObjectMapper objectMapper,
        CourseRepository courseRepository,
        KnowledgeBaseRepository knowledgeBaseRepository,
        ClassMapper classMapper
    ) {
        super(userMapper, aiService, streamAiService, redissonClient, knowledgeRepository, nodeMapper, conversationMapper, aiTaskMapper, objectMapper);
        this.courseRepository = courseRepository;
        this.knowledgeBaseRepository = knowledgeBaseRepository;
        this.classMapper = classMapper;
        
        // 全局默认知识点列表 - 这里将保留用于通用模式
        refreshGlobalKnowledgePoints();
    }
    
    /**
     * 刷新全局知识点列表
     */
    private void refreshGlobalKnowledgePoints() {
        List<Knowledge> knowledgeList = knowledgeRepository.findAll();
        List<String> knowledgePoints = knowledgeList.stream().map(Knowledge::getName).toList();
        RList<String> rList = redissonClient.getList(RedisKey.CACHE_PREFIX + "knowledge-points");
        rList.clear();
        rList.addAll(knowledgePoints);
        // 清除过期时间，设置为永不过期
        rList.clearExpire();
    }

    @Override
    public void createOtherNodesOrUpdateNodeData(WorkContext workContext) throws JsonProcessingException {
        AiTaskMessage aiTaskMessage = workContext.getAiTaskMessage();
        Node streamNode = workContext.getStreamNode();
        
        List<String> knowledgePoints;
        String classId = aiTaskMessage.getClassId();
        
        // 如果指定了班级ID，则优先使用该班级对应的知识库
        if (classId != null && !classId.isEmpty()) {
            log.info("使用指定班级ID获取知识点: classId={}", classId);
            knowledgePoints = getClassRelatedKnowledgePoints(classId);
            
            // 如果指定班级没有关联知识库或知识点为空，则退化为使用用户相关知识点
            if (knowledgePoints.isEmpty()) {
                log.info("指定班级无关联知识点，退化为用户相关知识点: classId={}", classId);
                knowledgePoints = getUserRelatedKnowledgePoints(aiTaskMessage.getUserId());
            }
        } else {
            // 获取知识点列表，优先从用户加入的课程和知识库中获取
            knowledgePoints = getUserRelatedKnowledgePoints(aiTaskMessage.getUserId());
        }
        
        // 如果用户没有加入任何课程或知识库，则使用全局知识点列表
        if (knowledgePoints.isEmpty()) {
            log.info("无法获取到任何关联知识点，使用全局知识点列表");
            RList<String> rList = redissonClient.getList(RedisKey.CACHE_PREFIX + "knowledge-points");
            knowledgePoints = rList.stream().toList();
        }
        
        List<String> relatedKnowledgePoints = aiService.getRelatedKnowledgePoints(knowledgePoints, aiTaskMessage.getPrompt(), workContext.getTokens());

        if (relatedKnowledgePoints == null) return;
        if (relatedKnowledgePoints.isEmpty()) return;

        // 创建知识点节点
        for (String knowledgePoint : relatedKnowledgePoints) {
            Map<String, Object> knowledgeHeadNodeData = new ConcurrentHashMap<>();
            knowledgeHeadNodeData.put("title", NodeTitle.KNOWLEDGE_HEAD);
            knowledgeHeadNodeData.put("text", knowledgePoint);
            Node knowledgeHeadNode = new Node(
                NodeType.KNOWLEDGE_HEAD,
                objectMapper.writeValueAsString(knowledgeHeadNodeData),
                objectMapper.writeValueAsString(XYPosition.origin()),
                streamNode.getId(),
                aiTaskMessage.getUserId(),
                aiTaskMessage.getConvId(),
                true
            );
            insertAndPublishNoneStreamNode(workContext, knowledgeHeadNode, knowledgeHeadNodeData);
        }
    }
    
    /**
     * 获取用户相关的知识点列表
     * @param userId 用户ID
     * @return 知识点列表
     */
    private List<String> getUserRelatedKnowledgePoints(Long userId) {
        Set<String> knowledgePoints = new HashSet<>();
        
        // 从用户加入的课程中获取知识库
        List<Course> userCourses = courseRepository.findByStudentIdsContainingAndIsDeletedFalse(userId);
        for (Course course : userCourses) {
            List<String> knowledgeBaseIds = course.getKnowledgeBaseIds();
            if (knowledgeBaseIds != null && !knowledgeBaseIds.isEmpty()) {
                for (String knowledgeBaseId : knowledgeBaseIds) {
                    KnowledgeBase knowledgeBase = knowledgeBaseRepository.findById(knowledgeBaseId).orElse(null);
                    if (knowledgeBase != null && !knowledgeBase.getIsDeleted()) {
                        // 只使用已发布的知识库，或者用户是知识库创建者的知识库
                        if (knowledgeBase.getIsPublished() || knowledgeBase.getCreatorId().equals(userId)) {
                            addKnowledgePointsFromBase(knowledgeBase, knowledgePoints);
                        } else {
                            log.info("用户{}跳过未发布的知识库: {}", userId, knowledgeBase.getName());
                        }
                    }
                }
            }
        }
        
        // 从用户直接加入的知识库中获取知识点
        List<KnowledgeBase> userKnowledgeBases = knowledgeBaseRepository.findByMembersContaining(userId);
        for (KnowledgeBase knowledgeBase : userKnowledgeBases) {
            if (!knowledgeBase.getIsDeleted()) {
                // 只使用已发布的知识库，或者用户是知识库创建者的知识库
                if (knowledgeBase.getIsPublished() || knowledgeBase.getCreatorId().equals(userId)) {
                    addKnowledgePointsFromBase(knowledgeBase, knowledgePoints);
                } else {
                    log.info("用户{}跳过未发布的知识库: {}", userId, knowledgeBase.getName());
                }
            }
        }
        
        // 添加所有公开发布的知识库中的知识点
        List<KnowledgeBase> publishedBases = knowledgeBaseRepository.findByIsPublishedTrue();
        for (KnowledgeBase knowledgeBase : publishedBases) {
            if (!knowledgeBase.getIsDeleted()) {
                addKnowledgePointsFromBase(knowledgeBase, knowledgePoints);
            }
        }
        
        return new ArrayList<>(knowledgePoints);
    }
    
    /**
     * 从知识库中添加知识点到集合
     * @param knowledgeBase 知识库
     * @param knowledgePoints 知识点集合
     */
    private void addKnowledgePointsFromBase(KnowledgeBase knowledgeBase, Set<String> knowledgePoints) {
        List<String> knowledgeIds = knowledgeBase.getKnowledgeIds();
        if (knowledgeIds != null && !knowledgeIds.isEmpty()) {
            for (String knowledgeId : knowledgeIds) {
                Knowledge knowledge = knowledgeRepository.findById(knowledgeId).orElse(null);
                if (knowledge != null) {
                    knowledgePoints.add(knowledge.getName());
                }
            }
        }
    }
    
    /**
     * 获取班级相关的知识点列表
     * @param classId 班级ID
     * @return 知识点列表
     */
    private List<String> getClassRelatedKnowledgePoints(String classId) {
        Set<String> knowledgePoints = new HashSet<>();
        
        // 通过班级ID获取班级信息
        cn.yifan.drawsee.pojo.entity.Class clazz = classMapper.getById(Long.parseLong(classId));
        if (clazz == null || clazz.getIsDeleted()) {
            log.warn("班级不存在或已删除: classId={}", classId);
            return new ArrayList<>();
        }
        
        // 获取该班级对应的课程
        Course course = courseRepository.findByClassCodeAndIsDeletedFalse(clazz.getClassCode());
        if (course == null) {
            log.warn("没有找到班级对应的课程: classCode={}", clazz.getClassCode());
            return new ArrayList<>();
        }
        
        // 获取课程关联的知识库
        List<String> knowledgeBaseIds = course.getKnowledgeBaseIds();
        if (knowledgeBaseIds == null || knowledgeBaseIds.isEmpty()) {
            log.info("课程没有关联知识库: courseId={}, classCode={}", course.getId(), course.getClassCode());
            return new ArrayList<>();
        }
        
        // 从课程关联的知识库中获取知识点
        for (String knowledgeBaseId : knowledgeBaseIds) {
            KnowledgeBase knowledgeBase = knowledgeBaseRepository.findById(knowledgeBaseId).orElse(null);
            if (knowledgeBase != null && !knowledgeBase.getIsDeleted() && knowledgeBase.getIsPublished()) {
                addKnowledgePointsFromBase(knowledgeBase, knowledgePoints);
            }
        }
        
        if (knowledgePoints.isEmpty()) {
            log.info("班级相关知识库中未找到知识点: classId={}", classId);
        } else {
            log.info("从班级相关知识库中找到{}\u4e2a知识点: classId={}", knowledgePoints.size(), classId);
        }
        
        return new ArrayList<>(knowledgePoints);
    }
}

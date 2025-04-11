package cn.yifan.drawsee.worker;

import cn.yifan.drawsee.constant.NodeTitle;
import cn.yifan.drawsee.constant.*;
import cn.yifan.drawsee.mapper.AiTaskMapper;
import cn.yifan.drawsee.mapper.ClassMapper;
import cn.yifan.drawsee.mapper.ClassMemberMapper;
import cn.yifan.drawsee.mapper.ConversationMapper;
import cn.yifan.drawsee.mapper.NodeMapper;
import cn.yifan.drawsee.mapper.UserMapper;
import cn.yifan.drawsee.pojo.XYPosition;
import cn.yifan.drawsee.pojo.entity.ClassMember;
import cn.yifan.drawsee.pojo.entity.Node;
import cn.yifan.drawsee.pojo.mongo.Course;
import cn.yifan.drawsee.pojo.mongo.Knowledge;
import cn.yifan.drawsee.pojo.mongo.KnowledgeBase;
import cn.yifan.drawsee.pojo.mongo.KnowledgeResource;
import cn.yifan.drawsee.pojo.rabbit.AiTaskMessage;
import cn.yifan.drawsee.repository.CourseRepository;
import cn.yifan.drawsee.repository.KnowledgeBaseRepository;
import cn.yifan.drawsee.repository.KnowledgeRepository;
import cn.yifan.drawsee.service.base.AiService;
import cn.yifan.drawsee.service.base.StreamAiService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.model.StreamingResponseHandler;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * @FileName KnowledgeDetailWorkFlow
 * @Description
 * @Author yifan
 * @date 2025-03-09 13:49
 **/

@Slf4j
@Service
public class KnowledgeDetailWorkFlow extends WorkFlow {

    private final KnowledgeBaseRepository knowledgeBaseRepository;
    private final ClassMemberMapper classMemberMapper;
    private final CourseRepository courseRepository;
    private final ClassMapper classMapper;

    public KnowledgeDetailWorkFlow(
            UserMapper userMapper,
            AiService aiService,
            StreamAiService streamAiService,
            RedissonClient redissonClient,
            KnowledgeRepository knowledgeRepository,
            NodeMapper nodeMapper,
            ConversationMapper conversationMapper,
            AiTaskMapper aiTaskMapper,
            ObjectMapper objectMapper,
            KnowledgeBaseRepository knowledgeBaseRepository,
            ClassMemberMapper classMemberMapper,
            CourseRepository courseRepository,
            ClassMapper classMapper
    ) {
        super(userMapper, aiService, streamAiService, redissonClient, knowledgeRepository, nodeMapper, conversationMapper, aiTaskMapper, objectMapper);
        this.knowledgeBaseRepository = knowledgeBaseRepository;
        this.classMemberMapper = classMemberMapper;
        this.courseRepository = courseRepository;
        this.classMapper = classMapper;
    }

    @Override
    public Boolean validateAndInit(WorkContext workContext) {
        Boolean isValid = super.validateAndInit(workContext);
        if (!isValid) return false;
        Node parentNode = workContext.getParentNode();
        AiTaskMessage aiTaskMessage = workContext.getAiTaskMessage();
        // 校验父节点类型是否正确
        if (!parentNode.getType().equals(NodeType.KNOWLEDGE_HEAD)) {
            log.error("父节点不是知识点头节点, taskMessage: {}", aiTaskMessage);
            return false;
        }
        return true;
    }

    @Override
    public void createInitNodes(WorkContext workContext) throws JsonProcessingException {
        Node parentNode = workContext.getParentNode();
        AiTaskMessage aiTaskMessage = workContext.getAiTaskMessage();

        // 创建knowledge-detail节点
        Map<String, Object> knowledgeDetailNodeData = new ConcurrentHashMap<>();
        knowledgeDetailNodeData.put("title", NodeTitle.KNOWLEDGE_DETAIL);
        knowledgeDetailNodeData.put("text", "");
        Map<String, Object> knowledgeDetailMedia = new ConcurrentHashMap<>();
        knowledgeDetailMedia.put("animationObjectNames", new ArrayList<>());
        knowledgeDetailMedia.put("bilibiliUrls", new ArrayList<>());
        knowledgeDetailMedia.put("wordDocUrls", new ArrayList<>());
        knowledgeDetailMedia.put("pdfDocUrls", new ArrayList<>());
        knowledgeDetailNodeData.put("media", knowledgeDetailMedia);

        Node knowledgeDetailNode = new Node(
            NodeType.KNOWLEDGE_DETAIL,
            objectMapper.writeValueAsString(knowledgeDetailNodeData),
            objectMapper.writeValueAsString(XYPosition.origin()),
            parentNode.getId(),
            aiTaskMessage.getUserId(),
            aiTaskMessage.getConvId(),
            true
        );
        insertAndPublishStreamNode(workContext, knowledgeDetailNode, knowledgeDetailNodeData);
    }

    @Override
    public void streamChat(WorkContext workContext, StreamingResponseHandler<AiMessage> handler) throws JsonProcessingException {
        Node parentNode = workContext.getParentNode();
        LinkedList<ChatMessage> history = workContext.getHistory();
        TypeReference<Map<String, Object>> parentNodeDataTypeReference = new TypeReference<>() {};
        Map<String, Object> parentNodeData = objectMapper.readValue(parentNode.getData(), parentNodeDataTypeReference);
        String knowledgePoint = (String) parentNodeData.get("text");

        // 保持原有实现，不添加增强逻辑
        streamAiService.knowledgeDetailChat(history, knowledgePoint, handler);
    }

    @Override
    public void createOtherNodesOrUpdateNodeData(WorkContext workContext) throws JsonProcessingException {
        Node parentNode = workContext.getParentNode();
        Node streamNode = workContext.getStreamNode();
        TypeReference<Map<String, Object>> parentNodeDataTypeReference = new TypeReference<>() {};
        Map<String, Object> parentNodeData = objectMapper.readValue(parentNode.getData(), parentNodeDataTypeReference);
        String knowledgePoint = (String) parentNodeData.get("text");
        AiTaskMessage aiTaskMessage = workContext.getAiTaskMessage();
        Long userId = aiTaskMessage.getUserId();

        // 使用新的查找方法，从用户相关的班级课程知识库中查找知识点
        Knowledge knowledge = findKnowledgeInUserBases(userId, knowledgePoint);
        
        if (knowledge == null) {
            log.info("未找到知识点: knowledgePoint={}, userId={}", knowledgePoint, userId);
            return;
        }
        
        log.info("成功找到知识点: knowledgePoint={}, knowledgeId={}", knowledgePoint, knowledge.getId());
        List<KnowledgeResource> resources = knowledge.getResources();
        if (resources == null || resources.isEmpty()) {
            log.info("知识点没有关联资源: knowledgePoint={}", knowledgePoint);
            return;
        }

        // 过滤获取各类型资源
        List<String> animationObjectNames = resources.stream()
                .filter(knowledgeResource -> knowledgeResource.getType().equals(KnowledgeResourceType.ANIMATION))
                .map(KnowledgeResource::getValue).toList();

        List<String> bilibiliUrls = resources.stream()
                .filter(knowledgeResource -> knowledgeResource.getType().equals(KnowledgeResourceType.BILIBILI))
                .map(KnowledgeResource::getValue).toList();
                
        List<String> wordDocUrls = resources.stream()
                .filter(knowledgeResource -> knowledgeResource.getType().equals(KnowledgeResourceType.WORD))
                .map(KnowledgeResource::getValue).toList();
                
        List<String> pdfDocUrls = resources.stream()
                .filter(knowledgeResource -> knowledgeResource.getType().equals(KnowledgeResourceType.PDF))
                .map(KnowledgeResource::getValue).toList();

        // 创建各类型resource节点
        if (!animationObjectNames.isEmpty()) {
            Map<String, Object> animationResourceNodeData = new ConcurrentHashMap<>();
            animationResourceNodeData.put("title", NodeTitle.ANIMATION);
            animationResourceNodeData.put("subtype", NodeSubType.ANIMATION);
            animationResourceNodeData.put("objectNames", animationObjectNames);
            Node animationResourceNode = new Node(
                NodeType.RESOURCE,
                objectMapper.writeValueAsString(animationResourceNodeData),
                objectMapper.writeValueAsString(XYPosition.origin()),
                streamNode.getId(),
                aiTaskMessage.getUserId(),
                aiTaskMessage.getConvId(),
                true
            );
            insertAndPublishNoneStreamNode(workContext, animationResourceNode, animationResourceNodeData);
        }
        
        if (!bilibiliUrls.isEmpty()) {
            Map<String, Object> bilibiliResourceNodeData = new ConcurrentHashMap<>();
            bilibiliResourceNodeData.put("title", NodeTitle.BILIBILI);
            bilibiliResourceNodeData.put("subtype", NodeSubType.BILIBILI);
            bilibiliResourceNodeData.put("urls", bilibiliUrls);
            Node bilibiliResourceNode = new Node(
                NodeType.RESOURCE,
                objectMapper.writeValueAsString(bilibiliResourceNodeData),
                objectMapper.writeValueAsString(XYPosition.origin()),
                streamNode.getId(),
                aiTaskMessage.getUserId(),
                aiTaskMessage.getConvId(),
                true
            );
            insertAndPublishNoneStreamNode(workContext, bilibiliResourceNode, bilibiliResourceNodeData);
        }
        
        // 添加Word文档资源节点
        if (!wordDocUrls.isEmpty()) {
            Map<String, Object> wordResourceNodeData = new ConcurrentHashMap<>();
            wordResourceNodeData.put("title", NodeTitle.WORD);
            wordResourceNodeData.put("subtype", NodeSubType.WORD);
            wordResourceNodeData.put("urls", wordDocUrls);
            Node wordResourceNode = new Node(
                NodeType.RESOURCE,
                objectMapper.writeValueAsString(wordResourceNodeData),
                objectMapper.writeValueAsString(XYPosition.origin()),
                streamNode.getId(),
                aiTaskMessage.getUserId(),
                aiTaskMessage.getConvId(),
                true
            );
            insertAndPublishNoneStreamNode(workContext, wordResourceNode, wordResourceNodeData);
        }
        
        // 添加PDF文档资源节点
        if (!pdfDocUrls.isEmpty()) {
            Map<String, Object> pdfResourceNodeData = new ConcurrentHashMap<>();
            pdfResourceNodeData.put("title", NodeTitle.PDF);
            pdfResourceNodeData.put("subtype", NodeSubType.PDF);
            pdfResourceNodeData.put("urls", pdfDocUrls);
            Node pdfResourceNode = new Node(
                NodeType.RESOURCE,
                objectMapper.writeValueAsString(pdfResourceNodeData),
                objectMapper.writeValueAsString(XYPosition.origin()),
                streamNode.getId(),
                aiTaskMessage.getUserId(),
                aiTaskMessage.getConvId(),
                true
            );
            insertAndPublishNoneStreamNode(workContext, pdfResourceNode, pdfResourceNodeData);
        }
    }
    
    /**
     * 在用户相关的知识库中查找知识点
     * @param userId 用户ID
     * @param knowledgePointName 知识点名称
     * @return 知识点实体
     */
    private Knowledge findKnowledgeInUserBases(Long userId, String knowledgePointName) {
        // 1. 查找用户所属的班级
        List<ClassMember> userClassMembers = classMemberMapper.getByUserId(userId);
        if (userClassMembers == null || userClassMembers.isEmpty()) {
            log.info("用户未加入任何班级: userId={}", userId);
            return findKnowledgeInPublishedBases(knowledgePointName);
        }
        
        // 2. 查找班级对应的课程和知识库
        Knowledge knowledge = null;
        for (ClassMember classMember : userClassMembers) {
            if (classMember.getIsDeleted()) continue;
            
            // 2.1 通过班级ID查找班级信息
            cn.yifan.drawsee.pojo.entity.Class clazz = classMapper.getById(classMember.getClassId());
            if (clazz == null || clazz.getIsDeleted()) continue;
            
            Course course = courseRepository.findByClassCodeAndIsDeletedFalse(clazz.getClassCode());
            if (course == null || course.getIsDeleted()) continue;
            
            // 2.2 获取课程关联的知识库列表
            List<String> knowledgeBaseIds = course.getKnowledgeBaseIds();
            if (knowledgeBaseIds == null || knowledgeBaseIds.isEmpty()) {
                log.info("课程未关联知识库: courseId={}, classCode={}", course.getId(), course.getClassCode());
                continue;
            }
            
            // 2.3 在每个知识库中查找指定的知识点，优先查找已发布的知识库
            for (String knowledgeBaseId : knowledgeBaseIds) {
                KnowledgeBase knowledgeBase = knowledgeBaseRepository.findById(knowledgeBaseId).orElse(null);
                if (knowledgeBase == null || knowledgeBase.getIsDeleted()) continue;
                
                // 只查找已发布的知识库，或者当前用户是该知识库的创建者的知识库
                if (!knowledgeBase.getIsPublished() && !knowledgeBase.getCreatorId().equals(userId)) {
                    log.info("知识库未发布且用户非创建者，跳过: knowledgeBaseId={}, creatorId={}", 
                            knowledgeBase.getId(), knowledgeBase.getCreatorId());
                    continue;
                }
                
                List<String> knowledgeIds = knowledgeBase.getKnowledgeIds();
                if (knowledgeIds != null && !knowledgeIds.isEmpty()) {
                    for (String knowledgeId : knowledgeIds) {
                        Knowledge foundKnowledge = knowledgeRepository.findById(knowledgeId).orElse(null);
                        if (foundKnowledge != null && foundKnowledge.getName().equals(knowledgePointName)) {
                            log.info("在知识库中找到知识点: knowledgeBase={}, knowledgePoint={}", 
                                    knowledgeBase.getName(), knowledgePointName);
                            knowledge = foundKnowledge;
                            // 如果知识库已发布，则优先返回
                            if (knowledgeBase.getIsPublished()) {
                                return knowledge;
                            }
                        }
                    }
                }
            }
        }
        
        // 如果在班级关联的知识库中找到了知识点，则返回
        if (knowledge != null) {
            return knowledge;
        }
        
        // 3. 如果在班级的知识库中没找到，尝试查找用户直接加入的知识库
        List<KnowledgeBase> userKnowledgeBases = knowledgeBaseRepository.findByMembersContaining(userId);
        for (KnowledgeBase knowledgeBase : userKnowledgeBases) {
            if (knowledgeBase.getIsDeleted()) continue;
            
            // 只查找已发布的知识库，或者当前用户是该知识库的创建者的知识库
            if (!knowledgeBase.getIsPublished() && !knowledgeBase.getCreatorId().equals(userId)) {
                continue;
            }
            
            List<String> knowledgeIds = knowledgeBase.getKnowledgeIds();
            if (knowledgeIds != null && !knowledgeIds.isEmpty()) {
                for (String knowledgeId : knowledgeIds) {
                    Knowledge foundKnowledge = knowledgeRepository.findById(knowledgeId).orElse(null);
                    if (foundKnowledge != null && foundKnowledge.getName().equals(knowledgePointName)) {
                        log.info("在用户直接加入的知识库中找到知识点: knowledgeBase={}, knowledgePoint={}", 
                                knowledgeBase.getName(), knowledgePointName);
                        return foundKnowledge;
                    }
                }
            }
        }
        
        // 4. 如果在用户相关的知识库中都没找到，尝试查找所有已发布的知识库
        return findKnowledgeInPublishedBases(knowledgePointName);
    }
    
    /**
     * 在所有已发布的知识库中查找知识点
     * @param knowledgePointName 知识点名称
     * @return 知识点实体
     */
    private Knowledge findKnowledgeInPublishedBases(String knowledgePointName) {
        // 查找所有已发布的知识库
        List<KnowledgeBase> publishedBases = knowledgeBaseRepository.findByIsPublishedTrue();
        
        for (KnowledgeBase knowledgeBase : publishedBases) {
            if (knowledgeBase.getIsDeleted()) continue;
            
            List<String> knowledgeIds = knowledgeBase.getKnowledgeIds();
            if (knowledgeIds != null && !knowledgeIds.isEmpty()) {
                for (String knowledgeId : knowledgeIds) {
                    Knowledge knowledge = knowledgeRepository.findById(knowledgeId).orElse(null);
                    if (knowledge != null && knowledge.getName().equals(knowledgePointName)) {
                        log.info("在公开知识库中找到知识点: knowledgeBase={}, knowledgePoint={}", 
                                knowledgeBase.getName(), knowledgePointName);
                        return knowledge;
                    }
                }
            }
        }
        
        // 如果实在找不到，尝试匹配线性代数知识库（向后兼容，可以在未来版本移除）
        log.info("在所有已发布知识库中未找到知识点，尝试匹配线性代数知识库: knowledgePoint={}", knowledgePointName);
        return knowledgeRepository.findBySubjectAndName(KnowledgeSubject.LINEAR_ALGEBRA, knowledgePointName);
    }
}

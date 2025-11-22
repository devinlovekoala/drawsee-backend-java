package cn.yifan.drawsee.worker;

import cn.yifan.drawsee.constant.NodeTitle;
import cn.yifan.drawsee.constant.*;
import cn.yifan.drawsee.mapper.AiTaskMapper;
import cn.yifan.drawsee.mapper.ClassMapper;
import cn.yifan.drawsee.mapper.ClassMemberMapper;
import cn.yifan.drawsee.mapper.ConversationMapper;
import cn.yifan.drawsee.mapper.CourseMapper;
import cn.yifan.drawsee.mapper.KnowledgeBaseMapper;
import cn.yifan.drawsee.mapper.KnowledgeMapper;
import cn.yifan.drawsee.mapper.KnowledgeResourceMapper;
import cn.yifan.drawsee.mapper.NodeMapper;
import cn.yifan.drawsee.mapper.UserMapper;
import cn.yifan.drawsee.pojo.XYPosition;
import cn.yifan.drawsee.pojo.entity.ClassMember;
import cn.yifan.drawsee.pojo.entity.Course;
import cn.yifan.drawsee.pojo.entity.Knowledge;
import cn.yifan.drawsee.pojo.entity.KnowledgeBase;
import cn.yifan.drawsee.pojo.entity.KnowledgeResource;
import cn.yifan.drawsee.pojo.entity.Node;
import cn.yifan.drawsee.pojo.rabbit.AiTaskMessage;
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
 * @Description 知识点详情AI工作流 - MySQL版本
 * @Author yifan
 * @date 2025-04-15 15:45
 **/

@Slf4j
@Service
public class KnowledgeDetailWorkFlow extends WorkFlow {

    private final KnowledgeBaseMapper knowledgeBaseMapper;
    private final KnowledgeMapper knowledgeMapper;
    private final KnowledgeResourceMapper knowledgeResourceMapper;
    private final ClassMemberMapper classMemberMapper;
    private final CourseMapper courseMapper;
    private final ClassMapper classMapper;

    public KnowledgeDetailWorkFlow(
            UserMapper userMapper,
            AiService aiService,
            StreamAiService streamAiService,
            RedissonClient redissonClient,
            NodeMapper nodeMapper,
            ConversationMapper conversationMapper,
            AiTaskMapper aiTaskMapper,
            ObjectMapper objectMapper,
            KnowledgeBaseMapper knowledgeBaseMapper,
            KnowledgeMapper knowledgeMapper,
            KnowledgeResourceMapper knowledgeResourceMapper,
            ClassMemberMapper classMemberMapper,
            CourseMapper courseMapper,
            ClassMapper classMapper
    ) {
        super(userMapper, aiService, streamAiService, redissonClient, nodeMapper, conversationMapper, aiTaskMapper, objectMapper);
        this.knowledgeBaseMapper = knowledgeBaseMapper;
        this.knowledgeMapper = knowledgeMapper;
        this.knowledgeResourceMapper = knowledgeResourceMapper;
        this.classMemberMapper = classMemberMapper;
        this.courseMapper = courseMapper;
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
        AiTaskMessage aiTaskMessage = workContext.getAiTaskMessage();
        String model = aiTaskMessage.getModel();
        
        TypeReference<Map<String, Object>> parentNodeDataTypeReference = new TypeReference<>() {};
        Map<String, Object> parentNodeData = objectMapper.readValue(parentNode.getData(), parentNodeDataTypeReference);
        String knowledgePoint = (String) parentNodeData.get("text");

        streamAiService.knowledgeDetailChat(history, knowledgePoint, model, handler);
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

        // 检查是否指定了班级ID
        String classId = aiTaskMessage.getClassId();
        if (classId == null || classId.isEmpty()) {
            log.info("未指定班级ID，不查找知识点资源: userId={}, knowledgePoint={}", userId, knowledgePoint);
            return;
        }
        
        log.info("指定了班级ID，尝试查找知识点资源: classId={}, knowledgePoint={}", classId, knowledgePoint);
        
        // 使用新的查找方法，从用户相关的班级课程知识库中查找知识点
        Knowledge knowledge = findKnowledgeInUserBases(userId, knowledgePoint, aiTaskMessage);
        
        if (knowledge == null) {
            log.info("未找到知识点: knowledgePoint={}, userId={}", knowledgePoint, userId);
            return;
        }
        
        log.info("成功找到知识点: knowledgePoint={}, knowledgeId={}", knowledgePoint, knowledge.getId());
        
        // 从知识点关联的资源中获取资源列表
        List<KnowledgeResource> resources = knowledgeResourceMapper.getByKnowledgeId(knowledge.getId(), false);
        if (resources == null || resources.isEmpty()) {
            log.info("知识点没有关联资源: knowledgePoint={}", knowledgePoint);
            return;
        }

        // 过滤获取各类型资源
        List<String> animationObjectNames = resources.stream()
                .filter(knowledgeResource -> "animation".equals(knowledgeResource.getResourceType()))
                .map(KnowledgeResource::getUrl).toList();

        List<String> bilibiliUrls = resources.stream()
                .filter(knowledgeResource -> "bilibili".equals(knowledgeResource.getResourceType()))
                .map(KnowledgeResource::getUrl).toList();
                
        List<String> wordDocUrls = resources.stream()
                .filter(knowledgeResource -> "document".equals(knowledgeResource.getResourceType()))
                .map(KnowledgeResource::getUrl).toList();
                
        List<String> pdfDocUrls = resources.stream()
                .filter(knowledgeResource -> "pdf".equals(knowledgeResource.getResourceType()))
                .map(KnowledgeResource::getUrl).toList();

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
     * @param aiTaskMessage AI任务消息
     * @return 知识点实体
     */
    private Knowledge findKnowledgeInUserBases(Long userId, String knowledgePointName, AiTaskMessage aiTaskMessage) {
        // 获取当前任务的班级ID
        String classId = aiTaskMessage.getClassId();
        
        // 如果指定了班级ID，优先使用指定的班级
        if (classId != null && !classId.isEmpty()) {
            // 直接查找指定班级的知识点
            Knowledge knowledge = findKnowledgeInSpecificClass(classId, knowledgePointName);
            
            // 如果找到了，直接返回
            if (knowledge != null) {
                log.info("在指定班级中找到知识点: classId={}, knowledgePoint={}", classId, knowledgePointName);
                return knowledge;
            }
            
            log.info("在指定班级中未找到知识点，将查找用户其他班级: classId={}, knowledgePoint={}", classId, knowledgePointName);
        }
        
        // 如果没有指定班级ID或者在指定班级中未找到，则查找用户所属的所有班级
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
            
            Course course = courseMapper.getByClassCode(clazz.getClassCode());
            if (course == null || course.getIsDeleted()) continue;
            
            // 2.2 获取课程关联的知识库列表
            List<String> knowledgeBaseIds = course.getKnowledgeBaseIds();
            if (knowledgeBaseIds == null || knowledgeBaseIds.isEmpty()) {
                log.info("课程未关联知识库: courseId={}, classCode={}", course.getId(), course.getClassCode());
                continue;
            }
            
            // 2.3 在每个知识库中查找指定的知识点，优先查找已发布的知识库
            for (String knowledgeBaseId : knowledgeBaseIds) {
                KnowledgeBase knowledgeBase = knowledgeBaseMapper.getById(knowledgeBaseId);
                if (knowledgeBase == null || knowledgeBase.getIsDeleted()) continue;
                
                // 只查找已发布的知识库，或者当前用户是该知识库的创建者的知识库
                if (!knowledgeBase.getIsPublished() && !knowledgeBase.getCreatorId().equals(userId)) {
                    log.info("知识库未发布且用户非创建者，跳过: knowledgeBaseId={}, creatorId={}", 
                            knowledgeBase.getId(), knowledgeBase.getCreatorId());
                    continue;
                }
                
                // 在知识库中查找指定的知识点
                List<Knowledge> knowledgeList = knowledgeMapper.getByKnowledgeBaseId(knowledgeBaseId, false);
                for (Knowledge foundKnowledge : knowledgeList) {
                    if (foundKnowledge.getName().equals(knowledgePointName)) {
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
        
        // 如果在班级关联的知识库中找到了知识点，则返回
        if (knowledge != null) {
            return knowledge;
        }
        
        // 3. 如果在班级的知识库中没找到，尝试查找用户直接加入的知识库
        List<KnowledgeBase> userKnowledgeBases = knowledgeBaseMapper.getByMemberId(userId, false);
        for (KnowledgeBase knowledgeBase : userKnowledgeBases) {
            if (knowledgeBase.getIsDeleted()) continue;
            
            // 只查找已发布的知识库，或者当前用户是该知识库的创建者的知识库
            if (!knowledgeBase.getIsPublished() && !knowledgeBase.getCreatorId().equals(userId)) {
                continue;
            }
            
            List<Knowledge> knowledgeList = knowledgeMapper.getByKnowledgeBaseId(knowledgeBase.getId(), false);
            for (Knowledge foundKnowledge : knowledgeList) {
                if (foundKnowledge.getName().equals(knowledgePointName)) {
                    log.info("在用户直接加入的知识库中找到知识点: knowledgeBase={}, knowledgePoint={}", 
                            knowledgeBase.getName(), knowledgePointName);
                    return foundKnowledge;
                }
            }
        }
        
        // 4. 如果在用户相关的知识库中都没找到，尝试查找所有已发布的知识库
        return findKnowledgeInPublishedBases(knowledgePointName);
    }
    
    /**
     * 在指定班级中查找知识点
     * @param classId 班级ID
     * @param knowledgePointName 知识点名称
     * @return 知识点实体
     */
    private Knowledge findKnowledgeInSpecificClass(String classId, String knowledgePointName) {
        // 1. 通过班级ID获取班级信息
        cn.yifan.drawsee.pojo.entity.Class clazz = classMapper.getById(Long.parseLong(classId));
        if (clazz == null || clazz.getIsDeleted()) {
            log.warn("班级不存在或已删除: classId={}", classId);
            return null;
        }
        
        // 2. 获取班级对应的课程
        Course course = courseMapper.getByClassCode(clazz.getClassCode());
        if (course == null || course.getIsDeleted()) {
            log.warn("课程不存在或已删除: classCode={}", clazz.getClassCode());
            return null;
        }
        
        // 3. 获取课程关联的知识库
        List<String> knowledgeBaseIds = course.getKnowledgeBaseIds();
        if (knowledgeBaseIds == null || knowledgeBaseIds.isEmpty()) {
            log.info("课程没有关联知识库: courseId={}, classCode={}", course.getId(), course.getClassCode());
            return null;
        }
        
        // 4. 在知识库中查找知识点
        for (String knowledgeBaseId : knowledgeBaseIds) {
            KnowledgeBase knowledgeBase = knowledgeBaseMapper.getById(knowledgeBaseId);
            if (knowledgeBase == null || knowledgeBase.getIsDeleted() || !knowledgeBase.getIsPublished()) {
                continue;
            }
            
            List<Knowledge> knowledgeList = knowledgeMapper.getByKnowledgeBaseId(knowledgeBaseId, false);
            for (Knowledge knowledge : knowledgeList) {
                if (knowledge.getName().equals(knowledgePointName)) {
                    log.info("在班级相关知识库中找到知识点: classId={}, knowledgeBase={}, knowledgePoint={}", 
                            classId, knowledgeBase.getName(), knowledgePointName);
                    return knowledge;
                }
            }
        }
        
        log.info("在班级相关知识库中未找到知识点: classId={}, knowledgePoint={}", classId, knowledgePointName);
        return null;
    }
    
    private Knowledge findKnowledgeInPublishedBases(String knowledgePointName) {
        // 查找所有已发布的知识库
        List<KnowledgeBase> publishedBases = knowledgeBaseMapper.getByIsPublishedTrue();
        
        for (KnowledgeBase knowledgeBase : publishedBases) {
            if (knowledgeBase.getIsDeleted()) continue;
            
            List<Knowledge> knowledgeList = knowledgeMapper.getByKnowledgeBaseId(knowledgeBase.getId(), false);
            for (Knowledge knowledge : knowledgeList) {
                if (knowledge.getName().equals(knowledgePointName)) {
                    log.info("在公开知识库中找到知识点: knowledgeBase={}, knowledgePoint={}", 
                            knowledgeBase.getName(), knowledgePointName);
                    return knowledge;
                }
            }
        }
        
        log.info("在所有已发布知识库中未找到知识点: knowledgePoint={}", knowledgePointName);
        return null;
    }
}

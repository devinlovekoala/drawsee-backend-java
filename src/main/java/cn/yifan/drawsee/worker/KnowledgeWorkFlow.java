package cn.yifan.drawsee.worker;

import cn.yifan.drawsee.constant.NodeTitle;
import cn.yifan.drawsee.constant.NodeType;
import cn.yifan.drawsee.constant.RedisKey;
import cn.yifan.drawsee.mapper.AiTaskMapper;
import cn.yifan.drawsee.mapper.ClassMapper;
import cn.yifan.drawsee.mapper.ConversationMapper;
import cn.yifan.drawsee.mapper.CourseMapper;
import cn.yifan.drawsee.mapper.KnowledgeBaseMapper;
import cn.yifan.drawsee.mapper.KnowledgeMapper;
import cn.yifan.drawsee.mapper.NodeMapper;
import cn.yifan.drawsee.mapper.UserMapper;
import cn.yifan.drawsee.pojo.XYPosition;
import cn.yifan.drawsee.pojo.entity.Course;
import cn.yifan.drawsee.pojo.entity.Knowledge;
import cn.yifan.drawsee.pojo.entity.KnowledgeBase;
import cn.yifan.drawsee.pojo.entity.Node;
import cn.yifan.drawsee.pojo.rabbit.AiTaskMessage;
import cn.yifan.drawsee.service.base.AiService;
import cn.yifan.drawsee.service.base.StreamAiService;
import cn.yifan.drawsee.service.business.ClassKnowledgeService;
import cn.yifan.drawsee.service.business.RagQueryService;
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

/**
 * @FileName KnowledgeWorkFlow
 * @Description 知识点分点AI工作流 - MySQL版本
 * @Author yifan
 * @date 2025-04-15 15:40
 **/

@Slf4j
@Service
public class KnowledgeWorkFlow extends WorkFlow {

    private final CourseMapper courseMapper;
    private final KnowledgeBaseMapper knowledgeBaseMapper;
    private final KnowledgeMapper knowledgeMapper;
    private final ClassMapper classMapper;
    private final ClassKnowledgeService classKnowledgeService;
    private final RagQueryService ragQueryService;

    public KnowledgeWorkFlow(
        UserMapper userMapper,
        AiService aiService,
        StreamAiService streamAiService,
        RedissonClient redissonClient,
        NodeMapper nodeMapper,
        ConversationMapper conversationMapper,
        AiTaskMapper aiTaskMapper,
        ObjectMapper objectMapper,
        CourseMapper courseMapper,
        KnowledgeBaseMapper knowledgeBaseMapper,
        KnowledgeMapper knowledgeMapper,
        ClassMapper classMapper,
        ClassKnowledgeService classKnowledgeService,
        RagQueryService ragQueryService
    ) {
        super(userMapper, aiService, streamAiService, redissonClient, nodeMapper, conversationMapper, aiTaskMapper, objectMapper);
        this.courseMapper = courseMapper;
        this.knowledgeBaseMapper = knowledgeBaseMapper;
        this.knowledgeMapper = knowledgeMapper;
        this.classMapper = classMapper;
        this.classKnowledgeService = classKnowledgeService;
        this.ragQueryService = ragQueryService;
        
        // 全局默认知识点列表 - 这里将保留用于通用模式
        refreshGlobalKnowledgePoints();
    }
    
    /**
     * 刷新全局知识点列表
     */
    private void refreshGlobalKnowledgePoints() {
        try {
            List<Knowledge> knowledgeList = knowledgeMapper.getAll(false);
            List<String> knowledgePoints = knowledgeList.stream().map(Knowledge::getName).toList();
            RList<String> rList = redissonClient.getList(RedisKey.CACHE_PREFIX + "knowledge-points");
            rList.clear();
            rList.addAll(knowledgePoints);
            // 清除过期时间，设置为永不过期
            rList.clearExpire();
        } catch (Exception e) {
            log.warn("刷新全局知识点列表失败，可能是数据库表不存在: error={}", e.getMessage());
        }
    }

    @Override
    public void createOtherNodesOrUpdateNodeData(WorkContext workContext) throws JsonProcessingException {
        AiTaskMessage aiTaskMessage = workContext.getAiTaskMessage();
        Node streamNode = workContext.getStreamNode();
        
        // 首先尝试使用RAG增强AI回答
        boolean ragSuccess = tryEnhanceWithRag(workContext);
        
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
            try {
                RList<String> rList = redissonClient.getList(RedisKey.CACHE_PREFIX + "knowledge-points");
                knowledgePoints = rList.stream().toList();
            } catch (Exception e) {
                log.warn("获取全局知识点列表失败: error={}", e.getMessage());
                knowledgePoints = new ArrayList<>();
            }
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
        try {
            List<Course> userCourses = courseMapper.getByStudentId(userId, false);
            for (Course course : userCourses) {
                List<String> knowledgeBaseIds = course.getKnowledgeBaseIds();
                if (knowledgeBaseIds != null && !knowledgeBaseIds.isEmpty()) {
                    for (String knowledgeBaseId : knowledgeBaseIds) {
                        KnowledgeBase knowledgeBase = knowledgeBaseMapper.getById(knowledgeBaseId);
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
        } catch (Exception e) {
            log.warn("查询用户课程失败，可能是数据库表不存在: userId={}, error={}", userId, e.getMessage());
        }
        
        // 从用户直接加入的知识库中获取知识点
        try {
            List<KnowledgeBase> userKnowledgeBases = knowledgeBaseMapper.getByMemberId(userId, false);
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
        } catch (Exception e) {
            log.warn("查询用户知识库失败，可能是数据库表不存在: userId={}, error={}", userId, e.getMessage());
        }
        
        // 添加所有公开发布的知识库中的知识点
        try {
            List<KnowledgeBase> publishedBases = knowledgeBaseMapper.getByIsPublishedTrue();
            for (KnowledgeBase knowledgeBase : publishedBases) {
                if (!knowledgeBase.getIsDeleted()) {
                    addKnowledgePointsFromBase(knowledgeBase, knowledgePoints);
                }
            }
        } catch (Exception e) {
            log.warn("查询已发布知识库失败，可能是数据库表不存在: error={}", e.getMessage());
        }
        
        return new ArrayList<>(knowledgePoints);
    }
    
    /**
     * 从知识库中添加知识点到集合
     * @param knowledgeBase 知识库
     * @param knowledgePoints 知识点集合
     */
    private void addKnowledgePointsFromBase(KnowledgeBase knowledgeBase, Set<String> knowledgePoints) {
        try {
            // 从知识库中获取所有知识点
            List<Knowledge> knowledgeList = knowledgeMapper.getByKnowledgeBaseId(knowledgeBase.getId(), false);
            for (Knowledge knowledge : knowledgeList) {
                knowledgePoints.add(knowledge.getName());
            }
        } catch (Exception e) {
            log.warn("从知识库获取知识点失败: knowledgeBaseId={}, error={}", knowledgeBase.getId(), e.getMessage());
        }
    }
    
    /**
     * 获取班级相关的知识点列表
     * @param classId 班级ID
     * @return 知识点列表
     */
    private List<String> getClassRelatedKnowledgePoints(String classId) {
        Set<String> knowledgePoints = new HashSet<>();
        
        try {
            // 通过班级ID获取班级信息
            cn.yifan.drawsee.pojo.entity.Class clazz = classMapper.getById(Long.parseLong(classId));
            if (clazz == null || clazz.getIsDeleted()) {
                log.warn("班级不存在或已删除: classId={}", classId);
                return new ArrayList<>();
            }
            
            // 获取该班级对应的课程
            Course course = courseMapper.getByClassCode(clazz.getClassCode());
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
                KnowledgeBase knowledgeBase = knowledgeBaseMapper.getById(knowledgeBaseId);
                if (knowledgeBase != null && !knowledgeBase.getIsDeleted() && knowledgeBase.getIsPublished()) {
                    addKnowledgePointsFromBase(knowledgeBase, knowledgePoints);
                }
            }
            
            if (knowledgePoints.isEmpty()) {
                log.info("班级相关知识库中未找到知识点: classId={}", classId);
            } else {
                log.info("从班级相关知识库中找到{}个知识点: classId={}", knowledgePoints.size(), classId);
            }
        } catch (Exception e) {
            log.warn("获取班级相关知识点失败: classId={}, error={}", classId, e.getMessage());
        }
        
        return new ArrayList<>(knowledgePoints);
    }

    /**
     * 尝试使用RAG增强AI回答
     */
    private boolean tryEnhanceWithRag(WorkContext workContext) {
        try {
            AiTaskMessage aiTaskMessage = workContext.getAiTaskMessage();
            String classId = aiTaskMessage.getClassId();
            Long userId = aiTaskMessage.getUserId();
            
            // 获取可访问的知识库列表
            List<String> knowledgeBaseIds;
            if (classId != null && !classId.isEmpty()) {
                knowledgeBaseIds = classKnowledgeService.getAccessibleKnowledgeBaseIds(Long.parseLong(classId), userId);
            } else {
                knowledgeBaseIds = classKnowledgeService.getAccessibleKnowledgeBaseIds(null, userId);
            }
            
            if (knowledgeBaseIds.isEmpty()) {
                log.info("用户 {} 在班级 {} 中没有可访问的知识库，跳过RAG增强", userId, classId);
                return false;
            }
            
            // 使用RAG服务检索并生成增强回答（作为MCP工具）
            log.info("调用Python RAG MCP工具进行知识检索，知识库数量: {}, 查询: {}", knowledgeBaseIds.size(), aiTaskMessage.getPrompt());
            var ragResponse = ragQueryService.query(
                knowledgeBaseIds,
                aiTaskMessage.getPrompt(),
                null,  // history - can be null for now
                aiTaskMessage.getUserId(),
                String.valueOf(classId)
            );

            if (ragResponse != null && ragResponse.getAnswer() != null && !ragResponse.getAnswer().isEmpty()) {
                // 将RAG增强的回答作为额外的上下文信息添加到streamNode
                Node streamNode = workContext.getStreamNode();
                String originalData = streamNode.getData();

                // 解析原始数据
                Map<String, Object> dataMap = objectMapper.readValue(originalData, Map.class);

                // 添加RAG增强信息
                dataMap.put("ragEnhanced", true);
                dataMap.put("ragKnowledgeBaseCount", knowledgeBaseIds.size());
                if (ragResponse.getChunks() != null) {
                    dataMap.put("ragChunkCount", ragResponse.getChunks().size());
                }

                // 更新节点数据
                streamNode.setData(objectMapper.writeValueAsString(dataMap));

                log.info("RAG MCP工具调用成功，知识库数量: {}, 检索到的块数量: {}",
                    knowledgeBaseIds.size(),
                    ragResponse.getChunks() != null ? ragResponse.getChunks().size() : 0);
                return true;
            } else {
                log.info("RAG MCP工具返回null或无结果，回退到纯LLM生成（无RAG增强）");
            }
            
        } catch (Exception e) {
            log.warn("RAG增强失败: {}", e.getMessage(), e);
        }

        return false;
    }

    /**
     * 重写streamChat方法，注入RAG检索上下文增强Prompt
     */
    @Override
    public void streamChat(WorkContext workContext, dev.langchain4j.model.StreamingResponseHandler<dev.langchain4j.data.message.AiMessage> handler) throws com.fasterxml.jackson.core.JsonProcessingException {
        AiTaskMessage aiTaskMessage = workContext.getAiTaskMessage();
        java.util.LinkedList<dev.langchain4j.data.message.ChatMessage> history = workContext.getHistory();
        String originalPrompt = aiTaskMessage.getPrompt();

        // 尝试RAG增强Prompt
        String enhancedPrompt = originalPrompt;
        try {
            String classId = aiTaskMessage.getClassId();
            Long userId = aiTaskMessage.getUserId();

            // 获取可访问的知识库列表
            List<String> knowledgeBaseIds;
            if (classId != null && !classId.isEmpty()) {
                knowledgeBaseIds = classKnowledgeService.getAccessibleKnowledgeBaseIds(Long.parseLong(classId), userId);
            } else {
                knowledgeBaseIds = classKnowledgeService.getAccessibleKnowledgeBaseIds(null, userId);
            }

            if (!knowledgeBaseIds.isEmpty()) {
                log.info("使用RAG增强Prompt: 知识库数量={}, 原始问题={}", knowledgeBaseIds.size(), originalPrompt);

                // 调用RAG检索
                var ragResponse = ragQueryService.query(
                    knowledgeBaseIds,
                    originalPrompt,
                    null,
                    userId,
                    String.valueOf(classId)
                );

                if (ragResponse != null && ragResponse.getAnswer() != null && !ragResponse.getAnswer().isEmpty()) {
                    // 将RAG检索结果注入Prompt
                    StringBuilder promptBuilder = new StringBuilder();
                    promptBuilder.append("【知识库检索结果】\n");
                    promptBuilder.append(ragResponse.getAnswer());
                    promptBuilder.append("\n\n");
                    promptBuilder.append("【用户问题】\n");
                    promptBuilder.append(originalPrompt);
                    promptBuilder.append("\n\n");
                    promptBuilder.append("请基于上述知识库中的电路图相关知识，结合你的专业能力，准确、详细地回答用户问题。");
                    promptBuilder.append("如果知识库中的信息与用户问题不完全匹配，请明确指出，并基于你的知识给出最佳建议。");

                    enhancedPrompt = promptBuilder.toString();

                    log.info("RAG Prompt增强成功: 检索到{}个相关电路图, 增强后Prompt长度={}",
                             ragResponse.getChunks() != null ? ragResponse.getChunks().size() : 0,
                             enhancedPrompt.length());
                } else {
                    log.info("RAG检索无结果，使用原始Prompt");
                }
            }
        } catch (Exception e) {
            log.warn("RAG Prompt增强失败，使用原始Prompt: {}", e.getMessage());
        }

        // 使用增强后的Prompt调用AI服务
        if (cn.yifan.drawsee.constant.AiTaskType.GENERAL.equals(aiTaskMessage.getType())) {
            streamAiService.answerPointChat(history, enhancedPrompt, aiTaskMessage.getModel(), handler);
        } else {
            streamAiService.generalChat(history, enhancedPrompt, aiTaskMessage.getModel(), handler);
        }
    }
}
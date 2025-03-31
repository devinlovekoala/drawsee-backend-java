package cn.yifan.drawsee.service.business;

import cn.dev33.satoken.stp.StpUtil;
import cn.yifan.drawsee.constant.*;
import cn.yifan.drawsee.exception.ApiError;
import cn.yifan.drawsee.exception.ApiException;
import cn.yifan.drawsee.mapper.ConversationMapper;
import cn.yifan.drawsee.mapper.AiTaskMapper;
import cn.yifan.drawsee.mapper.NodeMapper;
import cn.yifan.drawsee.pojo.XYPosition;
import cn.yifan.drawsee.pojo.dto.CreateAiTaskDTO;
import cn.yifan.drawsee.pojo.dto.UpdateNodeDTO;
import cn.yifan.drawsee.pojo.dto.UpdateNodesDTO;
import cn.yifan.drawsee.pojo.entity.AiTask;
import cn.yifan.drawsee.pojo.entity.Conversation;
import cn.yifan.drawsee.pojo.entity.Node;
import cn.yifan.drawsee.pojo.rabbit.LinkedQueue;
import cn.yifan.drawsee.pojo.rabbit.AiTaskMessage;
import cn.yifan.drawsee.pojo.vo.*;
import cn.yifan.drawsee.service.base.AiService;
import cn.yifan.drawsee.service.base.MinioService;
import cn.yifan.drawsee.util.RedisUtils;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.minio.errors.MinioException;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.Nullable;
import org.redisson.api.RAtomicLong;
import org.redisson.api.RStream;
import org.redisson.api.RedissonClient;
import org.redisson.api.StreamMessageId;
import org.redisson.api.stream.StreamReadArgs;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import java.io.IOException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.sql.Timestamp;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * @FileName FlowService
 * @Description
 * @Author yifan
 * @date 2025-01-29 17:27
 **/

@Service
@Slf4j
public class FlowService {

    @Autowired
    private List<LinkedQueue> aiTaskQueues;
    @Autowired
    private RabbitTemplate rabbitTemplate;
    @Autowired
    private RedissonClient redissonClient;
    @Autowired
    private AiTaskMapper aiTaskMapper;
    @Autowired
    private ConversationMapper conversationMapper;
    @Autowired
    private NodeMapper nodeMapper;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private MinioService minioService;
    @Autowired
    private AiService aiService;

    // 获取用户所有会话
    public List<ConversationVO> getConversations() {
        List<ConversationVO> conversationVOS = new ArrayList<>();
        Long userId = StpUtil.getLoginIdAsLong();
        List<Conversation> conversations = conversationMapper.getByUserId(userId);
        for (Conversation conversation : conversations) {
            ConversationVO conversationVO = new ConversationVO();
            BeanUtils.copyProperties(conversation, conversationVO);
            conversationVOS.add(conversationVO);
        }
        return conversationVOS;
    }

    // 获取会话下所有节点
    public List<NodeVO> getNodes(Long convId) {
        List<NodeVO> nodeVOS = new ArrayList<>();
        Long userId = StpUtil.getLoginIdAsLong();
        Conversation conversation = conversationMapper.getById(convId);
        if (conversation == null || !userId.equals(conversation.getUserId())) {
            throw new ApiException(ApiError.CONVERSATION_NOT_EXISTED);
        }
        List<Node> nodes = nodeMapper.getByConvId(convId);
        for (Node node : nodes) {
            NodeVO nodeVO = new NodeVO();
            nodeVO.setId(node.getId());
            nodeVO.setType(node.getType());
            TypeReference<Map<String, Object>> dataTypeReference = new TypeReference<Map<String, Object>>() {};
            Map<String, Object> data = null;
            try {
                data = objectMapper.readValue(node.getData(), dataTypeReference);
            } catch (JsonProcessingException e) {
                throw new ApiException(ApiError.SYSTEM_ERROR);
            }
            XYPosition position = null;
            try {
                position = objectMapper.readValue(node.getPosition(), XYPosition.class);
            } catch (JsonProcessingException e) {
                throw new ApiException(ApiError.SYSTEM_ERROR);
            }
            nodeVO.setData(data);
            nodeVO.setHeight(null);
            nodeVO.setPosition(position);
            nodeVO.setHeight(node.getHeight());
            nodeVO.setParentId(node.getParentId());
            nodeVO.setConvId(node.getConvId());
            nodeVO.setUserId(node.getUserId());
            nodeVO.setCreatedAt(node.getCreatedAt());
            nodeVO.setUpdatedAt(node.getUpdatedAt());
            nodeVOS.add(nodeVO);
        }
        return nodeVOS;
    }

    // 更新节点位置
    public void updateNodes(UpdateNodesDTO updateNodesDTO) {
        Long userId = StpUtil.getLoginIdAsLong();
        List<Node> nodes = new ArrayList<>();
        for (UpdateNodesDTO.NodeToUpdate nodeToUpdate : updateNodesDTO.getNodes()) {
            if (nodeToUpdate.getHeight() == null || nodeToUpdate.getPosition() == null) {
                throw new ApiException(ApiError.PARAM_ERROR);
            }
            Node node = nodeMapper.getById(nodeToUpdate.getId());
            if (node == null || !userId.equals(node.getUserId())) {
                throw new ApiException(ApiError.PARAM_ERROR);
            }
            String position = null;
            try {
                position = objectMapper.writeValueAsString(nodeToUpdate.getPosition());
            } catch (JsonProcessingException e) {
                throw new ApiException(ApiError.PARAM_ERROR);
            }
            node.setPosition(position);
            node.setHeight(nodeToUpdate.getHeight());
            nodes.add(node);
        }
        nodeMapper.updatePositionAndHeightBatch(nodes);
    }

    // 获取所有生成中的任务
    public List<AiTaskVO> getProcessingTasks(Long convId) {
        Long userId = StpUtil.getLoginIdAsLong();
        List<AiTask> aiTasks = aiTaskMapper.getByUserIdAndConvIdAndStatus(userId, convId, AiTaskStatus.PROCESSING);
        // 把task转换为taskVO，lambda玩法
        return aiTasks.stream().map((task -> {
            AiTaskVO aiTaskVO = new AiTaskVO();
            BeanUtils.copyProperties(task, aiTaskVO);
            return aiTaskVO;
        })).toList();
    }

    // 随机选取队列
    private LinkedQueue getRandomQueue() {
        Random random = new Random();
        int randomIndex = random.nextInt(aiTaskQueues.size());
        return aiTaskQueues.get(randomIndex);
    }

    // 校验每天最多x次AI对话
    public void validateUseAiCount(Long userId) {
        RAtomicLong counter = RedisUtils.getUseAiCounter(redissonClient, userId);
        // 检查
        if (counter.get() >= AiTaskLimit.DAY_LIMIT) {
            throw new ApiException(ApiError.AI_TASK_EXCEED_LIMIT);
        }
        // 加1
        counter.incrementAndGet();
    }

    /**
     * 验证用户每日对话次数限制
     *
     * @param userId 用户ID
     */
    private void validateDailyConversationLimit(Long userId) {
        String key = RedisKey.DAILY_CONVERSATION_COUNT + userId;
        RAtomicLong count = redissonClient.getAtomicLong(key);
        
        // 如果是新的一天，重置计数器
        if (!count.isExists()) {
            count.set(0);
            count.expire(Duration.ofDays(1));
        }
        
        // 检查是否超过限制
        if (count.get() >= SystemConfig.MAX_DAILY_CONVERSATIONS) {
            throw new ApiException(ApiError.DAILY_LIMIT_EXCEEDED);
        }
        
        // 增加计数
        count.incrementAndGet();
    }

    /**
     * 创建AI任务
     *
     * @param createAiTaskDTO 创建AI任务的DTO
     * @return AI任务VO
     */
    public CreateAiTaskVO createTask(CreateAiTaskDTO createAiTaskDTO) {
        Long userId = StpUtil.getLoginIdAsLong();
        
        // 校验每日对话次数限制
        validateDailyConversationLimit(userId);

        // 创建会话（如果需要）
        Conversation conversation = null;
        if (createAiTaskDTO.getConvId() == null) {
            try {
                conversation = createNewConversation(createAiTaskDTO);
                createAiTaskDTO.setConvId(conversation.getId());
            } catch (JsonProcessingException e) {
                throw new ApiException(ApiError.SYSTEM_ERROR);
            }
        }

        // 创建AI任务
        AiTask aiTask = new AiTask();
        aiTask.setUserId(userId);
        aiTask.setConvId(createAiTaskDTO.getConvId());
        aiTask.setType(createAiTaskDTO.getType());
        aiTask.setStatus(AiTaskStatus.PENDING);
        aiTask.setPrompt(createAiTaskDTO.getPrompt());
        aiTask.setPromptParams(objectMapper.valueToTree(createAiTaskDTO.getPromptParams()));
        aiTaskMapper.insert(aiTask);

        // 创建任务消息
        AiTaskMessage aiTaskMessage = new AiTaskMessage();
        aiTaskMessage.setTaskId(aiTask.getId());
        aiTaskMessage.setUserId(userId);
        BeanUtils.copyProperties(createAiTaskDTO, aiTaskMessage);

        // 随机选取队列并发送到RabbitMQ
        LinkedQueue queue = getRandomQueue();
        rabbitTemplate.convertAndSend(
            queue.getExchangeName(),
            queue.getRoutingKey(),
            aiTaskMessage
        );

        // 构建返回对象
        CreateAiTaskVO createAiTaskVO = new CreateAiTaskVO();
        AiTaskVO aiTaskVO = new AiTaskVO();
        BeanUtils.copyProperties(aiTask, aiTaskVO);
        
        if (conversation != null) {
            ConversationVO conversationVO = new ConversationVO();
            BeanUtils.copyProperties(conversation, conversationVO);
            aiTaskVO.setConversation(conversationVO);
        }
        
        createAiTaskVO.setTask(aiTaskVO);
        return createAiTaskVO;
    }

    /**
     * 创建新会话
     * @throws JsonProcessingException JSON处理异常
     */
    private Conversation createNewConversation(CreateAiTaskDTO createAiTaskDTO) throws JsonProcessingException {
        Long userId = StpUtil.getLoginIdAsLong();
        
        // 生成会话标题
        String title = aiService.getConvTitle(createAiTaskDTO.getPrompt());
        
        // 创建会话
        Conversation conversation = new Conversation();
        conversation.setUserId(userId);
        conversation.setTitle(title);
        conversation.setSubject(createAiTaskDTO.getSubject());
        conversationMapper.insert(conversation);
        
        // 创建根节点
        Node rootNode = new Node();
        rootNode.setType(NodeType.ROOT);
        rootNode.setData("{}");
        rootNode.setPosition(objectMapper.writeValueAsString(XYPosition.origin()));
        rootNode.setUserId(userId);
        rootNode.setConvId(conversation.getId());
        rootNode.setParentId(0L);
        nodeMapper.insert(rootNode);
        
        return conversation;
    }

    @Async
    public void getCompletion(SseEmitter emitter, Long taskId) {
        AiTask aiTask = aiTaskMapper.getById(taskId);
        RStream<String, Object> redisStream = redissonClient.getStream(RedisKey.AI_TASK_PREFIX + taskId);
        String errorMessage = getAiTaskError(aiTask, redisStream);

        if (errorMessage != null) {
            SseEmitter.SseEventBuilder event = SseEmitter.event()
                    .data(errorMessage)
                    .name("error");
            try {
                emitter.send(event);
            } catch (IOException e) {
                emitter.completeWithError(e);
                return;
            }
            emitter.complete();
            return;
        }

        StreamMessageId lastId = StreamMessageId.ALL; // 从最开始的offset读取消息
        while (true) {
            // 从上次读取的位置继续读取消息
            Map<StreamMessageId, Map<String, Object>> messages = redisStream.read(
                StreamReadArgs.greaterThan(lastId).count(1).timeout(Duration.ofSeconds(0))
            ); // TODO 可优化参数
            if (!messages.isEmpty()) {
                // 遍历获取Map中的每对消息
                for (Map.Entry<StreamMessageId, Map<String, Object>> entry : messages.entrySet()) {
                    StreamMessageId messageId = entry.getKey();
                    Map<String, Object> messageData = entry.getValue();
                    try {
                        SseEmitter.SseEventBuilder event = SseEmitter.event()
                            .data(messageData)
                            .name("message");
                        emitter.send(event);
                        String status = (String) messageData.get("type");
                        if (status.equals(AiTaskMessageType.DONE)) {
                            emitter.complete();
                            return;
                        }
                    } catch (IOException e) {
                        // 发生异常时，完成 SseEmitter 并设置异常信息
                        emitter.completeWithError(e);
                        return;
                    }
                    // 更新最后读取的消息 ID
                    lastId = messageId;
                }
            }
        }
    }

    @Nullable
    private static String getAiTaskError(AiTask chatTask, RStream<String, Object> redisStream) {
        String errorMessage = null;
        if (chatTask == null) {
            errorMessage = ApiError.AI_TASK_NOT_EXISTED.getMessage();
        }
        else if (chatTask.getStatus().equals(AiTaskStatus.WAITING)) {
            errorMessage = ApiError.AI_TASK_IS_WAITING.getMessage();
        }
        /*else if (chatTask.getStatus().equals(ChatTaskStatus.SUCCEEDED) || chatTask.getStatus().equals(ChatTaskStatus.FAILED)) {
            errorMessage = ApiError.CHAT_TASK_IS_FINISHED.getMessage();
        }
        else if (!redisStream.isExists()) {
            errorMessage = ApiError.CHAT_TASK_IS_FINISHED.getMessage();
        }*/
        return errorMessage;
    }

    public ResourceVO getResource(String objectName) {
        try {
            String url = minioService.getObjectUrl(objectName);
            return new ResourceVO(url);
        } catch (MinioException | IOException | NoSuchAlgorithmException | InvalidKeyException e) {
            throw new ApiException(ApiError.PARAM_ERROR);
        }
    }

    public void updateNode(Long nodeId, UpdateNodeDTO updateNodeDTO) {
        Node node = nodeMapper.getById(nodeId);
        if (node == null) {
            throw new ApiException(ApiError.PARAM_ERROR);
        }
        String data = null;
        try {
            data = objectMapper.writeValueAsString(updateNodeDTO.getData());
        } catch (JsonProcessingException e) {
            throw new ApiException(ApiError.PARAM_ERROR);
        }
        node.setData(data);
        nodeMapper.update(node);
    }

    @Transactional
    public void deleteNode(Long nodeId) {
        Node node = nodeMapper.getById(nodeId);
        if (node == null) {
            throw new ApiException(ApiError.NODE_NOT_EXISTED);
        }
        // 删除节点及其所有子节点
        Long convId = node.getConvId();
        List<Node> allNodes = nodeMapper.getByConvId(convId);
        List<Node> nodesToDelete = new ArrayList<>();
        node.setIsDeleted(true);
        nodesToDelete.add(node);
        Long currentId = node.getId();
        do {
            boolean hasChild = false;
            // 遍历所有节点，找到当前节点的子节点
            for (Node n : allNodes) {
                if (n.getParentId() != null && n.getParentId().equals(currentId)) {
                    n.setIsDeleted(true);
                    nodesToDelete.add(n);
                    currentId = n.getId();
                    hasChild = true;
                }
            }
            if (!hasChild) {
                break;
            }
        } while (true);
        nodeMapper.updateDataAndIsDeletedBatch(nodesToDelete);
    }

    @Transactional
    public void deleteConversation(Long convId) {
        Conversation conversation = conversationMapper.getById(convId);
        if (conversation == null) {
            throw new ApiException(ApiError.CONVERSATION_NOT_EXISTED);
        }
        // 删除会话
        conversation.setIsDeleted(true);
        conversationMapper.update(conversation);
        // 删除会话下所有节点
        List<Node> nodes = nodeMapper.getByConvId(convId);
        for (Node node : nodes) {
            node.setIsDeleted(true);
        }
        if (!nodes.isEmpty()) {
            nodeMapper.updateDataAndIsDeletedBatch(nodes);
        }
    }
}

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
import org.springframework.core.io.Resource;
import org.springframework.http.ResponseEntity;
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
    private ModeAutoDetectionService modeAutoDetectionService;
    @Autowired
    private UserRoleService userRoleService;

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
            throw new ApiException(ApiError.CONVERSATION_NOT_EXISTED, "文件不能为空");
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
                throw new ApiException(ApiError.SYSTEM_ERROR, "文件不能为空");
            }
            XYPosition position = null;
            try {
                position = objectMapper.readValue(node.getPosition(), XYPosition.class);
            } catch (JsonProcessingException e) {
                throw new ApiException(ApiError.SYSTEM_ERROR, "文件不能为空");
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
                throw new ApiException(ApiError.PARAM_ERROR, "文件不能为空");
            }
            Node node = nodeMapper.getById(nodeToUpdate.getId());
            if (node == null || !userId.equals(node.getUserId())) {
                throw new ApiException(ApiError.PARAM_ERROR, "文件不能为空");
            }
            String position = null;
            try {
                position = objectMapper.writeValueAsString(nodeToUpdate.getPosition());
            } catch (JsonProcessingException e) {
                throw new ApiException(ApiError.PARAM_ERROR, "文件不能为空");
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
        // 获取用户角色
        String userRole = userRoleService.getUserRole(userId);
        
        // 管理员和教师角色不受限制
        if (UserRole.ADMIN.equals(userRole) || UserRole.TEACHER.equals(userRole)) {
            return;
        }
        
        // 普通用户检查限制
        RAtomicLong counter = RedisUtils.getUseAiCounter(redissonClient, userId);
        // 检查是否超过限制
        if (counter.get() >= AiTaskLimit.NORMAL_USER_DAY_LIMIT) {
            throw new ApiException(ApiError.AI_TASK_EXCEED_LIMIT, "文件不能为空");
        }
        // 加1
        counter.incrementAndGet();
    }

    // 创建AI生成任务
    public CreateAiTaskVO createTask(CreateAiTaskDTO createAiTaskDTO) {
        Long userId = StpUtil.getLoginIdAsLong();

        // 参数校验
        if (createAiTaskDTO.getConvId() != null && createAiTaskDTO.getParentId() == null) {
            throw new ApiException(ApiError.PARAM_ERROR, "文件不能为空");
        }
        if (
                createAiTaskDTO.getType().equals(AiTaskType.SOLVER_FIRST) &&
                        createAiTaskDTO.getPromptParams().get("method") == null
        ) {
            throw new ApiException(ApiError.PARAM_ERROR, "文件不能为空");
        }

        validateUseAiCount(userId);
        
        // 如果是GENERAL类型，使用模式自动识别服务识别具体的任务类型
        if (AiTaskType.GENERAL.equals(createAiTaskDTO.getType())) {
            String detectedType = modeAutoDetectionService.detectTaskType(createAiTaskDTO);
            createAiTaskDTO.setType(detectedType);
            log.info("任务类型自动识别结果: {} -> {}", AiTaskType.GENERAL, detectedType);
        }

        // 如果没有convId，创建一个conversation以及第一个 node
        Conversation conversation = null;
        if (createAiTaskDTO.getConvId() == null) {
            // new conversation
            conversation = new Conversation("新会话", userId);
            conversationMapper.insert(conversation);
            createAiTaskDTO.setConvId(conversation.getId());
            // new node
            String position = null;
            try {
                position = objectMapper.writeValueAsString(XYPosition.origin());
            } catch (JsonProcessingException e) {
                throw new ApiException(ApiError.SYSTEM_ERROR, "文件不能为空");
            }
            Map<String, Object> dataMap = new ConcurrentHashMap<>();
            String data = null;
            try {
                data = objectMapper.writeValueAsString(dataMap);
            } catch (JsonProcessingException e) {
                throw new ApiException(ApiError.SYSTEM_ERROR, "文件不能为空");
            }
            Node node = new Node(
                    NodeType.ROOT, data, position, null, userId, conversation.getId(), false
            );
            nodeMapper.insert(node);
            createAiTaskDTO.setParentId(node.getId());
        }

        // ai_task
        Map<String, Object> dataMap = new ConcurrentHashMap<>();
        if (createAiTaskDTO.getPrompt() != null) {
            dataMap.put("prompt", createAiTaskDTO.getPrompt());
        }
        if (createAiTaskDTO.getPromptParams() != null) {
            dataMap.put("promptParams", createAiTaskDTO.getPromptParams());
        }
        dataMap.put("parentId", createAiTaskDTO.getParentId());
        String data = null;
        try {
            data = objectMapper.writeValueAsString(dataMap);
        } catch (JsonProcessingException e) {
            throw new ApiException(ApiError.PARAM_ERROR, "文件不能为空");
        }
        AiTask aiTask = new AiTask(
                createAiTaskDTO.getType(), data,
                AiTaskStatus.WAITING, userId, createAiTaskDTO.getConvId()
        );
        aiTaskMapper.insert(aiTask);

        // ai_task_message
        AiTaskMessage aiTaskMessage = new AiTaskMessage();
        aiTaskMessage.setTaskId(aiTask.getId());
        aiTaskMessage.setUserId(userId);
        BeanUtils.copyProperties(createAiTaskDTO, aiTaskMessage);
        
        // 设置班级ID，用于知识库选择
        if (createAiTaskDTO.getClassId() != null && !createAiTaskDTO.getClassId().isEmpty()) {
            log.info("AI任务使用指定班级ID: taskId={}, classId={}", aiTask.getId(), createAiTaskDTO.getClassId());
            aiTaskMessage.setClassId(createAiTaskDTO.getClassId());
        }

        // 随机选取队列
        LinkedQueue queue = getRandomQueue();

        // 发送到RabbitMQ
        rabbitTemplate.convertAndSend(
                queue.getExchangeName(),
                queue.getRoutingKey(),
                aiTaskMessage
        );

        ConversationVO conversationVO = null;
        if (conversation != null) {
            conversationVO = new ConversationVO();
            BeanUtils.copyProperties(conversation, conversationVO);
            conversationVO.setCreatedAt(new Timestamp(System.currentTimeMillis()));
            conversationVO.setUpdatedAt(new Timestamp(System.currentTimeMillis()));
        }

        // 返回任务ID和convId
        return new CreateAiTaskVO(aiTask.getId(), conversationVO);
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
        /* 保留：如需在任务已结束或流不存在时返回错误，可在此处扩展判断。
        else if (chatTask.getStatus().equals(ChatTaskStatus.SUCCEEDED) || chatTask.getStatus().equals(ChatTaskStatus.FAILED)) {
            errorMessage = ApiError.CHAT_TASK_IS_FINISHED.getMessage();
        }
        else if (!redisStream.isExists()) {
            errorMessage = ApiError.CHAT_TASK_IS_FINISHED.getMessage();
        }*/
        return errorMessage;
    }

    public ResourceVO getResource(String objectName) {
        try {
            return minioService.getResource(objectName);
        } catch (MinioException | IOException | NoSuchAlgorithmException | InvalidKeyException e) {
            throw new ApiException(ApiError.SYSTEM_ERROR, "文件不能为空");
        }
    }

    public ResponseEntity<Resource> downloadResource(String objectName) {
        try {
            return minioService.downloadResource(objectName);
        } catch (MinioException | IOException | NoSuchAlgorithmException | InvalidKeyException e) {
            throw new ApiException(ApiError.SYSTEM_ERROR, "文件不能为空");
        }
    }

    public void updateNode(Long nodeId, UpdateNodeDTO updateNodeDTO) {
        Node node = nodeMapper.getById(nodeId);
        if (node == null) {
            throw new ApiException(ApiError.PARAM_ERROR, "文件不能为空");
        }
        String data = null;
        try {
            data = objectMapper.writeValueAsString(updateNodeDTO.getData());
        } catch (JsonProcessingException e) {
            throw new ApiException(ApiError.PARAM_ERROR, "文件不能为空");
        }
        node.setData(data);
        nodeMapper.update(node);
    }

    @Transactional
    public void deleteNode(Long nodeId) {
        Node node = nodeMapper.getById(nodeId);
        if (node == null) {
            throw new ApiException(ApiError.NODE_NOT_EXISTED, "文件不能为空");
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
            throw new ApiException(ApiError.CONVERSATION_NOT_EXISTED, "文件不能为空");
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
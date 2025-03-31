package cn.yifan.drawsee.worker;

import cn.yifan.drawsee.constant.NodeTitle;
import cn.yifan.drawsee.constant.*;
import cn.yifan.drawsee.mapper.AiTaskMapper;
import cn.yifan.drawsee.mapper.ConversationMapper;
import cn.yifan.drawsee.mapper.NodeMapper;
import cn.yifan.drawsee.mapper.UserMapper;
import cn.yifan.drawsee.pojo.XYPosition;
import cn.yifan.drawsee.pojo.entity.AiTask;
import cn.yifan.drawsee.pojo.entity.Conversation;
import cn.yifan.drawsee.pojo.entity.Node;
import cn.yifan.drawsee.pojo.entity.User;
import cn.yifan.drawsee.pojo.rabbit.AiTaskMessage;
import cn.yifan.drawsee.pojo.vo.NodeVO;
import cn.yifan.drawsee.repository.KnowledgeRepository;
import cn.yifan.drawsee.service.base.AiService;
import cn.yifan.drawsee.service.base.StreamAiService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.StreamingResponseHandler;
import dev.langchain4j.model.output.Response;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RStream;
import org.redisson.api.RedissonClient;
import org.redisson.api.stream.StreamAddArgs;
import org.springframework.beans.BeanUtils;

import java.sql.Timestamp;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * @FileName WorkFlow
 * @Description
 * @Author yifan
 * @date 2025-03-08 22:29
 **/

@Slf4j
public class WorkFlow {

    protected final UserMapper userMapper;
    protected final ConversationMapper conversationMapper;
    protected final NodeMapper nodeMapper;
    protected final AiTaskMapper aiTaskMapper;
    protected final KnowledgeRepository knowledgeRepository;
    protected final AiService aiService;
    protected final StreamAiService streamAiService;
    protected final RedissonClient redissonClient;
    protected final ObjectMapper objectMapper;

    public WorkFlow(
        UserMapper userMapper,
        AiService aiService,
        StreamAiService streamAiService,
        RedissonClient redissonClient,
        KnowledgeRepository knowledgeRepository,
        NodeMapper nodeMapper,
        ConversationMapper conversationMapper,
        AiTaskMapper aiTaskMapper,
        ObjectMapper objectMapper
    ) {
        this.userMapper = userMapper;
        this.aiService = aiService;
        this.streamAiService = streamAiService;
        this.redissonClient = redissonClient;
        this.knowledgeRepository = knowledgeRepository;
        this.nodeMapper = nodeMapper;
        this.conversationMapper = conversationMapper;
        this.aiTaskMapper = aiTaskMapper;
        this.objectMapper = objectMapper;
    }

    public final void execute(WorkContext workContext) {
        // 参数校验并初始化
        Boolean isValid = validateAndInit(workContext);
        if (!isValid) return;
        // 更新任务状态
        updateTaskToProcessing(workContext);
        // 更新conversation
        Conversation conversation = workContext.getConversation();
        // 更新现在的时间戳
        conversation.setUpdatedAt(System.currentTimeMillis());
        conversationMapper.update(conversation);
        try {
            // 创建初始节点
            createInitNodes(workContext);
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e); // TODO 异常处理
        }
        // title
        updateConvTitle(workContext);

        // 流式输出文本
        try {
            streamChat(workContext, new StreamingResponseHandler<AiMessage>() {

                @Override
                public void onNext(String token) {
                    Node streamNode = workContext.getStreamNode();
                    RStream<String, Object> redisStream = workContext.getRedisStream();
                    Map<String, Object> textData = new ConcurrentHashMap<>();
                    textData.put("nodeId", streamNode.getId());
                    textData.put("content", token);
                    redisStream.add(StreamAddArgs.entries(
                    "type", AiTaskMessageType.TEXT, "data", textData
                    ));
                }

                @Override
                public void onComplete(Response<AiMessage> response) {

                    workContext.setStreamResponse(response);

                    try {
                        createOtherNodesOrUpdateNodeData(workContext);
                    } catch (JsonProcessingException e) {
                        throw new RuntimeException(e); // TODO 异常处理
                    }

                    // 发送结束消息
                    if (workContext.getIsSendDone()) {
                        RStream<String, Object> redisStream = workContext.getRedisStream();
                        redisStream.add(StreamAddArgs.entries(
                        "type", AiTaskMessageType.DONE, "data", ""
                        ));
                    }

                    // update task and nodes
                    updateTaskToSuccess(workContext);
                    AiTask aiTask = workContext.getAiTask();
                    aiTaskMapper.update(aiTask);
                    try {
                        updateStreamNode(workContext);
                    } catch (JsonProcessingException e) {
                        throw new RuntimeException(e); // TODO 异常处理
                    }
                    List<Node> nodesToUpdate = workContext.getNodesToUpdate();
                    nodeMapper.updateDataAndIsDeletedBatch(nodesToUpdate);

                    // 定时删除
                    redissonClient.getQueue(RedisKey.CLEAN_AI_TASK_QUEUE_KEY).add(aiTask.getId());
                }

                @Override
                public void onError(Throwable error) {
                    log.error("流式输出文本失败, taskMessage: {}", workContext.getAiTaskMessage(), error);
                    RStream<String, Object> redisStream = workContext.getRedisStream();
                    redisStream.add(StreamAddArgs.entries(
                    "type", AiTaskMessageType.ERROR, "data", error.getMessage()
                    ));
                    // update task to failed
                    AiTask aiTask = workContext.getAiTask();
                    aiTask.setStatus(AiTaskStatus.FAILED);
                    aiTask.setResult(error.getMessage());
                    aiTaskMapper.update(aiTask);
                }

            });
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e); // TODO 异常处理
        }
    }

    // 获取历史记录
    public LinkedList<ChatMessage> getHistory(WorkContext workContext) {
        Node parentNode = workContext.getParentNode();
        Long convId = parentNode.getConvId();
        List<Node> nodes = nodeMapper.getByConvId(convId);
        Map<Long, Node> nodeMap = nodes.stream().collect(ConcurrentHashMap::new, (map, node) -> map.put(node.getId(), node), ConcurrentHashMap::putAll);
        LinkedList<ChatMessage> messages = new LinkedList<>();
        while (!parentNode.getType().equals(NodeType.ROOT)) {
            TypeReference<Map<String, Object>> parentNodeDataTypeReference = new TypeReference<>() {};
            Map<String, Object> data = null;
            String text = null;
            try {
                data = objectMapper.readValue(parentNode.getData(), parentNodeDataTypeReference);
                text = (String) data.get("text");
            } catch (JsonProcessingException e) {
                // TODO 异常处理
                parentNode = nodeMap.get(parentNode.getParentId());
                continue;
            }
            // 是AI回答节点
            if (
                parentNode.getType().equals(NodeType.ANSWER) ||
                parentNode.getType().equals(NodeType.KNOWLEDGE_DETAIL)
            ) {
                messages.addFirst(new AiMessage(text));
            }
            // 是用户提问节点
            else if (parentNode.getType().equals(NodeType.QUERY)) {
                messages.addFirst(new UserMessage(text));
            }
            parentNode = nodeMap.get(parentNode.getParentId());
        }
        return messages;
    }

    // 参数校验
    public Boolean validateAndInit(WorkContext workContext) {
        AiTaskMessage aiTaskMessage = workContext.getAiTaskMessage();
        User user = userMapper.getById(aiTaskMessage.getUserId());
        if (user == null) {
            log.error("用户不存在, taskMessage: {}", aiTaskMessage);
            return false;
        }
        Conversation conversation = conversationMapper.getById(aiTaskMessage.getConvId());
        if (conversation == null) {
            log.error("对话不存在, taskMessage: {}", aiTaskMessage);
            return false;
        }
        AiTask aiTask = aiTaskMapper.getById(aiTaskMessage.getTaskId());
        if (aiTask == null) {
            log.error("任务不存在, taskMessage: {}", aiTaskMessage);
            return false;
        }
        Node parentNode = nodeMapper.getById(aiTaskMessage.getParentId());
        if (parentNode == null) {
            log.error("父节点不存在, taskMessage: {}", aiTaskMessage);
            return false;
        }
        workContext.setUser(user);
        workContext.setConversation(conversation);
        workContext.setAiTask(aiTask);
        workContext.setParentNode(parentNode);
        workContext.setRedisStream(redissonClient.getStream(RedisKey.AI_TASK_PREFIX + aiTaskMessage.getTaskId()));

        // 获取历史记录
        LinkedList<ChatMessage> history = getHistory(workContext);
        // 只取最后n条
        /*int maxHistory = 12;
        if (history.size() > maxHistory) {
            history = history.subList(history.size() - maxHistory, history.size());
        }*/
        workContext.setHistory(history);
        log.info("历史记录: {}", history);

        return true;
    }

    public void updateTaskToProcessing(WorkContext workContext) {
        AiTask aiTask = workContext.getAiTask();
        aiTask.setStatus(AiTaskStatus.PROCESSING);
        aiTaskMapper.update(aiTask);
    }

    public void updateConvTitle(WorkContext workContext) {
        Conversation conversation = workContext.getConversation();
        AiTaskMessage aiTaskMessage = workContext.getAiTaskMessage();
        RStream<String, Object> redisStream = workContext.getRedisStream();
        Node parentNode = workContext.getParentNode();
        // 如果是新创建的conversation，生成新标题
        if (parentNode.getType().equals(NodeType.ROOT)) {
            String title = aiService.getConvTitle(aiTaskMessage.getPrompt());
            conversation.setTitle(title);
            conversationMapper.update(conversation);
            redisStream.add(StreamAddArgs.entries(
            "type", AiTaskMessageType.TITLE, "data", title
            ));
        }
    }

    public void insertAndPublishNode(WorkContext workContext, Node node, Map<String, Object> nodeData) {
        RStream<String, Object> redisStream = workContext.getRedisStream();
        nodeMapper.insert(node);
        NodeVO nodeVO = new NodeVO();
        BeanUtils.copyProperties(node, nodeVO);
        nodeVO.setData(nodeData);
        nodeVO.setHeight(null);
        nodeVO.setPosition(XYPosition.origin());
        Timestamp now = new Timestamp(System.currentTimeMillis());
        nodeVO.setCreatedAt(now);
        nodeVO.setUpdatedAt(now);
        redisStream.add(StreamAddArgs.entries(
        "type", AiTaskMessageType.NODE, "data", nodeVO
        ));
    }

    public void insertAndPublishNoneStreamNode(WorkContext workContext, Node node, Map<String, Object> nodeData) throws JsonProcessingException {
        insertAndPublishNode(workContext, node, nodeData);
        node.setIsDeleted(false);
        workContext.getNodesToUpdate().add(node);
    }

    public void insertAndPublishStreamNode(WorkContext workContext, Node streamNode, Map<String, Object> streamNodeData) throws JsonProcessingException {
        insertAndPublishNode(workContext, streamNode, streamNodeData);
        workContext.setStreamNode(streamNode);
        workContext.setStreamNodeData(streamNodeData);
    }

    public Long createInitQueryNode(WorkContext workContext) throws JsonProcessingException {
        AiTaskMessage aiTaskMessage = workContext.getAiTaskMessage();

        // 创建用户查询节点
        Map<String, Object> queryNodeData = new ConcurrentHashMap<>();
        queryNodeData.put("title", NodeTitle.QUERY);
        queryNodeData.put("text", aiTaskMessage.getPrompt());
        queryNodeData.put("mode", aiTaskMessage.getType());
        Node queryNode = new Node(
            NodeType.QUERY,
            objectMapper.writeValueAsString(queryNodeData),
            objectMapper.writeValueAsString(XYPosition.origin()),
            aiTaskMessage.getParentId(),
            aiTaskMessage.getUserId(),
            aiTaskMessage.getConvId(),
            true
        );
        insertAndPublishNoneStreamNode(workContext, queryNode, queryNodeData);
        return queryNode.getId();
    }

    public void createInitStreamNode(WorkContext workContext, Long parentNodeId) throws JsonProcessingException {
        AiTaskMessage aiTaskMessage = workContext.getAiTaskMessage();

        // 创建AI回答节点
        Map<String, Object> answerNodeData = new ConcurrentHashMap<>();
        answerNodeData.put("title", NodeTitle.ANSWER);
        answerNodeData.put("text", "");
        Node answerNode = new Node(
                NodeType.ANSWER,
                objectMapper.writeValueAsString(answerNodeData),
                objectMapper.writeValueAsString(XYPosition.origin()),
                parentNodeId,
                aiTaskMessage.getUserId(),
                aiTaskMessage.getConvId(),
                true
        );
        insertAndPublishStreamNode(workContext, answerNode, answerNodeData);
    }

    public void createInitNodes(WorkContext workContext) throws JsonProcessingException {
        Long queryNodeId = createInitQueryNode(workContext);
        createInitStreamNode(workContext, queryNodeId);
    }

    public void streamChat(WorkContext workContext, StreamingResponseHandler<AiMessage> handler) throws JsonProcessingException {
        AiTaskMessage aiTaskMessage = workContext.getAiTaskMessage();
        LinkedList<ChatMessage> history = workContext.getHistory();
        streamAiService.generalChat(history, aiTaskMessage.getPrompt(), aiTaskMessage.getModel(), handler);
    }

    public void createOtherNodesOrUpdateNodeData(WorkContext workContext) throws JsonProcessingException {}

    public void updateTaskToSuccess(WorkContext workContext) {
        AiTask aiTask = workContext.getAiTask();
        Response<AiMessage> streamResponse = workContext.getStreamResponse();
        AtomicLong tokens = workContext.getTokens();
        tokens.addAndGet(Long.valueOf(streamResponse.tokenUsage().totalTokenCount()));
        aiTask.setStatus(AiTaskStatus.SUCCESS);
        aiTask.setResult(streamResponse.content().text());
        aiTask.setTokens(tokens.get());
    }

    public void updateStreamNode(WorkContext workContext) throws JsonProcessingException {
        Response<AiMessage> streamResponse = workContext.getStreamResponse();
        Node streamNode = workContext.getStreamNode();
        Map<String, Object> streamNodeData = workContext.getStreamNodeData();
        streamNodeData.put("text", streamResponse.content().text());
        streamNode.setIsDeleted(false);
        streamNode.setData(objectMapper.writeValueAsString(streamNodeData));
        workContext.getNodesToUpdate().add(streamNode);
    }

}

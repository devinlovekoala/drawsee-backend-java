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
import java.util.ArrayList;
import com.fasterxml.jackson.databind.JsonNode;

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
    protected final AiService aiService;
    protected final StreamAiService streamAiService;
    protected final RedissonClient redissonClient;
    protected final ObjectMapper objectMapper;

    public WorkFlow(
        UserMapper userMapper,
        AiService aiService,
        StreamAiService streamAiService,
        RedissonClient redissonClient,
        NodeMapper nodeMapper,
        ConversationMapper conversationMapper,
        AiTaskMapper aiTaskMapper,
        ObjectMapper objectMapper
    ) {
        this.userMapper = userMapper;
        this.aiService = aiService;
        this.streamAiService = streamAiService;
        this.redissonClient = redissonClient;
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
        conversation.setUpdatedAt(new Timestamp(System.currentTimeMillis()));
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
                parentNode.getType().equals(NodeType.ANSWER_POINT) ||
                parentNode.getType().equals(NodeType.ANSWER_DETAIL) ||
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
        
        // 判断是否为通用对话类型，如果是则创建回答角度节点，否则创建普通回答节点
        if (AiTaskType.GENERAL.equals(aiTaskMessage.getType())) {
            Node answerNode = new Node(
                NodeType.ANSWER_POINT,
                objectMapper.writeValueAsString(answerNodeData),
                objectMapper.writeValueAsString(XYPosition.origin()),
                parentNodeId,
                aiTaskMessage.getUserId(),
                aiTaskMessage.getConvId(),
                true
            );
            answerNodeData.put("title", NodeTitle.ANSWER_POINT);
            answerNodeData.put("subtype", NodeSubType.ANSWER_POINT);
            insertAndPublishStreamNode(workContext, answerNode, answerNodeData);
        } else {
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
    }

    public void createInitNodes(WorkContext workContext) throws JsonProcessingException {
        Long queryNodeId = createInitQueryNode(workContext);
        createInitStreamNode(workContext, queryNodeId);
    }

    public void streamChat(WorkContext workContext, StreamingResponseHandler<AiMessage> handler) throws JsonProcessingException {
        AiTaskMessage aiTaskMessage = workContext.getAiTaskMessage();
        LinkedList<ChatMessage> history = workContext.getHistory();
        
        // 如果是通用对话类型，则使用answerPointChat方法生成回答角度
        if (AiTaskType.GENERAL.equals(aiTaskMessage.getType())) {
            streamAiService.answerPointChat(history, aiTaskMessage.getPrompt(), aiTaskMessage.getModel(), handler);
        } else {
            // 其他情况使用原有的generalChat方法
            streamAiService.generalChat(history, aiTaskMessage.getPrompt(), aiTaskMessage.getModel(), handler);
        }
    }

    public void createOtherNodesOrUpdateNodeData(WorkContext workContext) throws JsonProcessingException {
        // 如果是通用对话类型，则创建回答角度相关节点
        AiTaskMessage aiTaskMessage = workContext.getAiTaskMessage();
        if (AiTaskType.GENERAL.equals(aiTaskMessage.getType())) {
            createAnswerPointNodes(workContext);
        }
    }
    
    /**
     * 创建回答角度节点
     * @param workContext 工作上下文
     * @throws JsonProcessingException JSON处理异常
     */
    protected void createAnswerPointNodes(WorkContext workContext) throws JsonProcessingException {
        Node streamNode = workContext.getStreamNode();
        Response<AiMessage> streamResponse = workContext.getStreamResponse();
        AiTaskMessage aiTaskMessage = workContext.getAiTaskMessage();
        
        String responseText = streamResponse.content().text();
        
        try {
            // 尝试先用JSON方式解析（兼容旧格式）
            try {
                JsonNode jsonNode = objectMapper.readTree(responseText);
                // 判断是否为数组
                if (jsonNode.isArray() && jsonNode.size() > 0) {
                    for (JsonNode pointNode : jsonNode) {
                        String title = pointNode.has("title") ? pointNode.get("title").asText() : "未知角度";
                        String description = pointNode.has("description") ? pointNode.get("description").asText() : "";
                        
                        createSingleAnswerPointNode(workContext, streamNode, aiTaskMessage, title, description);
                    }
                    return; // 成功用JSON解析，返回
                }
            } catch (Exception jsonEx) {
                // JSON解析失败，尝试使用文本方式解析
                log.info("JSON解析回答角度失败，尝试使用文本方式解析");
            }
            
            // 使用文本方式解析（新格式）
            String[] lines = responseText.split("\n");
            String currentTitle = null;
            String currentDescription = null;
            
            for (int i = 0; i < lines.length; i++) {
                String line = lines[i].trim();
                
                if (line.isEmpty()) {
                    // 如果是空行，且已有标题和描述，创建节点
                    if (currentTitle != null && currentDescription != null) {
                        createSingleAnswerPointNode(workContext, streamNode, aiTaskMessage, currentTitle, currentDescription);
                        currentTitle = null;
                        currentDescription = null;
                    }
                    continue;
                }
                
                // 匹配"角度X：[标题]"格式
                if (line.matches("^角度\\d+：.+")) {
                    // 如果已有标题和描述，先创建之前的节点
                    if (currentTitle != null && currentDescription != null) {
                        createSingleAnswerPointNode(workContext, streamNode, aiTaskMessage, currentTitle, currentDescription);
                    }
                    
                    // 提取标题
                    currentTitle = line.substring(line.indexOf("：") + 1).trim();
                    currentDescription = null;
                } 
                // 如果有标题但没有描述，当前行作为描述
                else if (currentTitle != null && currentDescription == null) {
                    currentDescription = line;
                }
            }
            
            // 处理最后一个角度
            if (currentTitle != null && currentDescription != null) {
                createSingleAnswerPointNode(workContext, streamNode, aiTaskMessage, currentTitle, currentDescription);
            }
        } catch (Exception e) {
            log.error("解析回答角度失败: {}", responseText, e);
        }
    }

    /**
     * 创建单个回答角度节点
     * @param workContext 工作上下文
     * @param streamNode 流式节点
     * @param aiTaskMessage AI任务消息
     * @param title 标题
     * @param description 描述
     * @throws JsonProcessingException JSON处理异常
     */
    private void createSingleAnswerPointNode(WorkContext workContext, Node streamNode, AiTaskMessage aiTaskMessage, String title, String description) throws JsonProcessingException {
        Map<String, Object> answerPointNodeData = new ConcurrentHashMap<>();
        answerPointNodeData.put("title", title);
        answerPointNodeData.put("text", description);
        answerPointNodeData.put("subtype", NodeSubType.ANSWER_POINT);
        
        Node answerPointNode = new Node(
            NodeType.ANSWER_POINT,
            objectMapper.writeValueAsString(answerPointNodeData),
            objectMapper.writeValueAsString(XYPosition.origin()),
            streamNode.getId(),
            aiTaskMessage.getUserId(),
            aiTaskMessage.getConvId(),
            true
        );
        
        try {
            // 使用传入的workContext
            insertAndPublishNoneStreamNode(workContext, answerPointNode, answerPointNodeData);
        } catch (Exception e) {
            log.error("创建回答角度节点失败: {}", e.getMessage(), e);
        }
    }

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

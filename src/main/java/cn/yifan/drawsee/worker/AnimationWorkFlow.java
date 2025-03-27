package cn.yifan.drawsee.worker;

import cn.yifan.drawsee.constant.NodeTitle;
import cn.yifan.drawsee.constant.AiTaskMessageType;
import cn.yifan.drawsee.constant.NodeSubType;
import cn.yifan.drawsee.constant.NodeType;
import cn.yifan.drawsee.mapper.AiTaskMapper;
import cn.yifan.drawsee.mapper.ConversationMapper;
import cn.yifan.drawsee.mapper.NodeMapper;
import cn.yifan.drawsee.mapper.UserMapper;
import cn.yifan.drawsee.pojo.XYPosition;
import cn.yifan.drawsee.pojo.entity.Node;
import cn.yifan.drawsee.pojo.rabbit.AiTaskMessage;
import cn.yifan.drawsee.pojo.rabbit.AnimationTaskMessage;
import cn.yifan.drawsee.pojo.rabbit.LinkedQueue;
import cn.yifan.drawsee.repository.KnowledgeRepository;
import cn.yifan.drawsee.service.base.AiService;
import cn.yifan.drawsee.service.base.PromptService;
import cn.yifan.drawsee.service.base.StreamAiService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.StreamingResponseHandler;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.output.Response;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RStream;
import org.redisson.api.RedissonClient;
import org.redisson.api.stream.StreamAddArgs;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * @FileName AnimationWorkFlow
 * @Description
 * @Author yifan
 * @date 2025-03-20 22:37
 **/

@Slf4j
@Service
public class AnimationWorkFlow extends WorkFlow {

    private final PromptService promptService;
    private final ChatLanguageModel deepseekV3ChatLanguageModel;
    private final List<LinkedQueue> animationTaskQueues;
    private final RabbitTemplate rabbitTemplate;

    public AnimationWorkFlow(
        UserMapper userMapper,
        AiService aiService,
        StreamAiService streamAiService,
        RedissonClient redissonClient,
        KnowledgeRepository knowledgeRepository,
        NodeMapper nodeMapper,
        ConversationMapper conversationMapper,
        AiTaskMapper aiTaskMapper,
        ObjectMapper objectMapper,
        PromptService promptService,
        ChatLanguageModel deepseekV3ChatLanguageModel,
        List<LinkedQueue> animationTaskQueues,
        RabbitTemplate rabbitTemplate
    ) {
        super(userMapper, aiService, streamAiService, redissonClient, knowledgeRepository, nodeMapper, conversationMapper, aiTaskMapper, objectMapper);
        this.promptService = promptService;
        this.deepseekV3ChatLanguageModel = deepseekV3ChatLanguageModel;
        this.animationTaskQueues = animationTaskQueues;
        this.rabbitTemplate = rabbitTemplate;
    }

    @Override
    public Boolean validateAndInit(WorkContext workContext) {
        workContext.setIsSendDone(false);
        return super.validateAndInit(workContext);
    }

    @Override
    public void streamChat(WorkContext workContext, StreamingResponseHandler<AiMessage> handler) throws JsonProcessingException {
        AiTaskMessage aiTaskMessage = workContext.getAiTaskMessage();
        LinkedList<ChatMessage> history = workContext.getHistory();
        streamAiService.animationChat(history, aiTaskMessage.getPrompt(), handler);
    }

    @Override
    public void createOtherNodesOrUpdateNodeData(WorkContext workContext) throws JsonProcessingException {
        RStream<String, Object> redisStream = workContext.getRedisStream();
        AiTaskMessage aiTaskMessage = workContext.getAiTaskMessage();
        Node streamNode = workContext.getStreamNode();
        AtomicLong tokens = workContext.getTokens();
        // 创建动画节点
        Map<String, Object> animationNodeData = new ConcurrentHashMap<>();
        animationNodeData.put("title", NodeTitle.GENERATED_ANIMATION);
        animationNodeData.put("subtype", NodeSubType.GENERATED_ANIMATION);
        animationNodeData.put("progress", "开始生成动画...");
        Node animationNode = new Node(
            NodeType.RESOURCE,
            objectMapper.writeValueAsString(animationNodeData),
            objectMapper.writeValueAsString(XYPosition.origin()),
            streamNode.getId(),
            aiTaskMessage.getUserId(),
            aiTaskMessage.getConvId(),
            true
        );
        insertAndPublishNoneStreamNode(workContext, animationNode, animationNodeData);

        // 动画代码生成工作流
        Map<String, Object> data = new ConcurrentHashMap<>();
        data.put("progress", "正在生成动画分镜...");
        data.put("nodeId", animationNode.getId());
        redisStream.add(StreamAddArgs.entries(
        "type", AiTaskMessageType.DATA,
        "data", data
        ));

        String question = aiTaskMessage.getPrompt();
        String animationShotTextListPrompt = promptService.getAnimationShotTextListPrompt(question);
        Response<AiMessage> animationShotTextListResponse = deepseekV3ChatLanguageModel.generate(UserMessage.from(animationShotTextListPrompt));
        tokens.addAndGet(animationShotTextListResponse.tokenUsage().totalTokenCount());
        String animationShotTextListResult = animationShotTextListResponse.content().text();
        TypeReference<List<Map<String, String>>> typeReference = new TypeReference<>() {};
        List<Map<String, String>> animationShotTextList = objectMapper.readValue(animationShotTextListResult, typeReference);

        Map<Integer, Map<String, String>> animationShotInfoMap = new ConcurrentHashMap<>();

        log.info("动画分镜生成成功：{}", animationShotTextList);

        data.put("progress", "正在生成动画代码...");
        redisStream.add(StreamAddArgs.entries(
        "type", AiTaskMessageType.DATA,
        "data", data
        ));

        // 创建CompletableFuture列表
        List<CompletableFuture<Void>> futures = new ArrayList<>();

        for (int i = 0; i < animationShotTextList.size(); i++) {
            Map<String, String> animationShotText = animationShotTextList.get(i);
            String shotDescription = animationShotText.get("shotDescription");
            String shotScript = animationShotText.get("shotScript");
            final int index = i + 1; // 创建final变量用于lambda表达式

            // 为每个镜头创建异步任务
            CompletableFuture<Void> future = CompletableFuture.runAsync(() -> {
                String animationShotCodePrompt = promptService.getAnimationShotCodePrompt(shotDescription, shotScript);
                Response<AiMessage> animationShotCodeResponse = deepseekV3ChatLanguageModel.generate(UserMessage.from(animationShotCodePrompt));
                tokens.addAndGet(animationShotCodeResponse.tokenUsage().totalTokenCount());
                String animationShotCodeResult = animationShotCodeResponse.content().text();
                Map<String, String> animationShotInfo = new ConcurrentHashMap<>();
                animationShotInfo.put("镜头描述：", shotDescription);
                animationShotInfo.put("镜头脚本：", shotScript);
                animationShotInfo.put("manim代码：", animationShotCodeResult);
                animationShotInfoMap.put(index, animationShotInfo);

                log.info("第{}个动画镜头代码生成成功：{}", index, animationShotInfo);
            });
            futures.add(future);
        }

        // 等待所有异步任务完成
        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();

        // 根据animationShotInfoMap的key排序获取animationShotInfoList
        List<Map<String, String>> animationShotInfoList = animationShotInfoMap.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .map(Map.Entry::getValue)
                .toList();

        String animationShotMergeCodePrompt = promptService.getAnimationShotMergeCodePrompt(animationShotInfoList.toString());
        Response<AiMessage> animationShotMergeCodeResponse = deepseekV3ChatLanguageModel.generate(UserMessage.from(animationShotMergeCodePrompt));
        tokens.addAndGet(animationShotMergeCodeResponse.tokenUsage().totalTokenCount());
        String animationShotMergeCodeResult = animationShotMergeCodeResponse.content().text();

        log.info("动画最终代码合并成功：{}", animationShotMergeCodeResult);

        // 取animationShotMergeCodeResult中```python和```之间的内容
        // 去掉animationShotMergeCodeResult前九个字符和最后三个字符
        String code = animationShotMergeCodeResult.substring(9, animationShotMergeCodeResult.length() - 3);

        // 渲染动画

        data.put("progress", "开始渲染动画...");
        redisStream.add(StreamAddArgs.entries(
        "type", AiTaskMessageType.DATA,
        "data", data
        ));

        // 随机选取队列
        LinkedQueue queue = getRandomQueue();
        // 创建AnimationTaskMessage
        AnimationTaskMessage animationTaskMessage = new AnimationTaskMessage(
            aiTaskMessage.getTaskId(),
            animationNode.getId(),
            code
        );
        // 发送到RabbitMQ
        rabbitTemplate.convertAndSend(

            queue.getExchangeName(),
            queue.getRoutingKey(),
            animationTaskMessage
        );
    }

    // 随机选取队列
    private LinkedQueue getRandomQueue() {
        Random random = new Random();
        int randomIndex = random.nextInt(animationTaskQueues.size());
        return animationTaskQueues.get(randomIndex);
    }
}

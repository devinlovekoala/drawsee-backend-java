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
import cn.yifan.drawsee.service.base.AiService;
import cn.yifan.drawsee.service.base.PromptService;
import cn.yifan.drawsee.service.base.StreamAiService;
import cn.yifan.drawsee.service.business.ContextBudgetManager;
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
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.Map;
import java.util.List;
import java.util.ArrayList;
import java.util.concurrent.atomic.AtomicInteger;

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
        NodeMapper nodeMapper,
        ConversationMapper conversationMapper,
        AiTaskMapper aiTaskMapper,
        ObjectMapper objectMapper,
        PromptService promptService,
        ChatLanguageModel deepseekV3ChatLanguageModel,
        List<LinkedQueue> animationTaskQueues,
        RabbitTemplate rabbitTemplate,
        ContextBudgetManager contextBudgetManager
    ) {
        super(userMapper, aiService, streamAiService, redissonClient, nodeMapper, conversationMapper, aiTaskMapper, objectMapper, contextBudgetManager);
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
        LinkedList<ChatMessage> history = applyHistoryBudget(
            workContext,
            planContextBudget(workContext, aiTaskMessage.getPrompt())
        );
        String model = aiTaskMessage.getModel();
        streamAiService.animationChat(history, aiTaskMessage.getPrompt(), model, handler);
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
        try {
            Map<String, Object> data = new ConcurrentHashMap<>();
            data.put("progress", "正在生成动画分镜...");
            data.put("nodeId", animationNode.getId());
            redisStream.add(StreamAddArgs.entries(
            "type", AiTaskMessageType.DATA,
            "data", data
            ));

            String question = aiTaskMessage.getPrompt();
            log.info("开始为问题生成动画分镜: {}", question);
            String animationShotTextListPrompt = promptService.getAnimationShotTextListPrompt(question);
            Response<AiMessage> animationShotTextListResponse = deepseekV3ChatLanguageModel.generate(UserMessage.from(animationShotTextListPrompt));
            tokens.addAndGet(animationShotTextListResponse.tokenUsage().totalTokenCount());
            String animationShotTextListResult = animationShotTextListResponse.content().text();
            
            log.info("动画分镜原始结果: {}", animationShotTextListResult);
            
            // 尝试解析动画分镜列表
            List<Map<String, String>> animationShotTextList;
            try {
                TypeReference<List<Map<String, String>>> typeReference = new TypeReference<>() {};
                animationShotTextList = objectMapper.readValue(animationShotTextListResult, typeReference);
                log.info("成功解析动画分镜列表，共{}个分镜", animationShotTextList.size());
            } catch (Exception e) {
                log.error("动画分镜列表解析失败，将使用备用动画: {}", e.getMessage(), e);
                String fallbackCode = generateFallbackAnimation(aiTaskMessage.getPrompt());
                data.put("progress", "分镜解析失败，使用备用方案渲染...");
                data.put("errorDetail", "分镜解析失败: " + e.getMessage());
                redisStream.add(StreamAddArgs.entries(
                    "type", AiTaskMessageType.DATA,
                    "data", data
                ));
                sendAnimationTaskToRabbitMQ(aiTaskMessage, animationNode, fallbackCode, redisStream, workContext);
                return;
            }

            if (animationShotTextList.isEmpty()) {
                log.error("动画分镜列表为空，将使用备用动画");
                String fallbackCode = generateFallbackAnimation(aiTaskMessage.getPrompt());
                data.put("progress", "分镜列表为空，使用备用方案渲染...");
                data.put("errorDetail", "分镜列表为空");
                redisStream.add(StreamAddArgs.entries(
                    "type", AiTaskMessageType.DATA,
                    "data", data
                ));
                sendAnimationTaskToRabbitMQ(aiTaskMessage, animationNode, fallbackCode, redisStream, workContext);
                return;
            }

            Map<Integer, Map<String, String>> animationShotInfoMap = new ConcurrentHashMap<>();

            log.info("开始生成动画代码，分镜数量: {}", animationShotTextList.size());
            data.put("progress", "正在生成动画代码...");
            redisStream.add(StreamAddArgs.entries(
            "type", AiTaskMessageType.DATA,
            "data", data
            ));

            // 创建CompletableFuture列表
            List<CompletableFuture<Void>> futures = new ArrayList<>();

            // 创建失败计数器和错误信息收集器
            AtomicInteger failedCount = new AtomicInteger(0);
            ConcurrentHashMap<Integer, String> errorMessages = new ConcurrentHashMap<>();

            for (int i = 0; i < animationShotTextList.size(); i++) {
                Map<String, String> animationShotText = animationShotTextList.get(i);
                String shotDescription = animationShotText.get("shotDescription");
                String shotScript = animationShotText.get("shotScript");
                final int index = i + 1; // 创建final变量用于lambda表达式

                if (shotDescription == null || shotScript == null) {
                    log.error("第{}个分镜缺少必要信息: shotDescription={}, shotScript={}", 
                            index, shotDescription, shotScript);
                    failedCount.incrementAndGet();
                    errorMessages.put(index, "分镜缺少必要信息");
                    continue;
                }

                // 为每个镜头创建异步任务
                CompletableFuture<Void> future = CompletableFuture.runAsync(() -> {
                    try {
                        log.info("开始生成第{}个分镜的代码", index);
                        String animationShotCodePrompt = promptService.getAnimationShotCodePrompt(shotDescription, shotScript);
                        Response<AiMessage> animationShotCodeResponse = deepseekV3ChatLanguageModel.generate(UserMessage.from(animationShotCodePrompt));
                        tokens.addAndGet(animationShotCodeResponse.tokenUsage().totalTokenCount());
                        String animationShotCodeResult = animationShotCodeResponse.content().text();
                        
                        // 检查是否返回了有效的Python代码
                        if (!animationShotCodeResult.contains("```python") || !animationShotCodeResult.contains("```")) {
                            log.error("第{}个分镜返回的不是有效Python代码: {}", index, animationShotCodeResult);
                            failedCount.incrementAndGet();
                            errorMessages.put(index, "返回的不是有效Python代码");
                            return;
                        }
                        
                        // 执行代码语法检查
                        String checkedCode;
                        try {
                            checkedCode = checkAndFixPythonCode(animationShotCodeResult);
                            log.info("第{}个分镜代码语法检查完成", index);
                        } catch (Exception e) {
                            log.error("第{}个分镜代码语法检查失败: {}", index, e.getMessage(), e);
                            failedCount.incrementAndGet();
                            errorMessages.put(index, "代码语法检查失败: " + e.getMessage());
                            return;
                        }
                        
                        Map<String, String> animationShotInfo = new ConcurrentHashMap<>();
                        animationShotInfo.put("镜头描述：", shotDescription);
                        animationShotInfo.put("镜头脚本：", shotScript);
                        animationShotInfo.put("manim代码：", checkedCode);
                        animationShotInfoMap.put(index, animationShotInfo);

                        log.info("第{}个动画镜头代码生成成功", index);
                    } catch (Exception e) {
                        log.error("第{}个分镜代码生成过程中出现异常: {}", index, e.getMessage(), e);
                        failedCount.incrementAndGet();
                        errorMessages.put(index, "生成过程异常: " + e.getMessage());
                    }
                });
                futures.add(future);
            }

            // 等待所有异步任务完成
            CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
            
            // 检查是否所有分镜都生成失败
            if (failedCount.get() == animationShotTextList.size()) {
                log.error("所有分镜代码生成都失败了，错误信息: {}", errorMessages);
                String fallbackCode = generateFallbackAnimation(aiTaskMessage.getPrompt());
                data.put("progress", "所有分镜代码生成失败，使用备用方案渲染...");
                data.put("errorDetail", errorMessages.toString());
                redisStream.add(StreamAddArgs.entries(
                    "type", AiTaskMessageType.DATA,
                    "data", data
                ));
                sendAnimationTaskToRabbitMQ(aiTaskMessage, animationNode, fallbackCode, redisStream, workContext);
                return;
            }
            
            // 检查是否有部分分镜生成失败
            if (failedCount.get() > 0) {
                log.warn("{}个分镜代码生成失败，将使用成功生成的{}个分镜继续", 
                        failedCount.get(), animationShotTextList.size() - failedCount.get());
                data.put("warningDetail", String.format("%d个分镜生成失败: %s", 
                        failedCount.get(), errorMessages.toString()));
                redisStream.add(StreamAddArgs.entries(
                    "type", AiTaskMessageType.DATA,
                    "data", data
                ));
            }

            // 根据animationShotInfoMap的key排序获取animationShotInfoList
            List<Map<String, String>> animationShotInfoList = animationShotInfoMap.entrySet().stream()
                    .sorted(Map.Entry.comparingByKey())
                    .map(Map.Entry::getValue)
                    .toList();

            if (animationShotInfoList.isEmpty()) {
                log.error("没有成功生成的分镜代码，将使用备用动画");
                String fallbackCode = generateFallbackAnimation(aiTaskMessage.getPrompt());
                data.put("progress", "没有成功生成分镜代码，使用备用方案渲染...");
                redisStream.add(StreamAddArgs.entries(
                    "type", AiTaskMessageType.DATA,
                    "data", data
                ));
                sendAnimationTaskToRabbitMQ(aiTaskMessage, animationNode, fallbackCode, redisStream, workContext);
                return;
            }

            log.info("开始合并分镜代码，共{}个成功生成的分镜", animationShotInfoList.size());
            data.put("progress", "正在合并分镜代码...");
            redisStream.add(StreamAddArgs.entries(
                "type", AiTaskMessageType.DATA,
                "data", data
            ));

            String animationShotMergeCodePrompt = promptService.getAnimationShotMergeCodePrompt(animationShotInfoList.toString());
            Response<AiMessage> animationShotMergeCodeResponse = deepseekV3ChatLanguageModel.generate(UserMessage.from(animationShotMergeCodePrompt));
            tokens.addAndGet(animationShotMergeCodeResponse.tokenUsage().totalTokenCount());
            String animationShotMergeCodeResult = animationShotMergeCodeResponse.content().text();

            log.info("动画最终代码合并返回结果: {}", animationShotMergeCodeResult);

            // 检查合并结果是否是有效的Python代码
            if (!animationShotMergeCodeResult.contains("```python") || !animationShotMergeCodeResult.contains("```")) {
                log.error("合并结果不是有效的Python代码，将使用备用动画");
                String fallbackCode = generateFallbackAnimation(aiTaskMessage.getPrompt());
                data.put("progress", "合并结果无效，使用备用方案渲染...");
                data.put("errorDetail", "合并后不是有效Python代码");
                redisStream.add(StreamAddArgs.entries(
                    "type", AiTaskMessageType.DATA,
                    "data", data
                ));
                sendAnimationTaskToRabbitMQ(aiTaskMessage, animationNode, fallbackCode, redisStream, workContext);
                return;
            }

            // 取animationShotMergeCodeResult中```python和```之间的内容
            String code;
            try {
                // 尝试提取代码块内容
                int startIndex = animationShotMergeCodeResult.indexOf("```python") + 9;
                int endIndex = animationShotMergeCodeResult.lastIndexOf("```");
                
                if (startIndex <= 9 || endIndex <= 0 || endIndex <= startIndex) {
                    throw new IllegalArgumentException("无法提取Python代码块，格式不正确");
                }
                
                code = animationShotMergeCodeResult.substring(startIndex, endIndex).trim();
                log.info("成功提取到Python代码，长度为{}字符", code.length());
            } catch (Exception e) {
                log.error("提取Python代码时出错: {}", e.getMessage(), e);
                String fallbackCode = generateFallbackAnimation(aiTaskMessage.getPrompt());
                data.put("progress", "代码提取失败，使用备用方案渲染...");
                data.put("errorDetail", "代码提取失败: " + e.getMessage());
                redisStream.add(StreamAddArgs.entries(
                    "type", AiTaskMessageType.DATA,
                    "data", data
                ));
                sendAnimationTaskToRabbitMQ(aiTaskMessage, animationNode, fallbackCode, redisStream, workContext);
                return;
            }
            
            // 判断代码是否为空
            if (code.trim().isEmpty()) {
                log.error("提取的Python代码为空，将使用备用动画");
                String fallbackCode = generateFallbackAnimation(aiTaskMessage.getPrompt());
                data.put("progress", "提取的代码为空，使用备用方案渲染...");
                redisStream.add(StreamAddArgs.entries(
                    "type", AiTaskMessageType.DATA,
                    "data", data
                ));
                sendAnimationTaskToRabbitMQ(aiTaskMessage, animationNode, fallbackCode, redisStream, workContext);
                return;
            }
            
            // 对最终合并的代码再次进行语法检查
            String finalCode;
            try {
                finalCode = checkAndFixPythonCode(code);
                log.info("最终代码语法检查完成，准备发送渲染任务");
            } catch (Exception e) {
                log.error("最终代码语法检查失败: {}", e.getMessage(), e);
                String fallbackCode = generateFallbackAnimation(aiTaskMessage.getPrompt());
                data.put("progress", "最终代码语法检查失败，使用备用方案渲染...");
                data.put("errorDetail", "语法检查失败: " + e.getMessage());
                redisStream.add(StreamAddArgs.entries(
                    "type", AiTaskMessageType.DATA,
                    "data", data
                ));
                sendAnimationTaskToRabbitMQ(aiTaskMessage, animationNode, fallbackCode, redisStream, workContext);
                return;
            }

            // 渲染动画
            log.info("发送动画渲染任务到RabbitMQ");
            sendAnimationTaskToRabbitMQ(aiTaskMessage, animationNode, finalCode, redisStream, workContext);
            
        } catch (Exception e) {
            log.error("动画生成过程中出现错误：", e);
            
            // 生成一个简单的备用动画代码
            String fallbackCode = generateFallbackAnimation(aiTaskMessage.getPrompt());
            log.info("生成备用动画代码: 长度={}", fallbackCode.length());
            
            Map<String, Object> data = new ConcurrentHashMap<>();
            data.put("progress", "正在使用备用方案渲染...");
            data.put("nodeId", animationNode.getId());
            data.put("errorDetail", "生成过程出错: " + e.getMessage());
            redisStream.add(StreamAddArgs.entries(
                "type", AiTaskMessageType.DATA,
                "data", data
            ));
            
            // 使用备用动画代码进行渲染
            sendAnimationTaskToRabbitMQ(aiTaskMessage, animationNode, fallbackCode, redisStream, workContext);
        }
    }
    
    // 发送动画任务到RabbitMQ
    private void sendAnimationTaskToRabbitMQ(AiTaskMessage aiTaskMessage, Node animationNode, String code, RStream<String, Object> redisStream, WorkContext workContext) {
        Map<String, Object> data = new ConcurrentHashMap<>();
        data.put("progress", "开始渲染动画...");
        data.put("nodeId", animationNode.getId());
        redisStream.add(StreamAddArgs.entries(
            "type", AiTaskMessageType.DATA,
            "data", data
        ));

        try {
            // 随机选取队列
            LinkedQueue queue = getRandomQueue();
            // 创建AnimationTaskMessage
            AnimationTaskMessage animationTaskMessage = new AnimationTaskMessage(
                aiTaskMessage.getTaskId(),
                animationNode.getId(),
                code
            );
            
            log.info("准备发送动画任务到RabbitMQ队列, taskId={}, nodeId={}, queue={}, 代码长度={}", 
                    aiTaskMessage.getTaskId(), animationNode.getId(), queue.getName(), code.length());
            
            // 发送到RabbitMQ
            rabbitTemplate.convertAndSend(
                queue.getExchangeName(),
                queue.getRoutingKey(),
                animationTaskMessage
            );
            
            log.info("动画任务已成功发送到RabbitMQ队列, taskId={}, nodeId={}, queue={}", 
                    aiTaskMessage.getTaskId(), animationNode.getId(), queue.getName());
            
            // 设置任务完成标志，使前端能收到完成通知
            workContext.setIsSendDone(true);
            
            log.info("已设置任务完成标志(isSendDone=true)，前端将收到完成通知");
        } catch (Exception e) {
            log.error("发送动画任务到RabbitMQ失败, taskId={}, nodeId={}, 错误信息: {}", 
                    aiTaskMessage.getTaskId(), animationNode.getId(), e.getMessage(), e);
                    
            // 更新节点状态为生成失败
            data.put("progress", "渲染准备失败，请重试");
            data.put("errorDetail", "发送任务失败: " + e.getMessage());
            redisStream.add(StreamAddArgs.entries(
                "type", AiTaskMessageType.DATA,
                "data", data
            ));
        }
    }
    
    // 生成一个简单的备用动画代码
    private String generateFallbackAnimation(String prompt) {
        // 生成一个非常简单的动画，确保它能够成功渲染
        String fallbackCode = "from manim import *\n\n" +
            "class ManimScene(Scene):\n" +
            "    def construct(self):\n" +
            "        # 显示问题\n" +
            "        title = Tex(r\"问题：" + escapeLatex(prompt) + "\", tex_template=TexTemplateLibrary.ctex)\n" +
            "        title.scale(0.8).to_edge(UP)\n" +
            "        self.play(Write(title))\n" +
            "        self.wait(1)\n\n" +
            "        # 显示说明文字\n" +
            "        explanation = Tex(r\"正在为您生成一个可视化解释。\\\\\\\\\", tex_template=TexTemplateLibrary.ctex)\n" +
            "        explanation.next_to(title, DOWN, buff=1)\n" +
            "        self.play(FadeIn(explanation))\n" +
            "        self.wait(1)\n\n" +
            "        # 显示简单的几何图形\n" +
            "        circle = Circle(radius=2, color=BLUE)\n" +
            "        square = Square(side_length=2, color=GREEN).shift(RIGHT*3)\n" +
            "        arrow = Arrow(circle.get_right(), square.get_left(), color=RED)\n" +
            "        formula = Tex(r\"$E = mc^2$\").shift(DOWN*2)\n\n" +
            "        # 播放动画\n" +
            "        self.play(Create(circle))\n" +
            "        self.wait(0.5)\n" +
            "        self.play(Create(square))\n" +
            "        self.wait(0.5)\n" +
            "        self.play(Create(arrow))\n" +
            "        self.wait(0.5)\n" +
            "        self.play(Write(formula))\n" +
            "        self.wait(2)\n\n" +
            "        # 总结文字\n" +
            "        ending = Tex(r\"这个问题需要通过可视化方式来理解。\\\\\\\\\", tex_template=TexTemplateLibrary.ctex)\n" +
            "        ending.to_edge(DOWN)\n" +
            "        self.play(FadeIn(ending))\n" +
            "        self.wait(2)\n\n" +
            "        # 淡出所有元素\n" +
            "        self.play(FadeOut(title), FadeOut(explanation), FadeOut(circle), \n" +
            "                 FadeOut(square), FadeOut(arrow), FadeOut(formula), FadeOut(ending))\n" +
            "        self.wait(1)";
        
        return fallbackCode;
    }
    
    // 转义LaTeX特殊字符
    private String escapeLatex(String text) {
        return text.replaceAll("\\\\", "\\\\\\\\")   // 反斜杠需要双重转义
                  .replaceAll("_", "\\\\_")         // 下划线
                  .replaceAll("\\$", "\\\\$")       // 美元符号
                  .replaceAll("&", "\\\\&")         // &符号
                  .replaceAll("#", "\\\\#")         // #符号
                  .replaceAll("%", "\\\\%")         // %符号
                  .replaceAll("\\{", "\\\\{")       // 左花括号
                  .replaceAll("\\}", "\\\\}")       // 右花括号
                  .replaceAll("~", "\\\\textasciitilde ")  // 波浪号
                  .replaceAll("\\^", "\\\\textasciicircum "); // 插入符号
    }

    // 检查并修复Python代码中的语法错误，特别是数学表达式中缺少运算符的问题
    private String checkAndFixPythonCode(String code) {
        log.info("开始对Python代码进行语法检查和修复");
        String pythonCode = code;
        
        // 1. 如果输入是带有```python和```的，提取其中的内容
        if (pythonCode.contains("```python") && pythonCode.contains("```")) {
            int startIndex = pythonCode.indexOf("```python") + 9;
            int endIndex = pythonCode.lastIndexOf("```");
            if (startIndex > 9 && endIndex > 0 && endIndex > startIndex) {
                pythonCode = pythonCode.substring(startIndex, endIndex).trim();
                log.info("从代码块中提取Python代码，长度: {}", pythonCode.length());
            }
        }
        
        // 检查代码是否为空
        if (pythonCode.trim().isEmpty()) {
            throw new IllegalArgumentException("Python代码为空");
        }
        
        // 收集已处理的错误
        List<String> fixedIssues = new ArrayList<>();
        
        // 2. 检查并修复类名
        if (!pythonCode.contains("class ManimScene(Scene)")) {
            if (pythonCode.contains("class ") && pythonCode.contains("(Scene)")) {
                String originalClassDef = pythonCode.substring(
                    pythonCode.indexOf("class "), 
                    pythonCode.indexOf("{", pythonCode.indexOf("class ")) != -1 
                        ? pythonCode.indexOf("{", pythonCode.indexOf("class "))
                        : pythonCode.indexOf(":", pythonCode.indexOf("class ")) + 1
                );
                
                String newClassDef = "class ManimScene(Scene):";
                pythonCode = pythonCode.replace(originalClassDef, newClassDef);
                fixedIssues.add("将类名修改为ManimScene");
                log.info("修复: 类名不是ManimScene，已修改");
            }
        }
        
        // 3. 检查Tex组件参数
        if (pythonCode.contains("tex_template=TexTemplateLibrary.ctex tex_template=")) {
            pythonCode = pythonCode.replace("tex_template=TexTemplateLibrary.ctex tex_template=", "tex_template=");
            fixedIssues.add("修复重复的tex_template参数");
            log.info("修复: 删除重复的tex_template参数");
        }
        
        // 4. 检查方法参数缺少逗号的情况
        Pattern paramPattern = Pattern.compile("([a-zA-Z0-9_]+)\\s+([a-zA-Z0-9_]+)\\s*\\)");
        Matcher paramMatcher = paramPattern.matcher(pythonCode);
        StringBuffer sb = new StringBuffer();
        while (paramMatcher.find()) {
            paramMatcher.appendReplacement(sb, paramMatcher.group(1) + ", " + paramMatcher.group(2) + ")");
            fixedIssues.add("在方法参数之间添加缺失的逗号");
            log.info("修复: 参数 {} 和 {} 之间缺少逗号，已添加", paramMatcher.group(1), paramMatcher.group(2));
        }
        paramMatcher.appendTail(sb);
        pythonCode = sb.toString();
        
        // 5. 检查缺少逗号的位置：next_to方法
        Pattern nextToPattern = Pattern.compile("next_to\\([^,)]+\\s+([A-Za-z0-9_]+)\\s+([0-9.]+)\\)");
        Matcher nextToMatcher = nextToPattern.matcher(pythonCode);
        sb = new StringBuffer();
        while (nextToMatcher.find()) {
            String replacement = nextToMatcher.group(0).replace(
                nextToMatcher.group(1) + " " + nextToMatcher.group(2), 
                nextToMatcher.group(1) + ", " + nextToMatcher.group(2)
            );
            nextToMatcher.appendReplacement(sb, replacement);
            fixedIssues.add("在next_to方法参数之间添加缺失的逗号");
            log.info("修复: next_to方法参数之间缺少逗号，已添加");
        }
        nextToMatcher.appendTail(sb);
        pythonCode = sb.toString();
        
        // 6. 检查shift方法缺少逗号
        Pattern shiftPattern = Pattern.compile("shift\\(([A-Za-z]+)\\s+([0-9.]+)\\)");
        Matcher shiftMatcher = shiftPattern.matcher(pythonCode);
        sb = new StringBuffer();
        while (shiftMatcher.find()) {
            shiftMatcher.appendReplacement(sb, "shift(" + shiftMatcher.group(1) + ", " + shiftMatcher.group(2) + ")");
            fixedIssues.add("在shift方法参数之间添加缺失的逗号");
            log.info("修复: shift方法参数之间缺少逗号，已添加");
        }
        shiftMatcher.appendTail(sb);
        pythonCode = sb.toString();
        
        // 7. 检查Transform方法之间缺少逗号
        Pattern transformPattern = Pattern.compile("Transform\\([^,)]+,[^,)]+\\)\\s+Transform");
        Matcher transformMatcher = transformPattern.matcher(pythonCode);
        sb = new StringBuffer();
        while (transformMatcher.find()) {
            String replacement = transformMatcher.group(0).replace(") Transform", "), Transform");
            transformMatcher.appendReplacement(sb, replacement);
            fixedIssues.add("在Transform调用之间添加缺失的逗号");
            log.info("修复: Transform调用之间缺少逗号，已添加");
        }
        transformMatcher.appendTail(sb);
        pythonCode = sb.toString();
        
        // 8. 检查Axes参数配置缺少逗号
        Pattern axesConfigPattern = Pattern.compile("axis_config=\\{[^}]+\\}\\s+([a-zA-Z_]+)");
        Matcher axesConfigMatcher = axesConfigPattern.matcher(pythonCode);
        sb = new StringBuffer();
        while (axesConfigMatcher.find()) {
            axesConfigMatcher.appendReplacement(sb, axesConfigMatcher.group(0).replace("} " + axesConfigMatcher.group(1), "}, " + axesConfigMatcher.group(1)));
            fixedIssues.add("在axis_config后添加缺失的逗号");
            log.info("修复: axis_config后缺少逗号，已添加");
        }
        axesConfigMatcher.appendTail(sb);
        pythonCode = sb.toString();
        
        // 9. 检查引号匹配
        int singleQuotes = 0;
        int doubleQuotes = 0;
        boolean inSingleQuote = false;
        boolean inDoubleQuote = false;
        
        for (int i = 0; i < pythonCode.length(); i++) {
            char c = pythonCode.charAt(i);
            if (c == '\'' && (i == 0 || pythonCode.charAt(i-1) != '\\')) {
                inSingleQuote = !inSingleQuote;
                singleQuotes++;
            } else if (c == '\"' && (i == 0 || pythonCode.charAt(i-1) != '\\')) {
                inDoubleQuote = !inDoubleQuote;
                doubleQuotes++;
            }
        }
        
        if (singleQuotes % 2 != 0) {
            log.warn("引号不匹配: 发现{}个单引号，可能有未闭合的引号", singleQuotes);
        }
        
        if (doubleQuotes % 2 != 0) {
            log.warn("引号不匹配: 发现{}个双引号，可能有未闭合的引号", doubleQuotes);
        }
        
        // 10. 检查括号匹配
        int parentheses = 0;
        int squareBrackets = 0;
        int curlyBraces = 0;
        
        for (char c : pythonCode.toCharArray()) {
            switch (c) {
                case '(': parentheses++; break;
                case ')': parentheses--; break;
                case '[': squareBrackets++; break;
                case ']': squareBrackets--; break;
                case '{': curlyBraces++; break;
                case '}': curlyBraces--; break;
            }
        }
        
        if (parentheses != 0) {
            log.warn("括号不匹配: 圆括号嵌套级别为{}，可能有未闭合的括号", parentheses);
        }
        
        if (squareBrackets != 0) {
            log.warn("括号不匹配: 方括号嵌套级别为{}，可能有未闭合的括号", squareBrackets);
        }
        
        if (curlyBraces != 0) {
            log.warn("括号不匹配: 花括号嵌套级别为{}，可能有未闭合的括号", curlyBraces);
        }
        
        // 11. 检查manim的特定方法调用格式
        // 检查play()方法调用中缺少逗号的模式
        Pattern playMethodPattern = Pattern.compile("self\\.play\\(([^,)]+)\\s+([A-Za-z]+\\([^)]*\\))");
        Matcher playMethodMatcher = playMethodPattern.matcher(pythonCode);
        sb = new StringBuffer();
        while (playMethodMatcher.find()) {
            String replacement = "self.play(" + playMethodMatcher.group(1) + ", " + playMethodMatcher.group(2);
            playMethodMatcher.appendReplacement(sb, replacement);
            fixedIssues.add("在play方法参数之间添加缺失的逗号");
            log.info("修复: play方法参数之间缺少逗号，已添加");
        }
        playMethodMatcher.appendTail(sb);
        pythonCode = sb.toString();
        
        // 12. 确保所有Python行正确缩进
        String[] lines = pythonCode.split("\n");
        StringBuilder indentedCode = new StringBuilder();
        int currentIndent = 0;
        boolean inClassDef = false;
        boolean inMethodDef = false;
        
        for (String line : lines) {
            String trimmedLine = line.trim();
            
            // 跳过空行，保持原样
            if (trimmedLine.isEmpty()) {
                indentedCode.append("\n");
                continue;
            }
            
            // 检测缩进级别变化
            if (trimmedLine.startsWith("class ")) {
                currentIndent = 0;
                inClassDef = true;
            } else if (trimmedLine.startsWith("def ")) {
                if (inClassDef && !inMethodDef) {
                    currentIndent = 4;
                }
                inMethodDef = true;
            } else if (trimmedLine.endsWith(":")) {
                // 如果是控制结构或其他需要缩进的行
                currentIndent += 4;
            } else if (trimmedLine.equals("return") || trimmedLine.startsWith("return ") ||
                      trimmedLine.equals("break") || trimmedLine.equals("continue") ||
                      trimmedLine.equals("pass")) {
                // 这些语句通常标志着一个块的结束
                if (currentIndent >= 4) {
                    // 保持当前缩进，但下一行可能需要减少
                }
            }
            
            // 应用缩进
            for (int i = 0; i < currentIndent; i++) {
                indentedCode.append(" ");
            }
            indentedCode.append(trimmedLine).append("\n");
        }
        
        pythonCode = indentedCode.toString();
        
        // 日志输出修复结果
        if (!fixedIssues.isEmpty()) {
            log.info("Python代码语法检查完成，修复了{}个问题: {}", fixedIssues.size(), fixedIssues);
        } else {
            log.info("Python代码语法检查完成，未发现需要修复的问题");
        }
        
        return pythonCode;
    }

    // 随机选取队列
    private LinkedQueue getRandomQueue() {
        Random random = new Random();
        int randomIndex = random.nextInt(animationTaskQueues.size());
        return animationTaskQueues.get(randomIndex);
    }
}

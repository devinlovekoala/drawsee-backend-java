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
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.Map;
import java.util.HashMap;
import java.util.List;
import java.util.ArrayList;

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
                    
                    // 执行代码语法检查
                    String checkedCode = checkAndFixPythonCode(animationShotCodeResult);
                    
                    Map<String, String> animationShotInfo = new ConcurrentHashMap<>();
                    animationShotInfo.put("镜头描述：", shotDescription);
                    animationShotInfo.put("镜头脚本：", shotScript);
                    animationShotInfo.put("manim代码：", checkedCode);
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
            
            // 对最终合并的代码再次进行语法检查
            code = checkAndFixPythonCode(code);

            // 渲染动画
            sendAnimationTaskToRabbitMQ(aiTaskMessage, animationNode, code, redisStream);
            
        } catch (Exception e) {
            log.error("动画生成过程中出现错误：", e);
            
            // 生成一个简单的备用动画代码
            String fallbackCode = generateFallbackAnimation(aiTaskMessage.getPrompt());
            log.info("生成备用动画代码: {}", fallbackCode);
            
            Map<String, Object> data = new ConcurrentHashMap<>();
            data.put("progress", "正在使用备用方案渲染...");
            data.put("nodeId", animationNode.getId());
            redisStream.add(StreamAddArgs.entries(
                "type", AiTaskMessageType.DATA,
                "data", data
            ));
            
            // 使用备用动画代码进行渲染
            sendAnimationTaskToRabbitMQ(aiTaskMessage, animationNode, fallbackCode, redisStream);
        }
    }
    
    // 发送动画任务到RabbitMQ
    private void sendAnimationTaskToRabbitMQ(AiTaskMessage aiTaskMessage, Node animationNode, String code, RStream<String, Object> redisStream) {
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
            
            log.info("准备发送动画任务到RabbitMQ队列, taskId={}, nodeId={}, queue={}", 
                    aiTaskMessage.getTaskId(), animationNode.getId(), queue.getName());
            
            // 发送到RabbitMQ
            rabbitTemplate.convertAndSend(
                queue.getExchangeName(),
                queue.getRoutingKey(),
                animationTaskMessage
            );
            
            log.info("动画任务已发送到RabbitMQ队列, taskId={}, nodeId={}, queue={}", 
                    aiTaskMessage.getTaskId(), animationNode.getId(), queue.getName());
        } catch (Exception e) {
            log.error("发送动画任务到RabbitMQ失败, taskId={}, nodeId={}, 错误信息: {}", 
                    aiTaskMessage.getTaskId(), animationNode.getId(), e.getMessage(), e);
                    
            // 更新节点状态为生成失败
            data.put("progress", "渲染准备失败，请重试");
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
        log.info("开始进行Python代码语法检查...");
        
        // 提取实际Python代码（如果有```python包装）
        String pythonCode = code;
        if (code.startsWith("```python") && code.endsWith("```")) {
            pythonCode = code.substring(9, code.length() - 3);
        }
        
        // 检查并修复数学表达式中缺少运算符的问题
        // 使用正则表达式查找可能的语法错误模式
        // 模式1: 数字/变量 后跟空格再跟数字/变量，中间可能缺少运算符
        Pattern pattern1 = Pattern.compile("(\\w+)\\s+(\\d+\\.?\\d*|\\w+)");
        Matcher matcher = pattern1.matcher(pythonCode);
        StringBuffer sb = new StringBuffer();
        
        while (matcher.find()) {
            String group = matcher.group();
            // 检查这不是一个变量声明或其他合法语法
            // 排除常见的合法模式如"def construct"、"class ManimScene"等
            if (!group.matches("(def|class|return|import|from|in|as|if|else|elif)\\s+\\w+") && 
                !group.matches("\\w+\\s+(=|==|!=|>=|<=|>|<|\\+=|-=|\\*=|/=).*")) {
                // 如果看起来是缺少运算符的数学表达式
                log.warn("发现可能缺少运算符的表达式: {}", group);
                // 在这里默认添加一个加号，当然这是一个简单修复，可能不适用所有情况
                matcher.appendReplacement(sb, matcher.group(1) + " + " + matcher.group(2));
                log.info("修复为: {}", matcher.group(1) + " + " + matcher.group(2));
            } else {
                matcher.appendReplacement(sb, group);
            }
        }
        matcher.appendTail(sb);
        
        // 模式2: 查找可能缺少操作符的赋值语句，例如 "x = 2 3"
        Pattern pattern2 = Pattern.compile("(\\w+)\\s*=\\s*(\\d+\\.?\\d*|\\w+)\\s+(\\d+\\.?\\d*|\\w+)");
        matcher = pattern2.matcher(sb.toString());
        sb = new StringBuffer();
        
        while (matcher.find()) {
            String group = matcher.group();
            // 检查是否在语句中间缺少运算符
            if (!group.contains("+") && !group.contains("-") && 
                !group.contains("*") && !group.contains("/")) {
                log.warn("发现可能缺少运算符的赋值语句: {}", group);
                // 在数字/变量之间添加加号
                matcher.appendReplacement(sb, matcher.group(1) + " = " + matcher.group(2) + " + " + matcher.group(3));
                log.info("修复为: {}", matcher.group(1) + " = " + matcher.group(2) + " + " + matcher.group(3));
            } else {
                matcher.appendReplacement(sb, group);
            }
        }
        matcher.appendTail(sb);
        
        // 模式3: 查找重复的关键字参数
        // 例如: func(param=value, param=value)
        String fixedCode = sb.toString();
        Pattern pattern3 = Pattern.compile("(\\w+)\\s*\\(([^\\)]+)\\)");
        matcher = pattern3.matcher(fixedCode);
        sb = new StringBuffer();
        
        while (matcher.find()) {
            String functionName = matcher.group(1);
            String params = matcher.group(2);
            
            // 检查参数列表中是否有重复的关键字参数
            Map<String, String> keywordArgs = new HashMap<>();
            List<String> positionalArgs = new ArrayList<>();
            boolean paramsModified = false;
            
            // 简单分割参数
            String[] paramList = params.split(",");
            StringBuilder newParams = new StringBuilder();
            
            for (int i = 0; i < paramList.length; i++) {
                String param = paramList[i].trim();
                if (param.contains("=")) {
                    // 关键字参数
                    String[] parts = param.split("=", 2);
                    String key = parts[0].trim();
                    String value = parts[1].trim();
                    
                    if (keywordArgs.containsKey(key)) {
                        // 发现重复的关键字参数，跳过
                        log.warn("发现重复的关键字参数: {} 在函数 {}", key, functionName);
                        paramsModified = true;
                    } else {
                        keywordArgs.put(key, value);
                        if (newParams.length() > 0) {
                            newParams.append(", ");
                        }
                        newParams.append(key).append("=").append(value);
                    }
                } else {
                    // 位置参数
                    positionalArgs.add(param);
                    if (newParams.length() > 0) {
                        newParams.append(", ");
                    }
                    newParams.append(param);
                }
            }
            
            if (paramsModified) {
                String replacement = functionName + "(" + newParams.toString() + ")";
                log.info("修复重复参数: {} -> {}", matcher.group(0), replacement);
                matcher.appendReplacement(sb, replacement);
            } else {
                matcher.appendReplacement(sb, matcher.group(0));
            }
        }
        matcher.appendTail(sb);
        fixedCode = sb.toString();
        
        // 模式4: 修复LaTeX中常见的错误
        // 检查末尾缺少闭合的括号或引号
        Pattern pattern4 = Pattern.compile("Tex\\(r?\"([^\"]*)\"");
        matcher = pattern4.matcher(fixedCode);
        sb = new StringBuffer();
        
        while (matcher.find()) {
            String texContent = matcher.group(1);
            String fixedTexContent = texContent;
            
            // 检查未闭合的花括号
            int openBraces = 0;
            int closeBraces = 0;
            for (char c : texContent.toCharArray()) {
                if (c == '{') openBraces++;
                if (c == '}') closeBraces++;
            }
            
            if (openBraces > closeBraces) {
                // 添加缺失的闭合花括号
                for (int i = 0; i < (openBraces - closeBraces); i++) {
                    fixedTexContent += "}";
                }
                log.warn("修复LaTeX未闭合的花括号: {} -> {}", texContent, fixedTexContent);
            }
            
            // 检查是否有"There's no line here to end"错误，这通常是由于错误的\\\\用法导致的
            if (fixedTexContent.contains("\\\\") && 
                (fixedTexContent.trim().endsWith("\\\\") || fixedTexContent.contains("\\\\\n"))) {
                // 移除末尾的\\\\或者替换掉换行前的\\\\
                fixedTexContent = fixedTexContent.replaceAll("\\\\\\\\\\s*$", "");
                fixedTexContent = fixedTexContent.replaceAll("\\\\\\\\\\s*\n", "\n");
                log.warn("修复LaTeX中不正确的换行: {} -> {}", texContent, fixedTexContent);
            }
            
            if (!texContent.equals(fixedTexContent)) {
                matcher.appendReplacement(sb, "Tex(r\"" + fixedTexContent + "\"");
            } else {
                matcher.appendReplacement(sb, matcher.group(0));
            }
        }
        matcher.appendTail(sb);
        
        // 模式5: 专门检测并修复重复关键字参数的问题（尤其针对Tex函数）
        Pattern pattern5 = Pattern.compile("Tex\\(([^\\)]+)\\)");
        matcher = pattern5.matcher(sb.toString());
        sb = new StringBuffer();
        
        while (matcher.find()) {
            String texArgs = matcher.group(1);
            
            // 检查是否包含多个tex_template参数
            if (texArgs.contains("tex_template=") && texArgs.indexOf("tex_template=") != texArgs.lastIndexOf("tex_template=")) {
                log.warn("发现Tex函数包含重复的tex_template参数: {}", texArgs);
                
                // 使用简单的字符串处理来保留第一个tex_template参数并移除其他的
                String firstPart = texArgs.substring(0, texArgs.indexOf("tex_template="));
                String texTemplateParam = texArgs.substring(texArgs.indexOf("tex_template="));
                String restPart = "";
                
                if (texTemplateParam.contains(",")) {
                    restPart = texTemplateParam.substring(texTemplateParam.indexOf(",") + 1);
                    texTemplateParam = texTemplateParam.substring(0, texTemplateParam.indexOf(","));
                }
                
                // 从剩余部分移除所有tex_template参数
                StringBuilder cleanRestPart = new StringBuilder();
                String[] parts = restPart.split(",");
                for (String part : parts) {
                    if (!part.trim().startsWith("tex_template=")) {
                        if (cleanRestPart.length() > 0) {
                            cleanRestPart.append(", ");
                        }
                        cleanRestPart.append(part.trim());
                    }
                }
                
                String fixedTexArgs = firstPart + texTemplateParam;
                if (cleanRestPart.length() > 0) {
                    fixedTexArgs += ", " + cleanRestPart;
                }
                
                log.info("修复Tex函数的重复tex_template参数: {} -> {}", texArgs, fixedTexArgs);
                matcher.appendReplacement(sb, "Tex(" + fixedTexArgs + ")");
            } else {
                matcher.appendReplacement(sb, matcher.group(0));
            }
        }
        matcher.appendTail(sb);
        
        // 模式6：修复缺少逗号的Tex函数调用（特别是r"..." 后面缺少逗号的情况）
        Pattern pattern6 = Pattern.compile("Tex\\(r?\"([^\"]*)\"\\s*(\\w+)");
        matcher = pattern6.matcher(sb.toString());
        sb = new StringBuffer();
        
        while (matcher.find()) {
            // 字符串后面紧跟着一个标识符，这说明缺少逗号
            log.warn("发现可能缺少逗号的Tex调用: {}{}...", matcher.group(0), matcher.group(2));
            // 在字符串和标识符之间添加逗号
            matcher.appendReplacement(sb, "Tex(r\"" + matcher.group(1) + "\", " + matcher.group(2));
            log.info("修复为: Tex(r\"{}\"，{}...)", matcher.group(1), matcher.group(2));
        }
        matcher.appendTail(sb);
        
        // 模式7：检查并修复未完成的Tex函数调用语句
        // 例如：Tex(r"文本", tex_template=TexTemplateLibrary.ctex 缺少右括号的情况
        String code7 = sb.toString();
        Pattern pattern7 = Pattern.compile("Tex\\(([^\\)\\(]*)$", Pattern.MULTILINE);  // 在行尾查找未闭合的Tex调用
        matcher = pattern7.matcher(code7);
        sb = new StringBuffer();
        
        while (matcher.find()) {
            String texArgs = matcher.group(1);
            log.warn("发现未完成的Tex函数调用: Tex({}...没有闭合括号", texArgs);
            matcher.appendReplacement(sb, "Tex(" + texArgs + ")");
            log.info("修复为: Tex({})", texArgs);
        }
        matcher.appendTail(sb);
        
        pythonCode = sb.toString();
        
        // 最后一步：检查行内不平衡的括号问题
        Pattern linePattern = Pattern.compile("([^\\n]*?)\\n");
        matcher = linePattern.matcher(pythonCode);
        sb = new StringBuffer();
        
        while (matcher.find()) {
            String line = matcher.group(1);
            // 统计括号计数
            int parenCount = 0;
            for (char c : line.toCharArray()) {
                if (c == '(') parenCount++;
                else if (c == ')') parenCount--;
            }
            
            // 如果左括号多于右括号，并且不是在注释行，添加缺少的右括号
            if (parenCount > 0 && !line.trim().startsWith("#") && !line.contains("'''") && !line.contains("\"\"\"")) {
                log.warn("发现行内括号不平衡: {}", line);
                for (int i = 0; i < parenCount; i++) {
                    line += ")";
                }
                log.info("修复为: {}", line);
            }
            
            matcher.appendReplacement(sb, line + "\n");
        }
        matcher.appendTail(sb);
        
        pythonCode = sb.toString();
        
        // 如果输入是带有```python标记的，那么输出也保持一致
        if (code.startsWith("```python") && code.endsWith("```")) {
            return "```python\n" + pythonCode + "\n```";
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

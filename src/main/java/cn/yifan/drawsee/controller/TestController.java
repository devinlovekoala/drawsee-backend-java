package cn.yifan.drawsee.controller;

import cn.yifan.drawsee.pojo.Result;
import cn.yifan.drawsee.pojo.rabbit.AnimationTaskMessage;
import cn.yifan.drawsee.pojo.rabbit.LinkedQueue;
import cn.yifan.drawsee.service.base.ApiService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Random;

/**
 * @FileName TestController
 * @Description 测试控制器，用于开发和调试
 * @Author devin
 * @date 2025-04-23 20:53
 **/

@Slf4j
@RestController
@RequestMapping("/test")
public class TestController {

    @Autowired
    private ApiService apiService;
    
    @Autowired
    private RabbitTemplate rabbitTemplate;
    
    @Autowired
    private List<LinkedQueue> animationTaskQueues;
    
    /**
     * 测试直接调用Python服务的动画渲染API
     * 
     * @param taskId 任务ID
     * @param nodeId 节点ID
     * @param code Python代码（简单的示例代码）
     * @return 结果
     */
    @GetMapping("/render-animation")
    public Result<String> testRenderAnimation(
            @RequestParam(value = "taskId", defaultValue = "12345") Long taskId,
            @RequestParam(value = "nodeId", defaultValue = "67890") Long nodeId,
            @RequestParam(value = "code", defaultValue = "from manim import *\n\nclass ManimScene(Scene):\n    def construct(self):\n        text = Text(\"Hello, Drawsee!\")\n        self.play(Write(text))\n        self.wait(2)\n        self.play(FadeOut(text))\n") String code
    ) {
        try {
            log.info("尝试直接调用Python渲染服务...");
            apiService.renderAnimation(taskId, nodeId, code);
            return Result.success("渲染请求已发送");
        } catch (Exception e) {
            log.error("渲染请求发送失败", e);
            // 创建一个临时的Result<String>对象
            Result<String> errorResult = new Result<>();
            errorResult.setCode(500);
            errorResult.setMessage("渲染请求发送失败: " + e.getMessage());
            errorResult.setData(null);
            return errorResult;
        }
    }
    
    /**
     * 测试通过RabbitMQ发送动画任务
     * 
     * @param taskId 任务ID
     * @param nodeId 节点ID
     * @param code Python代码（简单的示例代码）
     * @return 结果
     */
    @GetMapping("/send-animation-task")
    public Result<String> testSendAnimationTask(
            @RequestParam(value = "taskId", defaultValue = "12345") Long taskId,
            @RequestParam(value = "nodeId", defaultValue = "67890") Long nodeId,
            @RequestParam(value = "code", defaultValue = "from manim import *\n\nclass ManimScene(Scene):\n    def construct(self):\n        text = Text(\"Hello, Drawsee!\")\n        self.play(Write(text))\n        self.wait(2)\n        self.play(FadeOut(text))\n") String code
    ) {
        try {
            // 随机选取队列
            Random random = new Random();
            int randomIndex = random.nextInt(animationTaskQueues.size());
            LinkedQueue queue = animationTaskQueues.get(randomIndex);
            
            // 创建消息
            AnimationTaskMessage message = new AnimationTaskMessage(taskId, nodeId, code);
            
            log.info("准备发送动画任务到RabbitMQ队列: {}, taskId={}, nodeId={}", 
                    queue.getName(), taskId, nodeId);
            
            // 发送到RabbitMQ
            rabbitTemplate.convertAndSend(
                queue.getExchangeName(),
                queue.getRoutingKey(),
                message
            );
            
            log.info("动画任务已发送到RabbitMQ队列: {}, taskId={}, nodeId={}", 
                    queue.getName(), taskId, nodeId);
                    
            return Result.success("动画任务已发送到RabbitMQ: " + queue.getName());
        } catch (Exception e) {
            log.error("发送动画任务失败", e);
            // 创建一个临时的Result<String>对象
            Result<String> errorResult = new Result<>();
            errorResult.setCode(500);
            errorResult.setMessage("发送动画任务失败: " + e.getMessage());
            errorResult.setData(null);
            return errorResult;
        }
    }
    
    /**
     * 测试复杂的完美动画脚本
     * 
     * @param taskId 任务ID
     * @param nodeId 节点ID
     * @return 结果
     */
    @GetMapping("/test-perfect-script")
    public Result<String> testPerfectScript(
            @RequestParam(value = "taskId", defaultValue = "10001") Long taskId,
            @RequestParam(value = "nodeId", defaultValue = "20001") Long nodeId
    ) {
        try {
            // 读取完美脚本文件
            ClassPathResource resource = new ClassPathResource("test/perfect_animation_script.py");
            String code = new String(resource.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            
            log.info("测试完美动画脚本，脚本长度：{} 字符", code.length());
            
            // 随机选取队列
            Random random = new Random();
            int randomIndex = random.nextInt(animationTaskQueues.size());
            LinkedQueue queue = animationTaskQueues.get(randomIndex);
            
            // 创建消息
            AnimationTaskMessage message = new AnimationTaskMessage(taskId, nodeId, code);
            
            log.info("准备发送完美动画脚本任务到RabbitMQ队列: {}, taskId={}, nodeId={}", 
                    queue.getName(), taskId, nodeId);
            
            // 发送到RabbitMQ
            rabbitTemplate.convertAndSend(
                queue.getExchangeName(),
                queue.getRoutingKey(),
                message
            );
            
            log.info("完美动画脚本任务已发送到RabbitMQ队列: {}, taskId={}, nodeId={}", 
                    queue.getName(), taskId, nodeId);
                    
            return Result.success("完美动画脚本任务已发送到RabbitMQ: " + queue.getName());
        } catch (IOException e) {
            log.error("发送完美动画脚本任务失败", e);
            Result<String> errorResult = new Result<>();
            errorResult.setCode(500);
            errorResult.setMessage("发送完美动画脚本任务失败: " + e.getMessage());
            errorResult.setData(null);
            return errorResult;
        }
    }
    
    /**
     * 测试有问题的动画脚本
     * 
     * @param taskId 任务ID
     * @param nodeId 节点ID
     * @return 结果
     */
    @GetMapping("/test-problematic-script")
    public Result<String> testProblematicScript(
            @RequestParam(value = "taskId", defaultValue = "10002") Long taskId,
            @RequestParam(value = "nodeId", defaultValue = "20002") Long nodeId
    ) {
        try {
            // 读取有问题的脚本文件
            ClassPathResource resource = new ClassPathResource("test/problematic_animation_script.py");
            String code = new String(resource.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            
            log.info("测试有问题的动画脚本，脚本长度：{} 字符", code.length());
            
            // 随机选取队列
            Random random = new Random();
            int randomIndex = random.nextInt(animationTaskQueues.size());
            LinkedQueue queue = animationTaskQueues.get(randomIndex);
            
            // 创建消息
            AnimationTaskMessage message = new AnimationTaskMessage(taskId, nodeId, code);
            
            log.info("准备发送有问题的动画脚本任务到RabbitMQ队列: {}, taskId={}, nodeId={}", 
                    queue.getName(), taskId, nodeId);
            
            // 发送到RabbitMQ
            rabbitTemplate.convertAndSend(
                queue.getExchangeName(),
                queue.getRoutingKey(),
                message
            );
            
            log.info("有问题的动画脚本任务已发送到RabbitMQ队列: {}, taskId={}, nodeId={}", 
                    queue.getName(), taskId, nodeId);
                    
            return Result.success("有问题的动画脚本任务已发送到RabbitMQ: " + queue.getName());
        } catch (IOException e) {
            log.error("发送有问题的动画脚本任务失败", e);
            Result<String> errorResult = new Result<>();
            errorResult.setCode(500);
            errorResult.setMessage("发送有问题的动画脚本任务失败: " + e.getMessage());
            errorResult.setData(null);
            return errorResult;
        }
    }
    
    /**
     * 测试两种脚本同时发送
     * 
     * @return 结果
     */
    @GetMapping("/test-both-scripts")
    public Result<String> testBothScripts() {
        try {
            // 读取完美脚本文件
            ClassPathResource perfectResource = new ClassPathResource("test/perfect_animation_script.py");
            String perfectCode = new String(perfectResource.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            
            // 读取有问题的脚本文件
            ClassPathResource problematicResource = new ClassPathResource("test/problematic_animation_script.py");
            String problematicCode = new String(problematicResource.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            
            log.info("测试两种动画脚本，完美脚本长度：{} 字符，有问题脚本长度：{} 字符", 
                    perfectCode.length(), problematicCode.length());
            
            // 随机选取队列
            Random random = new Random();
            int randomIndex = random.nextInt(animationTaskQueues.size());
            LinkedQueue queue = animationTaskQueues.get(randomIndex);
            
            // 创建消息 - 完美脚本
            Long perfectTaskId = 10003L;
            Long perfectNodeId = 20003L;
            AnimationTaskMessage perfectMessage = new AnimationTaskMessage(perfectTaskId, perfectNodeId, perfectCode);
            
            // 创建消息 - 有问题脚本
            Long problematicTaskId = 10004L;
            Long problematicNodeId = 20004L;
            AnimationTaskMessage problematicMessage = new AnimationTaskMessage(problematicTaskId, problematicNodeId, problematicCode);
            
            // 发送完美脚本任务到RabbitMQ
            log.info("准备发送完美动画脚本任务到RabbitMQ队列: {}, taskId={}, nodeId={}",
                    queue.getName(), perfectTaskId, perfectNodeId);
            
            rabbitTemplate.convertAndSend(
                queue.getExchangeName(),
                queue.getRoutingKey(),
                perfectMessage
            );
            
            log.info("完美动画脚本任务已发送到RabbitMQ队列: {}, taskId={}, nodeId={}", 
                    queue.getName(), perfectTaskId, perfectNodeId);
            
            // 发送有问题脚本任务到RabbitMQ
            log.info("准备发送有问题的动画脚本任务到RabbitMQ队列: {}, taskId={}, nodeId={}", 
                    queue.getName(), problematicTaskId, problematicNodeId);
            
            rabbitTemplate.convertAndSend(
                queue.getExchangeName(),
                queue.getRoutingKey(),
                problematicMessage
            );
            
            log.info("有问题的动画脚本任务已发送到RabbitMQ队列: {}, taskId={}, nodeId={}", 
                    queue.getName(), problematicTaskId, problematicNodeId);
            
            return Result.success("两种动画脚本任务已发送到RabbitMQ: " + queue.getName());
        } catch (IOException e) {
            log.error("发送动画脚本任务失败", e);
            Result<String> errorResult = new Result<>();
            errorResult.setCode(500);
            errorResult.setMessage("发送动画脚本任务失败: " + e.getMessage());
            errorResult.setData(null);
            return errorResult;
        }
    }
}
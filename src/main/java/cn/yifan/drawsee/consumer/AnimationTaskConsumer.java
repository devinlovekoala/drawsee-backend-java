package cn.yifan.drawsee.consumer;

import cn.yifan.drawsee.exception.ApiException;
import cn.yifan.drawsee.pojo.rabbit.AnimationTaskMessage;
import cn.yifan.drawsee.service.base.ApiService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;

/**
 * @FileName AnimationTaskConsumer @Description
 * 动画任务消费者，负责从RabbitMQ队列中接收动画任务消息，并调用Python服务进行渲染 @Author yifan
 *
 * @date 2025-03-25 15:15
 */
@Slf4j
@Component
public class AnimationTaskConsumer {

  @Autowired private ApiService apiService;

  @Autowired private ObjectMapper objectMapper;

  /**
   * 接收并处理动画任务消息
   *
   * @param message 原始消息
   * @param animationTaskMessage 解析后的动画任务消息
   */
  @RabbitListener(
      queues = {
        "${drawsee.rabbitmq.animationConfig.queueName}_1",
        "${drawsee.rabbitmq.animationConfig.queueName}_2",
        "${drawsee.rabbitmq.animationConfig.queueName}_3"
      })
  public void receiveAnimationTask(
      Message message, @Payload AnimationTaskMessage animationTaskMessage) {
    Long taskId = animationTaskMessage.getAiTaskId();
    Long nodeId = animationTaskMessage.getNodeId();

    log.info("接收到动画任务: taskId={}, nodeId={}", taskId, nodeId);

    try {
      // 调用Python服务渲染动画
      apiService.renderAnimation(taskId, nodeId, animationTaskMessage.getCode());

      log.info("渲染动画任务已发送: taskId={}, nodeId={}", taskId, nodeId);
    } catch (ApiException e) {
      log.error(
          "处理动画任务失败(API异常): taskId={}, nodeId={}, 错误信息: {}", taskId, nodeId, e.getMessage(), e);
      // 这里可以添加重试逻辑或者其他错误处理机制
    } catch (RestClientException e) {
      log.error(
          "处理动画任务失败(网络异常): taskId={}, nodeId={}, 错误信息: {}", taskId, nodeId, e.getMessage(), e);
      // 网络相关异常可能需要特殊处理
    } catch (Exception e) {
      log.error(
          "处理动画任务失败(未预期异常): taskId={}, nodeId={}, 错误信息: {}", taskId, nodeId, e.getMessage(), e);
      // 处理其他未预期的异常
    }
  }
}

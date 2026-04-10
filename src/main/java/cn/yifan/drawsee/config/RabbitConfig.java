package cn.yifan.drawsee.config;

import cn.yifan.drawsee.pojo.rabbit.LinkedQueue;
import cn.yifan.drawsee.pojo.rabbit.MqConfig;
import java.util.ArrayList;
import java.util.List;
import lombok.Data;
import org.jetbrains.annotations.NotNull;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.handler.annotation.support.DefaultMessageHandlerMethodFactory;

/**
 * @FileName RabbitMQConfig @Description @Author yifan
 *
 * @date 2025-01-28 22:55
 */
@Configuration
@ConfigurationProperties(prefix = "drawsee.rabbitmq")
@Data
public class RabbitConfig {

  private MqConfig aiConfig;

  private MqConfig animationConfig;

  @Bean
  public List<LinkedQueue> aiTaskQueues() {
    return getLinkedQueues(aiConfig);
  }

  @Bean
  public List<LinkedQueue> animationTaskQueues() {
    return getLinkedQueues(animationConfig);
  }

  @NotNull
  private List<LinkedQueue> getLinkedQueues(MqConfig mqConfig) {
    List<LinkedQueue> queues = new ArrayList<>();
    for (int i = 1; i <= mqConfig.getQueueCount(); i++) {
      LinkedQueue queue = new LinkedQueue();
      queue.setName(mqConfig.getQueueName() + "_" + i);
      queue.setRoutingKey(mqConfig.getRoutingKey() + "_" + i);
      queue.setConcurrency(mqConfig.getQueueConcurrency());
      queue.setExchangeName(mqConfig.getExchangeName());
      queues.add(queue);
    }
    return queues;
  }

  @Bean
  public DefaultMessageHandlerMethodFactory handlerMethodFactory() {
    return new DefaultMessageHandlerMethodFactory();
  }

  @Bean
  // 显示声明才能创建
  public RabbitAdmin rabbitAdmin(
      ConnectionFactory connectionFactory,
      List<LinkedQueue> aiTaskQueues,
      List<LinkedQueue> animationTaskQueues) {
    RabbitAdmin admin = new RabbitAdmin(connectionFactory);
    // AI任务队列
    declareExchangeAndQueues(aiTaskQueues, admin, aiConfig);
    // 动画任务队列
    declareExchangeAndQueues(animationTaskQueues, admin, animationConfig);
    return admin;
  }

  private void declareExchangeAndQueues(
      List<LinkedQueue> taskQueues, RabbitAdmin admin, MqConfig mqConfig) {
    DirectExchange exchange = new DirectExchange(mqConfig.getExchangeName());
    admin.declareExchange(exchange);
    for (LinkedQueue configQueue : taskQueues) {
      Queue queue = new Queue(configQueue.getName(), true, false, false);
      Binding binding = BindingBuilder.bind(queue).to(exchange).with(configQueue.getRoutingKey());
      admin.declareQueue(queue);
      admin.declareBinding(binding);
    }
  }

  @Bean
  public MessageConverter jsonMessageConverter() {
    return new Jackson2JsonMessageConverter();
  }

  @Bean
  public RabbitTemplate rabbitTemplate(
      ConnectionFactory connectionFactory, MessageConverter jsonMessageConverter) {
    RabbitTemplate rabbitTemplate = new RabbitTemplate(connectionFactory);
    rabbitTemplate.setMessageConverter(jsonMessageConverter);
    return rabbitTemplate;
  }

  @Bean
  public SimpleRabbitListenerContainerFactory rabbitListenerContainerFactory(
      ConnectionFactory connectionFactory, MessageConverter jsonMessageConverter) {
    SimpleRabbitListenerContainerFactory factory = new SimpleRabbitListenerContainerFactory();
    factory.setConnectionFactory(connectionFactory);
    factory.setMessageConverter(jsonMessageConverter);
    factory.setDefaultRequeueRejected(false);
    factory.setMissingQueuesFatal(false);
    return factory;
  }
}

package cn.yifan.drawsee.config;

import cn.yifan.drawsee.pojo.rabbit.LinkedQueue;
import cn.yifan.drawsee.pojo.rabbit.AiTaskMessage;
import cn.yifan.drawsee.worker.AITaskWorker;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListenerConfigurer;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerEndpoint;
import org.springframework.amqp.rabbit.listener.RabbitListenerEndpointRegistrar;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.handler.annotation.support.DefaultMessageHandlerMethodFactory;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

/**
 * @FileName RabbitListenerConfig
 * @Description 
 * @Author yifan
 * @date 2025-01-29 13:25
 **/

@Configuration
@Slf4j
public class RabbitListenerConfig implements RabbitListenerConfigurer {

    @Autowired
    private List<LinkedQueue> aiTaskQueues;
    @Autowired
    private DefaultMessageHandlerMethodFactory handlerMethodFactory;
    @Autowired
    private MessageConverter jsonMessageConverter;
    @Autowired
    private AITaskWorker aiTaskWorker;
    @Autowired
    private ObjectMapper objectMapper;

    @Override
    public void configureRabbitListeners(RabbitListenerEndpointRegistrar registrar) {
        registrar.setMessageHandlerMethodFactory(handlerMethodFactory);

        for (LinkedQueue queue : aiTaskQueues) {
            SimpleRabbitListenerEndpoint endpoint = new SimpleRabbitListenerEndpoint();
            endpoint.setId(queue.getName() + ".Endpoint");
            endpoint.setQueueNames(queue.getName());
            endpoint.setConcurrency(queue.getConcurrency());
            log.info("注册AI任务监听: queue={}, routingKey={}, concurrency={}", queue.getName(), queue.getRoutingKey(), queue.getConcurrency());
            //endpoint.setMessageConverter(jsonMessageConverter);
            endpoint.setMessageListener(message -> {
                Object data = null;
                try {
                    data = jsonMessageConverter.fromMessage(message);
                } catch (Exception e) {
                    log.warn("JSON转换失败，尝试按字符串解析, queue={}, error={}", queue.getName(), e.toString());
                }

                AiTaskMessage aiTaskMessage = null;
                try {
                    if (data instanceof AiTaskMessage m) {
                        aiTaskMessage = m;
                    } else if (data instanceof Map<?, ?> map) {
                        aiTaskMessage = objectMapper.convertValue(map, AiTaskMessage.class);
                    } else if (message.getBody() != null) {
                        String body = new String(message.getBody(), StandardCharsets.UTF_8);
                        aiTaskMessage = objectMapper.readValue(body, AiTaskMessage.class);
                    }
                } catch (Exception ex) {
                    log.error("无法解析消息为AiTaskMessage, queue={}, rawClass={}, error= {}", queue.getName(), data == null ? null : data.getClass(), ex.toString());
                }

                if (aiTaskMessage != null) {
                    log.info("消费任务：{}，线程：{}，开始处理", aiTaskMessage.getTaskId(), Thread.currentThread().getName());
                    aiTaskWorker.processTask(aiTaskMessage);
                } else {
                    log.warn("丢弃未知消息，queue={}, headers={}, bodySize={}", queue.getName(), message.getMessageProperties().getHeaders(), message.getBody() == null ? 0 : message.getBody().length);
                }
            });
            registrar.registerEndpoint(endpoint);
        }

    }

}

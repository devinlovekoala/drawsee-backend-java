package cn.yifan.drawsee.config;

import cn.yifan.drawsee.pojo.rabbit.LinkedQueue;
import cn.yifan.drawsee.pojo.rabbit.AiTaskMessage;
import cn.yifan.drawsee.worker.AITaskWorker;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListenerConfigurer;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerEndpoint;
import org.springframework.amqp.rabbit.listener.RabbitListenerEndpointRegistrar;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.handler.annotation.support.DefaultMessageHandlerMethodFactory;
import java.util.List;

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

    @Override
    public void configureRabbitListeners(RabbitListenerEndpointRegistrar registrar) {
        registrar.setMessageHandlerMethodFactory(handlerMethodFactory);

        for (LinkedQueue queue : aiTaskQueues) {
            SimpleRabbitListenerEndpoint endpoint = new SimpleRabbitListenerEndpoint();
            endpoint.setId(queue.getName() + ".Endpoint");
            endpoint.setQueueNames(queue.getName());
            endpoint.setConcurrency(queue.getConcurrency());
            //endpoint.setMessageConverter(jsonMessageConverter);
            endpoint.setMessageListener(message -> {
                Object data = jsonMessageConverter.fromMessage(message);
                if (data instanceof AiTaskMessage aiTaskMessage) {
                    // 处理消息的逻辑
                    log.info("消费任务：{}，线程：{}，开始处理", aiTaskMessage.getTaskId(), Thread.currentThread().getName());
                    aiTaskWorker.processTask(aiTaskMessage);
                }
            });
            registrar.registerEndpoint(endpoint);
        }

    }

}

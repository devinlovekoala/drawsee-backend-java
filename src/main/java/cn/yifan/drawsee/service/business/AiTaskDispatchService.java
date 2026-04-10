package cn.yifan.drawsee.service.business;

import cn.yifan.drawsee.pojo.rabbit.AiTaskMessage;
import cn.yifan.drawsee.worker.AITaskWorker;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

/**
 * AI任务本地异步派发服务
 *
 * <p>当前优先采用 JVM 内本地异步执行，避免 RabbitMQ 消费链路异常时导致任务静默挂起。
 */
@Service
@Slf4j
public class AiTaskDispatchService {

  private final AITaskWorker aiTaskWorker;

  public AiTaskDispatchService(AITaskWorker aiTaskWorker) {
    this.aiTaskWorker = aiTaskWorker;
  }

  @Async("taskExecutor")
  public void dispatch(AiTaskMessage aiTaskMessage) {
    log.info(
        "本地异步派发AI任务: taskId={}, type={}, convId={}",
        aiTaskMessage.getTaskId(),
        aiTaskMessage.getType(),
        aiTaskMessage.getConvId());
    aiTaskWorker.processTask(aiTaskMessage);
  }
}

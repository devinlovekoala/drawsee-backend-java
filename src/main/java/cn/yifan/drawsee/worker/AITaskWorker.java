package cn.yifan.drawsee.worker;

import cn.yifan.drawsee.constant.*;
import cn.yifan.drawsee.mapper.AiTaskMapper;
import cn.yifan.drawsee.pojo.rabbit.AiTaskMessage;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RStream;
import org.redisson.api.RedissonClient;
import org.redisson.api.stream.StreamAddArgs;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * @FileName AITaskConsumer @Description @Author yifan
 *
 * @date 2025-01-28 23:34
 */
@Service
@Slf4j
public class AITaskWorker {

  @Autowired private AiTaskMapper aiTaskMapper;
  @Autowired private RedissonClient redissonClient;

  @Autowired private GeneralWorkFlow generalWorkFlow;
  @Autowired private GeneralDetailWorkFlow generalDetailWorkFlow;
  @Autowired private KnowledgeDetailWorkFlow knowledgeDetailWorkFlow;
  @Autowired private AnimationWorkFlow animationWorkFlow;
  @Autowired private SolverFirstWorkFlow solverFirstWorkFlow;
  @Autowired private SolverContinueWorkFlow solverContinueWorkFlow;
  @Autowired private SolverSummaryWorkFlow solverSummaryWorkFlow;
  @Autowired private PlannerWorkFlow plannerWorkFlow;
  @Autowired private HtmlMakerWorkFlow htmlMakerWorkFlow;
  @Autowired private CircuitAnalysisWorkFlow circuitAnalysisWorkFlow;
  @Autowired private CircuitAnalysisDetailWorkFlow circuitAnalysisDetailWorkFlow;
  @Autowired private PdfCircuitAnalysisWorkFlow pdfCircuitAnalysisWorkFlow;
  @Autowired private PdfCircuitAnalysisDetailWorkFlow pdfCircuitAnalysisDetailWorkFlow;

  public void processTask(AiTaskMessage aiTaskMessage) {
    log.info(
        "开始处理任务: taskId={}, type={}, userId={}, convId={}",
        aiTaskMessage.getTaskId(),
        aiTaskMessage.getType(),
        aiTaskMessage.getUserId(),
        aiTaskMessage.getConvId());
    try {
      WorkContext workContext = new WorkContext(aiTaskMessage);
      switch (aiTaskMessage.getType()) {
        case AiTaskType.GENERAL -> generalWorkFlow.execute(workContext);
        case AiTaskType.GENERAL_DETAIL -> generalDetailWorkFlow.execute(workContext);
        case AiTaskType.KNOWLEDGE -> generalWorkFlow.execute(workContext);
        case AiTaskType.KNOWLEDGE_DETAIL -> knowledgeDetailWorkFlow.execute(workContext);
        case AiTaskType.ANIMATION -> animationWorkFlow.execute(workContext);
        case AiTaskType.SOLVER_FIRST -> solverFirstWorkFlow.execute(workContext);
        case AiTaskType.SOLVER_CONTINUE -> solverContinueWorkFlow.execute(workContext);
        case AiTaskType.SOLVER_SUMMARY -> solverSummaryWorkFlow.execute(workContext);
        case AiTaskType.PLANNER -> plannerWorkFlow.execute(workContext);
        case AiTaskType.HTML_MAKER -> htmlMakerWorkFlow.execute(workContext);
        case AiTaskType.CIRCUIT_ANALYSIS -> circuitAnalysisWorkFlow.execute(workContext);
        case AiTaskType.CIRCUIT_DETAIL -> circuitAnalysisDetailWorkFlow.execute(workContext);
        case AiTaskType.PDF_CIRCUIT_ANALYSIS -> pdfCircuitAnalysisWorkFlow.execute(workContext);
        case AiTaskType.PDF_CIRCUIT_ANALYSIS_DETAIL ->
            pdfCircuitAnalysisDetailWorkFlow.execute(workContext);
        default -> {
          log.error("未知任务类型: {}", aiTaskMessage.getType());
          publishTaskFailure(aiTaskMessage, "未知任务类型: " + aiTaskMessage.getType(), null);
        }
      }
    } catch (Exception e) {
      publishTaskFailure(aiTaskMessage, resolveErrorMessage(e), e);
    }
  }

  private void publishTaskFailure(AiTaskMessage aiTaskMessage, String message, Throwable error) {
    log.error(
        "AI任务Worker执行失败: taskId={}, type={}",
        aiTaskMessage.getTaskId(),
        aiTaskMessage.getType(),
        error);

    try {
      RStream<String, Object> redisStream =
          redissonClient.getStream(RedisKey.AI_TASK_PREFIX + aiTaskMessage.getTaskId());
      redisStream.add(StreamAddArgs.entries("type", AiTaskMessageType.ERROR, "data", message));
      redisStream.add(StreamAddArgs.entries("type", AiTaskMessageType.DONE, "data", ""));
    } catch (Exception streamError) {
      log.warn("Worker推送失败消息异常: {}", streamError.getMessage());
    }

    try {
      var aiTask = aiTaskMapper.getById(aiTaskMessage.getTaskId());
      if (aiTask != null) {
        aiTask.setStatus(AiTaskStatus.FAILED);
        aiTask.setResult(message);
        aiTaskMapper.update(aiTask);
      }
    } catch (Exception taskError) {
      log.warn("Worker更新任务失败状态异常: {}", taskError.getMessage());
    }
  }

  private String resolveErrorMessage(Throwable error) {
    if (error == null) {
      return "未知异常";
    }
    Throwable current = error;
    while (current.getCause() != null && current.getCause() != current) {
      current = current.getCause();
    }
    String message = current.getMessage();
    if (message == null || message.isBlank()) {
      message = error.getMessage();
    }
    if (message == null || message.isBlank()) {
      message = error.getClass().getSimpleName();
    }
    return message.length() > 400 ? message.substring(0, 400) + "..." : message;
  }
}

package cn.yifan.drawsee.worker;

import cn.yifan.drawsee.constant.*;
import cn.yifan.drawsee.pojo.rabbit.AiTaskMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * @FileName AITaskConsumer
 * @Description
 * @Author yifan
 * @date 2025-01-28 23:34
 **/

@Service
@Slf4j
public class AITaskWorker {

    @Autowired
    private GeneralWorkFlow generalWorkFlow;
    @Autowired
    private GeneralDetailWorkFlow generalDetailWorkFlow;
    @Autowired
    private KnowledgeWorkFlow knowledgeWorkFlow;
    @Autowired
    private KnowledgeDetailWorkFlow knowledgeDetailWorkFlow;
    @Autowired
    private AnimationWorkFlow animationWorkFlow;
    @Autowired
    private SolverFirstWorkFlow solverFirstWorkFlow;
    @Autowired
    private SolverContinueWorkFlow solverContinueWorkFlow;
    @Autowired
    private SolverSummaryWorkFlow solverSummaryWorkFlow;
    @Autowired
    private PlannerWorkFlow plannerWorkFlow;
    @Autowired
    private HtmlMakerWorkFlow htmlMakerWorkFlow;
    @Autowired
    private CircuitAnalysisWorkFlow circuitAnalysisWorkFlow;
    @Autowired
    private CircuitAnalysisDetailWorkFlow circuitAnalysisDetailWorkFlow;

    public void processTask(AiTaskMessage aiTaskMessage) {
        log.info("开始处理任务: taskId={}, type={}, userId={}, convId={}", aiTaskMessage.getTaskId(), aiTaskMessage.getType(), aiTaskMessage.getUserId(), aiTaskMessage.getConvId());
        WorkContext workContext = new WorkContext(aiTaskMessage);
        switch (aiTaskMessage.getType()) {
            case AiTaskType.GENERAL -> generalWorkFlow.execute(workContext);
            case AiTaskType.GENERAL_DETAIL -> generalDetailWorkFlow.execute(workContext);
            case AiTaskType.KNOWLEDGE -> knowledgeWorkFlow.execute(workContext);
            case AiTaskType.KNOWLEDGE_DETAIL -> knowledgeDetailWorkFlow.execute(workContext);
            case AiTaskType.ANIMATION -> animationWorkFlow.execute(workContext);
            case AiTaskType.SOLVER_FIRST -> solverFirstWorkFlow.execute(workContext);
            case AiTaskType.SOLVER_CONTINUE -> solverContinueWorkFlow.execute(workContext);
            case AiTaskType.SOLVER_SUMMARY -> solverSummaryWorkFlow.execute(workContext);
            case AiTaskType.PLANNER -> plannerWorkFlow.execute(workContext);
            case AiTaskType.HTML_MAKER -> htmlMakerWorkFlow.execute(workContext);
            case AiTaskType.CIRCUIT_ANALYSIS -> circuitAnalysisWorkFlow.execute(workContext);
            case AiTaskType.CIRCUIT_DETAIL -> circuitAnalysisDetailWorkFlow.execute(workContext);
            default -> log.error("未知任务类型: {}", aiTaskMessage.getType());
        }
    }

}

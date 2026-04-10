package cn.yifan.drawsee.controller.admin;

import cn.dev33.satoken.annotation.SaCheckLogin;
import cn.dev33.satoken.annotation.SaCheckRole;
import cn.yifan.drawsee.constant.KnowledgeDocumentStatus;
import cn.yifan.drawsee.constant.RagIngestionStage;
import cn.yifan.drawsee.constant.UserRole;
import cn.yifan.drawsee.pojo.entity.RagIngestionTask;
import cn.yifan.drawsee.pojo.vo.R;
import cn.yifan.drawsee.pojo.vo.rag.RagIngestionTaskVO;
import cn.yifan.drawsee.service.business.DocumentIngestionService;
import cn.yifan.drawsee.service.business.RagIngestionTaskService;
import java.util.List;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

/** 管理员RAG任务管理接口 */
@RestController
@RequestMapping("/api/admin/rag")
@SaCheckRole(UserRole.ADMIN)
@Slf4j
public class AdminRagTaskController {

  @Autowired private RagIngestionTaskService ragIngestionTaskService;

  @Autowired private DocumentIngestionService documentIngestionService;

  /** 管理员分页查询RAG入库任务 */
  @SaCheckLogin
  @GetMapping("/tasks")
  public R<List<RagIngestionTaskVO>> listTasks(
      @RequestParam(required = false) String knowledgeBaseId,
      @RequestParam(required = false) String documentId,
      @RequestParam(required = false) String stage,
      @RequestParam(required = false) String status,
      @RequestParam(defaultValue = "1") int page,
      @RequestParam(defaultValue = "20") int size) {
    if (page < 1 || size < 1) {
      return R.fail("分页参数不合法");
    }
    if (stage != null && !stage.isBlank()) {
      try {
        RagIngestionStage.valueOf(stage);
      } catch (IllegalArgumentException ex) {
        return R.fail("无效的stage参数");
      }
    }
    if (status != null && !status.isBlank()) {
      try {
        KnowledgeDocumentStatus.valueOf(status);
      } catch (IllegalArgumentException ex) {
        return R.fail("无效的status参数");
      }
    }
    int offset = (page - 1) * size;
    List<RagIngestionTask> tasks =
        ragIngestionTaskService.listTasks(knowledgeBaseId, documentId, stage, status, offset, size);
    return R.ok(tasks.stream().map(this::convertToVO).collect(Collectors.toList()));
  }

  /** 管理员重试RAG入库任务 */
  @SaCheckLogin
  @PostMapping("/tasks/{taskId}/retry")
  public R<Boolean> retryTask(@PathVariable("taskId") String taskId) {
    RagIngestionTask task = ragIngestionTaskService.resetForRetry(taskId);
    if (task == null) {
      return R.fail("任务不存在");
    }
    documentIngestionService.ingestAsync(taskId);
    return R.ok(true);
  }

  private RagIngestionTaskVO convertToVO(RagIngestionTask task) {
    return RagIngestionTaskVO.builder()
        .id(task.getId())
        .knowledgeBaseId(task.getKnowledgeBaseId())
        .documentId(task.getDocumentId())
        .stage(task.getStage())
        .status(task.getStatus())
        .progress(task.getProgress())
        .errorMessage(task.getErrorMessage())
        .durationMs(task.getDurationMs())
        .createdAt(task.getCreatedAt())
        .updatedAt(task.getUpdatedAt())
        .completedAt(task.getCompletedAt())
        .build();
  }
}

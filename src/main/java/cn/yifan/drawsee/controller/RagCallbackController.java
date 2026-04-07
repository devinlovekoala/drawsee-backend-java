package cn.yifan.drawsee.controller;

import cn.yifan.drawsee.constant.KnowledgeDocumentStatus;
import cn.yifan.drawsee.pojo.vo.CommonResponse;
import cn.yifan.drawsee.service.business.KnowledgeDocumentService;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

/**
 * RAG回调API - 接收Python服务的状态更新
 *
 * @author Drawsee Team
 */
@RestController
@RequestMapping("/api/rag/callback")
@Slf4j
public class RagCallbackController {

  @Autowired private KnowledgeDocumentService knowledgeDocumentService;

  /**
   * 接收Python服务的文档状态更新回调
   *
   * @param request 回调请求体
   * @return 响应
   */
  @PostMapping("/document-status")
  public CommonResponse<Void> updateDocumentStatus(@RequestBody Map<String, Object> request) {
    String documentId = (String) request.get("document_id");
    String statusStr = (String) request.get("status");
    String taskId = (String) request.get("task_id");
    String errorMessage = (String) request.get("error_message");

    log.info("收到Python服务回调: documentId={}, status={}, taskId={}", documentId, statusStr, taskId);

    try {
      // 转换状态枚举
      KnowledgeDocumentStatus status = KnowledgeDocumentStatus.valueOf(statusStr);

      // 更新文档状态
      knowledgeDocumentService.updateStatus(documentId, status, errorMessage);

      log.info("文档状态已更新: documentId={}, status={}", documentId, status);

      return CommonResponse.success(null);

    } catch (IllegalArgumentException e) {
      log.error("无效的文档状态: {}", statusStr);
      return CommonResponse.error(400, "无效的文档状态");
    } catch (Exception e) {
      log.error("更新文档状态失败: documentId={}", documentId, e);
      return CommonResponse.error(500, "更新文档状态失败: " + e.getMessage());
    }
  }
}

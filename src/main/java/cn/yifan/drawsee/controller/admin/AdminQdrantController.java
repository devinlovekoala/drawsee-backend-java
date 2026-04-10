package cn.yifan.drawsee.controller.admin;

import cn.dev33.satoken.annotation.SaCheckLogin;
import cn.dev33.satoken.annotation.SaCheckRole;
import cn.yifan.drawsee.constant.UserRole;
import cn.yifan.drawsee.pojo.vo.R;
import cn.yifan.drawsee.service.business.QdrantService;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Qdrant 管理接口（管理员） */
@RestController
@RequestMapping("/api/admin/qdrant")
@SaCheckRole(UserRole.ADMIN)
@Slf4j
public class AdminQdrantController {

  @Autowired private QdrantService qdrantService;

  @SaCheckLogin
  @GetMapping("/collection/info")
  public R<Map<String, Object>> getCollectionInfo() {
    Map<String, Object> info = qdrantService.getCollectionInfo();
    Map<String, Object> result = new java.util.HashMap<>();
    result.put("info", info);
    result.put("config", qdrantService.getEffectiveConfig());
    return R.ok(result);
  }

  @SaCheckLogin
  @GetMapping("/collection/count")
  public R<Map<String, Object>> countPoints(
      @RequestParam(required = false) String knowledgeBaseId,
      @RequestParam(required = false) String documentId) {
    Map<String, Object> result = qdrantService.countPoints(knowledgeBaseId, documentId);
    return R.ok(result);
  }
}

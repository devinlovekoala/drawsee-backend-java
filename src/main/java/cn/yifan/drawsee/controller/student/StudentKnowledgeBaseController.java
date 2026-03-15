package cn.yifan.drawsee.controller.student;

import cn.dev33.satoken.annotation.SaCheckLogin;
import cn.dev33.satoken.annotation.SaCheckRole;
import cn.yifan.drawsee.constant.UserRole;
import cn.yifan.drawsee.pojo.dto.JoinKnowledgeBaseDTO;
import cn.yifan.drawsee.pojo.vo.KnowledgeBaseVO;
import cn.yifan.drawsee.pojo.vo.R;
import cn.yifan.drawsee.pojo.vo.ResourceCountVO;
import cn.yifan.drawsee.pojo.vo.rag.RagKnowledgeVO;
import cn.yifan.drawsee.service.business.KnowledgeBaseService;
import cn.yifan.drawsee.service.business.KnowledgeResourceService;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

/**
 * @FileName StudentKnowledgeBaseController @Description 学生知识库控制器类，提供学生用户访问已加入知识库的接口 @Author yifan
 *
 * @date 2025-08-15 16:30
 * @update 2025-10-05 15:10 更新接口返回格式，增加资源访问接口
 */
@RestController
@RequestMapping("/api/student/knowledge-bases")
@SaCheckRole(UserRole.STUDENT)
@Slf4j
public class StudentKnowledgeBaseController {

  @Autowired private KnowledgeBaseService knowledgeBaseService;

  @Autowired private KnowledgeResourceService knowledgeResourceService;

  /**
   * 获取学生可访问的所有知识库列表 包括通过邀请码加入的知识库
   *
   * @return 知识库列表
   */
  @SaCheckLogin
  @GetMapping({"/list", ""})
  public R<List<KnowledgeBaseVO>> getAccessibleKnowledgeBases() {
    try {
      List<KnowledgeBaseVO> knowledgeBases = knowledgeBaseService.getKnowledgeBasesForCurrentUser();
      return R.ok(knowledgeBases);
    } catch (Exception e) {
      log.error("获取学生可访问知识库列表失败", e);
      return R.fail(e.getMessage());
    }
  }

  /**
   * 学生加入知识库
   *
   * @param joinKnowledgeBaseDTO 加入知识库DTO
   * @return 知识库ID
   */
  @SaCheckLogin
  @PostMapping("/join")
  public R<String> joinKnowledgeBase(
      @RequestBody @Valid JoinKnowledgeBaseDTO joinKnowledgeBaseDTO) {
    try {
      String knowledgeBaseId = knowledgeBaseService.joinKnowledgeBase(joinKnowledgeBaseDTO);
      return R.ok(knowledgeBaseId);
    } catch (Exception e) {
      log.error("学生加入知识库失败", e);
      return R.fail(e.getMessage());
    }
  }

  /**
   * 获取学生已加入的知识库列表
   *
   * @return 知识库列表
   */
  @SaCheckLogin
  @GetMapping("/joined")
  public R<List<KnowledgeBaseVO>> getJoinedKnowledgeBases() {
    try {
      List<KnowledgeBaseVO> knowledgeBases = knowledgeBaseService.getMyJoinedKnowledgeBases();
      return R.ok(knowledgeBases);
    } catch (Exception e) {
      log.error("获取学生已加入知识库列表失败", e);
      return R.fail(e.getMessage());
    }
  }

  /**
   * 获取知识库详情
   *
   * @param id 知识库ID
   * @return 知识库详情
   */
  @SaCheckLogin
  @GetMapping("/{id}")
  public R<KnowledgeBaseVO> getKnowledgeBaseDetail(@PathVariable("id") String id) {
    try {
      KnowledgeBaseVO knowledgeBase = knowledgeBaseService.getKnowledgeBaseDetail(id);
      return R.ok(knowledgeBase);
    } catch (Exception e) {
      log.error("获取知识库详情失败", e);
      return R.fail(e.getMessage());
    }
  }

  /** 获取学生可用的RAG知识库列表 */
  @SaCheckLogin
  @GetMapping("/rag/list")
  public R<List<RagKnowledgeVO>> getStudentRagKnowledges(
      @RequestParam(defaultValue = "1") int page, @RequestParam(defaultValue = "10") int size) {
    try {
      List<RagKnowledgeVO> ragKnowledgeBases =
          knowledgeBaseService.listRagKnowledgeBasesForCurrentUser(page, size);
      return R.ok(ragKnowledgeBases);
    } catch (Exception e) {
      log.error("获取学生RAG知识库列表失败", e);
      return R.fail(e.getMessage());
    }
  }

  /**
   * 获取知识库资源计数
   *
   * @param knowledgeBaseId 知识库ID
   * @return 资源计数
   */
  @SaCheckLogin
  @GetMapping("/resource/count/{knowledgeBaseId}")
  public R<ResourceCountVO> getKnowledgeResourceCount(
      @PathVariable("knowledgeBaseId") String knowledgeBaseId) {
    try {
      // 获取知识库资源计数
      Map<String, Integer> countMap =
          knowledgeResourceService.getResourceCountByTypes(knowledgeBaseId);

      // 构建响应VO
      ResourceCountVO countVO =
          ResourceCountVO.builder()
              .totalCount(countMap.values().stream().mapToInt(Integer::intValue).sum())
              .pdfCount(countMap.getOrDefault("PDF", 0))
              .wordCount(countMap.getOrDefault("WORD", 0))
              .videoCount(countMap.getOrDefault("VIDEO", 0))
              .bilibiliCount(countMap.getOrDefault("BILIBILI", 0))
              .build();

      return R.ok(countVO);
    } catch (Exception e) {
      log.error("获取知识库资源计数失败", e);
      return R.fail(e.getMessage());
    }
  }

  /**
   * 获取知识库的资源列表
   *
   * @param knowledgeBaseId 知识库ID
   * @return 资源列表
   */
  @SaCheckLogin
  @GetMapping("/resource/list/{knowledgeBaseId}")
  public R<List<Map<String, Object>>> getResourceList(
      @PathVariable("knowledgeBaseId") String knowledgeBaseId) {
    try {
      List<Map<String, Object>> resources =
          knowledgeResourceService.getResourceList(knowledgeBaseId);
      return R.ok(resources);
    } catch (Exception e) {
      log.error("获取知识库资源列表失败", e);
      return R.fail(e.getMessage());
    }
  }
}

package cn.yifan.drawsee.controller;

import cn.dev33.satoken.annotation.SaCheckLogin;
import cn.dev33.satoken.stp.StpUtil;
import cn.yifan.drawsee.pojo.entity.UserDocument;
import cn.yifan.drawsee.pojo.vo.R;
import cn.yifan.drawsee.service.business.UserDocumentService;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

/**
 * 用户文档控制器
 *
 * @author yifan
 * @date 2025-01-28
 */
@RestController
@RequestMapping("/user/document")
@Slf4j
@SaCheckLogin
public class UserDocumentController {

  @Autowired private UserDocumentService userDocumentService;

  /** 上传文档 */
  @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  public R<UserDocument> uploadDocument(
      @RequestPart("file") MultipartFile file,
      @RequestParam(value = "title", required = false) String title,
      @RequestParam(value = "description", required = false) String description) {

    Long userId = StpUtil.getLoginIdAsLong();
    UserDocument document = userDocumentService.uploadDocument(file, userId, title, description);
    return R.ok("文档上传成功", document);
  }

  /** 获取用户文档列表 */
  @GetMapping("/list")
  public R<List<UserDocument>> getUserDocuments() {
    Long userId = StpUtil.getLoginIdAsLong();
    List<UserDocument> documents = userDocumentService.getUserDocuments(userId);
    return R.ok("获取文档列表成功", documents);
  }

  /** 获取文档详情（根据ID） */
  @GetMapping("/{id}")
  public R<UserDocument> getDocumentById(@PathVariable Long id) {
    Long userId = StpUtil.getLoginIdAsLong();
    UserDocument document = userDocumentService.getDocumentById(id, userId);
    return R.ok("获取文档详情成功", document);
  }

  /** 获取文档详情（根据UUID） */
  @GetMapping("/uuid/{uuid}")
  public R<UserDocument> getDocumentByUuid(@PathVariable String uuid) {
    Long userId = StpUtil.getLoginIdAsLong();
    UserDocument document = userDocumentService.getDocumentByUuid(uuid, userId);
    return R.ok("获取文档详情成功", document);
  }

  /** 删除文档 */
  @DeleteMapping("/{id}")
  public R<Void> deleteDocument(@PathVariable Long id) {
    Long userId = StpUtil.getLoginIdAsLong();
    userDocumentService.deleteDocument(id, userId);
    return R.ok("文档删除成功");
  }

  /** 获取文档下载链接 */
  @GetMapping("/{id}/download")
  public R<String> getDocumentDownloadUrl(@PathVariable Long id) {
    Long userId = StpUtil.getLoginIdAsLong();
    String downloadUrl = userDocumentService.getDocumentDownloadUrl(id, userId);
    return R.ok("获取下载链接成功", downloadUrl);
  }

  /** PDF文档列表（针对电路实验分析） */
  @GetMapping("/pdf/list")
  public R<List<UserDocument>> getPdfDocuments() {
    Long userId = StpUtil.getLoginIdAsLong();
    List<UserDocument> documents = userDocumentService.getUserDocuments(userId);
    // 过滤PDF文档
    List<UserDocument> pdfDocuments =
        documents.stream().filter(doc -> "pdf".equals(doc.getDocumentType())).toList();
    return R.ok("获取PDF文档列表成功", pdfDocuments);
  }
}

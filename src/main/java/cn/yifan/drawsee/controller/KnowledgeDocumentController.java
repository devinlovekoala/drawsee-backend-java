package cn.yifan.drawsee.controller;

import cn.dev33.satoken.annotation.SaCheckLogin;
import cn.dev33.satoken.stp.StpUtil;
import cn.yifan.drawsee.constant.KnowledgeDocumentStatus;
import cn.yifan.drawsee.constant.MinioObjectPath;
import cn.yifan.drawsee.pojo.entity.KnowledgeBase;
import cn.yifan.drawsee.pojo.entity.KnowledgeDocument;
import cn.yifan.drawsee.pojo.entity.RagIngestionTask;
import cn.yifan.drawsee.pojo.vo.R;
import cn.yifan.drawsee.pojo.vo.rag.KnowledgeDocumentUploadVO;
import cn.yifan.drawsee.pojo.vo.rag.KnowledgeDocumentVO;
import cn.yifan.drawsee.pojo.vo.rag.RagIngestionTaskVO;
import cn.yifan.drawsee.mapper.KnowledgeBaseMapper;
import cn.yifan.drawsee.service.base.MinioService;
import cn.yifan.drawsee.service.business.DocumentIngestionService;
import cn.yifan.drawsee.service.business.KnowledgeDocumentService;
import cn.yifan.drawsee.service.business.RagIngestionTaskService;
import cn.yifan.drawsee.util.UUIDUtils;
import io.minio.errors.MinioException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 知识库文档控制器
 *
 * 提供文档上传、查询、删除以及任务状态查询接口
 *
 * @author yifan
 * @date 2025-10-10
 */
@RestController
@RequestMapping("/api")
@Slf4j
public class KnowledgeDocumentController {

    @Autowired
    private KnowledgeDocumentService knowledgeDocumentService;

    @Autowired
    private DocumentIngestionService documentIngestionService;

    @Autowired
    private RagIngestionTaskService ragIngestionTaskService;

    @Autowired
    private KnowledgeBaseMapper knowledgeBaseMapper;

    @Autowired
    private MinioService minioService;

    /**
     * 上传文档
     */
    @SaCheckLogin
    @PostMapping(value = {
        "/knowledge-bases/{knowledgeBaseId}/documents",
        "/admin/knowledge-bases/{knowledgeBaseId}/documents",
        "/teacher/knowledge-bases/{knowledgeBaseId}/documents"
    }, consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public R<KnowledgeDocumentUploadVO> uploadDocument(@PathVariable("knowledgeBaseId") String knowledgeBaseId,
                                                       @RequestParam("file") MultipartFile file) {
        try {
            if (file == null || file.isEmpty()) {
                return R.fail("文件不能为空");
            }
            log.info("接收到知识库文档上传请求: knowledgeBaseId={}, originalName={}, size={}",
                knowledgeBaseId, file.getOriginalFilename(), file.getSize());

            KnowledgeBase knowledgeBase = knowledgeBaseMapper.getById(knowledgeBaseId);
            if (knowledgeBase == null || Boolean.TRUE.equals(knowledgeBase.getIsDeleted())) {
                return R.fail("知识库不存在");
            }

            String originalFileName = file.getOriginalFilename();
            if (!StringUtils.hasText(originalFileName)) {
                originalFileName = "document-" + System.currentTimeMillis();
            }

            String documentId = UUIDUtils.generateUUID();
            String objectName = buildObjectName(knowledgeBaseId, documentId, originalFileName);

            String storageUrl = minioService.uploadFile(file, objectName);
            log.info("知识库文档已上传到 MinIO: object={}, storageUrl={}", objectName, storageUrl);

            KnowledgeDocument document = new KnowledgeDocument();
            document.setId(documentId);
            document.setKnowledgeBaseId(knowledgeBaseId);
            document.setTitle(stripExtension(originalFileName));
            document.setOriginalFileName(originalFileName);
            document.setFileType(file.getContentType());
            document.setFileSize(file.getSize());
            document.setStatus(KnowledgeDocumentStatus.UPLOADED);
            document.setStorageUrl(storageUrl);
            document.setStorageObject(objectName);

            RagIngestionTask task = knowledgeDocumentService.createDocument(document, StpUtil.getLoginIdAsLong());

            documentIngestionService.ingestAsync(task.getId());

            log.info("知识库文档元数据创建完成: knowledgeBaseId={}, documentId={}, taskId={}",
                knowledgeBaseId, documentId, task.getId());

            return R.ok(KnowledgeDocumentUploadVO.builder()
                .document(convertToVO(document))
                .taskId(task.getId())
                .build());
        } catch (MinioException | IOException | NoSuchAlgorithmException | InvalidKeyException ex) {
            log.error("上传文档失败", ex);
            return R.fail("文件上传失败: " + ex.getMessage());
        } catch (Exception ex) {
            log.error("创建文档元数据失败", ex);
            return R.fail("创建文档失败: " + ex.getMessage());
        }
    }

    /**
     * 获取知识库文档列表
     */
    @SaCheckLogin
    @GetMapping({
        "/knowledge-bases/{knowledgeBaseId}/documents",
        "/admin/knowledge-bases/{knowledgeBaseId}/documents",
        "/teacher/knowledge-bases/{knowledgeBaseId}/documents",
        "/student/knowledge-bases/{knowledgeBaseId}/documents"
    })
    public R<List<KnowledgeDocumentVO>> listDocuments(@PathVariable("knowledgeBaseId") String knowledgeBaseId) {
        List<KnowledgeDocument> documents = knowledgeDocumentService.listDocuments(knowledgeBaseId);
        log.info("查询知识库文档列表: knowledgeBaseId={}, count={}", knowledgeBaseId, documents.size());
        List<KnowledgeDocumentVO> vos = documents.stream()
            .map(this::convertToVO)
            .collect(Collectors.toList());
        return R.ok(vos);
    }

    /**
     * 获取文档详情
     */
    @SaCheckLogin
    @GetMapping({
        "/knowledge-bases/{knowledgeBaseId}/documents/{documentId}",
        "/admin/knowledge-bases/{knowledgeBaseId}/documents/{documentId}",
        "/teacher/knowledge-bases/{knowledgeBaseId}/documents/{documentId}",
        "/student/knowledge-bases/{knowledgeBaseId}/documents/{documentId}"
    })
    public R<KnowledgeDocumentVO> getDocument(@PathVariable("knowledgeBaseId") String knowledgeBaseId,
                                              @PathVariable("documentId") String documentId) {
        KnowledgeDocument document = knowledgeDocumentService.getDocument(documentId);
        if (document == null || !knowledgeBaseId.equals(document.getKnowledgeBaseId())) {
            return R.fail("文档不存在");
        }
        return R.ok(convertToVO(document));
    }

    /**
     * 删除文档
     */
    @SaCheckLogin
    @DeleteMapping({
        "/knowledge-bases/{knowledgeBaseId}/documents/{documentId}",
        "/admin/knowledge-bases/{knowledgeBaseId}/documents/{documentId}",
        "/teacher/knowledge-bases/{knowledgeBaseId}/documents/{documentId}"
    })
    public R<Boolean> deleteDocument(@PathVariable("knowledgeBaseId") String knowledgeBaseId,
                                     @PathVariable("documentId") String documentId) {
        KnowledgeDocument document = knowledgeDocumentService.getDocument(documentId);
        if (document == null || !knowledgeBaseId.equals(document.getKnowledgeBaseId())) {
            return R.fail("文档不存在");
        }
        knowledgeDocumentService.deleteDocument(documentId);
        return R.ok(true);
    }

    /**
     * 查询任务状态
     */
    @SaCheckLogin
    @GetMapping("/rag/documents/{taskId}/status")
    public R<RagIngestionTaskVO> getTaskStatus(@PathVariable("taskId") String taskId) {
        RagIngestionTask task = ragIngestionTaskService.getById(taskId);
        if (task == null) {
            return R.fail("任务不存在");
        }
        return R.ok(convertToVO(task));
    }

    private String buildObjectName(String knowledgeBaseId, String documentId, String originalFileName) {
        return MinioObjectPath.RAG_KNOWLEDGE_BASE_ROOT
            + knowledgeBaseId + "/"
            + MinioObjectPath.RAG_DOCUMENT_FOLDER
            + documentId + "/"
            + originalFileName;
    }

    private String stripExtension(String filename) {
        if (!StringUtils.hasText(filename)) {
            return filename;
        }
        int index = filename.lastIndexOf('.');
        if (index > 0) {
            return filename.substring(0, index);
        }
        return filename;
    }

    private KnowledgeDocumentVO convertToVO(KnowledgeDocument document) {
        return KnowledgeDocumentVO.builder()
            .id(document.getId())
            .knowledgeBaseId(document.getKnowledgeBaseId())
            .title(document.getTitle())
            .originalFileName(document.getOriginalFileName())
            .fileType(document.getFileType())
            .fileSize(document.getFileSize())
            .pageCount(document.getPageCount())
            .status(document.getStatus())
            .chunkCount(document.getChunkCount())
            .storageUrl(document.getStorageUrl())
            .uploadedAt(document.getUploadedAt())
            .processedAt(document.getProcessedAt())
            .failureReason(document.getFailureReason())
            .build();
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

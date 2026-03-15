package cn.yifan.drawsee.service.business;

import cn.yifan.drawsee.config.RagIngestionProperties;
import cn.yifan.drawsee.constant.KnowledgeDocumentStatus;
import cn.yifan.drawsee.constant.RagIngestionStage;
import cn.yifan.drawsee.mapper.KnowledgeBaseMapper;
import cn.yifan.drawsee.mapper.KnowledgeDocumentChunkMapper;
import cn.yifan.drawsee.pojo.entity.KnowledgeBase;
import cn.yifan.drawsee.pojo.entity.KnowledgeDocument;
import cn.yifan.drawsee.pojo.entity.KnowledgeDocumentChunk;
import cn.yifan.drawsee.pojo.entity.RagIngestionTask;
import cn.yifan.drawsee.service.base.MinioService;
import cn.yifan.drawsee.service.base.PythonRagService;
import cn.yifan.drawsee.service.business.parser.DocumentParser;
import cn.yifan.drawsee.service.business.parser.TextChunker;
import cn.yifan.drawsee.util.UUIDUtils;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

/**
 * 文档入库流水线服务
 *
 * <p>负责协调解析、分块、向量化等耗时操作 已迁移到Python RAG微服务 - 文档导入调用Python服务的文档入库API
 *
 * @author yifan
 * @date 2025-10-10
 */
@Service
@Slf4j
public class DocumentIngestionService {

  @Autowired private KnowledgeDocumentService knowledgeDocumentService;

  @Autowired private RagIngestionTaskService ragIngestionTaskService;

  @Autowired private ObjectProvider<PythonRagService> pythonRagServiceProvider;

  @Autowired private KnowledgeBaseMapper knowledgeBaseMapper;

  @Autowired private DocumentParser documentParser;

  @Autowired private TextChunker textChunker;

  @Autowired private EmbeddingService embeddingService;

  @Autowired private QdrantService qdrantService;

  @Autowired private KnowledgeDocumentChunkMapper knowledgeDocumentChunkMapper;

  @Autowired private MinioService minioService;

  @Autowired private RagIngestionProperties ragIngestionProperties;

  /**
   * 异步触发入库流程
   *
   * <p>已迁移到Python RAG服务 - 调用Python服务的文档入库API进行ETL处理
   */
  @Async("ragIngestionExecutor")
  public void ingestAsync(String taskId) {
    RagIngestionTask task = ragIngestionTaskService.getById(taskId);
    if (task == null) {
      log.warn("RAG任务不存在: {}", taskId);
      return;
    }

    KnowledgeDocument document = knowledgeDocumentService.getDocument(task.getDocumentId());
    if (document == null) {
      ragIngestionTaskService.markFailed(taskId, "文档元数据不存在");
      knowledgeDocumentService.updateStatus(
          task.getDocumentId(), KnowledgeDocumentStatus.FAILED, "文档元数据不存在");
      return;
    }

    try {
      KnowledgeBase knowledgeBase = knowledgeBaseMapper.getById(document.getKnowledgeBaseId());
      if (knowledgeBase == null) {
        throw new IllegalStateException("知识库不存在: " + document.getKnowledgeBaseId());
      }

      if (document.getStorageObject() == null || document.getStorageObject().isBlank()) {
        throw new IllegalStateException("文档存储对象未知，无法下载");
      }

      String mode = ragIngestionProperties != null ? ragIngestionProperties.getMode() : "JAVA";
      if (mode != null && mode.equalsIgnoreCase("PYTHON")) {
        ingestWithPython(task, document);
      } else {
        ingestWithJava(task, document);
      }

    } catch (Exception ex) {
      log.error("文档入库流程失败, taskId={}", taskId, ex);
      ragIngestionTaskService.markFailed(taskId, ex.getMessage());
      knowledgeDocumentService.updateStatus(
          document.getId(), KnowledgeDocumentStatus.FAILED, ex.getMessage());
    }
  }

  private void ingestWithPython(RagIngestionTask task, KnowledgeDocument document) {
    PythonRagService pythonRagService = pythonRagServiceProvider.getIfAvailable();
    if (pythonRagService == null) {
      throw new IllegalStateException("Python RAG 服务未启用");
    }
    if (!pythonRagService.isServiceAvailable()) {
      throw new IllegalStateException("Python RAG 服务不可用");
    }
    ragIngestionTaskService.updateTask(
        task, RagIngestionStage.PARSING, KnowledgeDocumentStatus.PARSING, 10);
    knowledgeDocumentService.updateStatus(document.getId(), KnowledgeDocumentStatus.PARSING, null);

    log.info(
        "调用Python RAG服务进行文档入库: documentId={}, storage_object={}",
        document.getId(),
        document.getStorageObject());

    Map<String, Object> result =
        pythonRagService.ingestDocument(
            document.getId(),
            document.getKnowledgeBaseId(),
            "",
            document.getUploaderId(),
            document.getStorageObject());

    if (result == null) {
      throw new IllegalStateException("Python RAG 服务返回结果为空");
    }

    Boolean success = (Boolean) result.get("success");
    if (!Boolean.TRUE.equals(success)) {
      String message = (String) result.get("message");
      throw new IllegalStateException("Python RAG 服务处理失败: " + message);
    }

    String pythonTaskId = (String) result.get("task_id");
    log.info(
        "Python RAG服务已接受文档入库请求: documentId={}, pythonTaskId={}", document.getId(), pythonTaskId);

    ragIngestionTaskService.markCompleted(task.getId());
    log.info("文档入库任务已委托给Python RAG服务: taskId={}, pythonTaskId={}", task.getId(), pythonTaskId);
  }

  private void ingestWithJava(RagIngestionTask task, KnowledgeDocument document) {
    long start = System.currentTimeMillis();
    ragIngestionTaskService.updateTask(
        task, RagIngestionStage.PARSING, KnowledgeDocumentStatus.PARSING, 10);
    knowledgeDocumentService.updateStatus(document.getId(), KnowledgeDocumentStatus.PARSING, null);

    try (var stream = minioService.downloadFile(document.getStorageObject())) {
      DocumentParser.ParsedDocument parsed =
          documentParser.parse(stream, document.getOriginalFileName(), document.getFileType());
      String content = parsed.getContent();
      if (content == null || content.isBlank()) {
        throw new IllegalStateException("文档解析结果为空");
      }
      knowledgeDocumentService.updateParsedMetadata(document.getId(), parsed.getPageCount());

      ragIngestionTaskService.updateTask(
          task, RagIngestionStage.CHUNKING, KnowledgeDocumentStatus.CHUNKING, 30);
      knowledgeDocumentService.updateStatus(
          document.getId(), KnowledgeDocumentStatus.CHUNKING, null);

      var chunks = textChunker.chunk(content);
      if (chunks.isEmpty()) {
        throw new IllegalStateException("文档切分结果为空");
      }

      ragIngestionTaskService.updateTask(
          task, RagIngestionStage.EMBEDDING, KnowledgeDocumentStatus.EMBEDDING, 50);
      knowledgeDocumentService.updateStatus(
          document.getId(), KnowledgeDocumentStatus.EMBEDDING, null);

      int total = chunks.size();
      int batchSize =
          ragIngestionProperties != null && ragIngestionProperties.getBatchSize() != null
              ? ragIngestionProperties.getBatchSize()
              : 64;

      List<KnowledgeDocumentChunk> chunkEntities = new java.util.ArrayList<>();
      List<QdrantService.QdrantPoint> points = new java.util.ArrayList<>();
      String sampleVectorId = null;

      for (int i = 0; i < total; i++) {
        String chunkText = chunks.get(i);
        double[] embedding = embeddingService.generateEmbedding(chunkText);
        int dimension = embedding.length;

        QdrantService.QdrantPoint point = new QdrantService.QdrantPoint();
        String vectorId = UUIDUtils.generateUUID();
        if (sampleVectorId == null) {
          sampleVectorId = vectorId;
        }
        point.setId(vectorId);
        point.setVector(toDoubleList(embedding));
        Map<String, Object> payload = new java.util.HashMap<>();
        payload.put("knowledgeBaseId", document.getKnowledgeBaseId());
        payload.put("documentId", document.getId());
        payload.put("chunkIndex", i);
        point.setPayload(payload);
        points.add(point);

        KnowledgeDocumentChunk chunkEntity = new KnowledgeDocumentChunk();
        chunkEntity.setId(UUIDUtils.generateUUID());
        chunkEntity.setDocumentId(document.getId());
        chunkEntity.setKnowledgeBaseId(document.getKnowledgeBaseId());
        chunkEntity.setChunkIndex(i);
        chunkEntity.setContent(chunkText);
        chunkEntity.setTokenCount(chunkText.length());
        chunkEntity.setVectorId(vectorId);
        chunkEntity.setVectorDimension(dimension);
        java.util.Date now = new java.util.Date();
        chunkEntity.setCreatedAt(now);
        chunkEntity.setUpdatedAt(now);
        chunkEntities.add(chunkEntity);

        if (points.size() >= batchSize) {
          qdrantService.ensureCollection(dimension);
          qdrantService.upsertPoints(points);
          knowledgeDocumentChunkMapper.insertBatch(new java.util.ArrayList<>(chunkEntities));
          points.clear();
          chunkEntities.clear();
        }

        int progress = 50 + (int) ((i + 1) * 40.0 / total);
        task.setDurationMs(System.currentTimeMillis() - start);
        ragIngestionTaskService.updateTask(
            task, RagIngestionStage.EMBEDDING, KnowledgeDocumentStatus.EMBEDDING, progress);
      }

      if (!points.isEmpty()) {
        int dimension = points.get(0).getVector().size();
        qdrantService.ensureCollection(dimension);
        qdrantService.upsertPoints(points);
        knowledgeDocumentChunkMapper.insertBatch(chunkEntities);
      }

      if (sampleVectorId != null) {
        Map<String, Object> sample =
            qdrantService.getPointsByIds(java.util.List.of(sampleVectorId));
        log.info(
            "Qdrant 读取校验: documentId={}, sampleId={}, response={}",
            document.getId(),
            sampleVectorId,
            sample);
      }

      knowledgeDocumentService.setChunkCount(document.getId(), total);
      ragIngestionTaskService.updateTask(
          task, RagIngestionStage.INDEXING, KnowledgeDocumentStatus.INDEXING, 90);
      knowledgeDocumentService.updateStatus(
          document.getId(), KnowledgeDocumentStatus.INDEXING, null);

      // 验证是否真正写入 Qdrant
      long filteredCount =
          extractCount(qdrantService.countPoints(document.getKnowledgeBaseId(), document.getId()));
      long totalCount = extractCount(qdrantService.countPoints(null, null));
      log.info(
          "Qdrant 写入校验: documentId={}, filteredCount={}, totalCount={}",
          document.getId(),
          filteredCount,
          totalCount);
      if (filteredCount <= 0 && totalCount <= 0) {
        throw new IllegalStateException("Qdrant 写入校验失败，未发现任何向量点");
      }

      task.setDurationMs(System.currentTimeMillis() - start);
      ragIngestionTaskService.markCompleted(task.getId());
      knowledgeDocumentService.updateStatus(
          document.getId(), KnowledgeDocumentStatus.COMPLETED, null);
      log.info("Java入库完成: documentId={}, chunks={}", document.getId(), total);
    } catch (Exception ex) {
      throw new IllegalStateException("Java入库失败: " + ex.getMessage(), ex);
    }
  }

  private List<Double> toDoubleList(double[] embedding) {
    List<Double> values = new java.util.ArrayList<>(embedding.length);
    for (double v : embedding) {
      values.add(v);
    }
    return values;
  }

  @SuppressWarnings("unchecked")
  private long extractCount(Map<String, Object> response) {
    if (response == null) {
      return 0L;
    }
    Object result = response.get("result");
    if (result instanceof Map) {
      Object count = ((Map<String, Object>) result).get("count");
      if (count instanceof Number) {
        return ((Number) count).longValue();
      }
    }
    return 0L;
  }
}

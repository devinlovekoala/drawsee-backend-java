package cn.yifan.drawsee.service.business;

import cn.yifan.drawsee.constant.KnowledgeDocumentStatus;
import cn.yifan.drawsee.mapper.KnowledgeBaseMapper;
import cn.yifan.drawsee.mapper.KnowledgeDocumentChunkMapper;
import cn.yifan.drawsee.mapper.KnowledgeDocumentMapper;
import cn.yifan.drawsee.pojo.entity.KnowledgeBase;
import cn.yifan.drawsee.pojo.entity.KnowledgeDocument;
import cn.yifan.drawsee.pojo.entity.KnowledgeDocumentChunk;
import cn.yifan.drawsee.pojo.entity.RagIngestionTask;
import cn.yifan.drawsee.service.base.MinioService;
import cn.yifan.drawsee.util.UUIDUtils;
import io.minio.errors.MinioException;
import java.io.IOException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.Date;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/** 文档元数据服务 */
@Service
@Slf4j
public class KnowledgeDocumentService {

  @Autowired private KnowledgeDocumentMapper knowledgeDocumentMapper;

  @Autowired private KnowledgeDocumentChunkMapper knowledgeDocumentChunkMapper;

  @Autowired private KnowledgeBaseMapper knowledgeBaseMapper;

  @Autowired private RagIngestionTaskService ragIngestionTaskService;

  @Autowired private MinioService minioService;

  // TODO: 已废弃 - 迁移到Python RAG服务
  // @Autowired
  // private WeaviateVectorStore weaviateVectorStore;

  /** 创建文档元数据并生成处理任务 */
  public RagIngestionTask createDocument(KnowledgeDocument document, Long uploaderId) {
    Date now = new Date();
    if (document.getId() == null) {
      document.setId(UUIDUtils.generateUUID());
    }
    document.setUploaderId(uploaderId);
    document.setUploadedAt(now);
    document.setCreatedAt(now);
    document.setUpdatedAt(now);
    document.setStatus(KnowledgeDocumentStatus.UPLOADED);
    document.setChunkCount(0);
    document.setIsDeleted(false);

    knowledgeDocumentMapper.insert(document);
    log.info(
        "知识库文档元数据写入成功: knowledgeBaseId={}, documentId={}, uploaderId={}",
        document.getKnowledgeBaseId(),
        document.getId(),
        uploaderId);
    refreshKnowledgeBaseRagStats(document.getKnowledgeBaseId());
    return ragIngestionTaskService.createTask(document);
  }

  public List<KnowledgeDocument> listDocuments(String knowledgeBaseId) {
    List<KnowledgeDocument> list =
        knowledgeDocumentMapper.listByKnowledgeBaseId(knowledgeBaseId, false);
    log.debug("从数据库读取知识库文档: knowledgeBaseId={}, size={}", knowledgeBaseId, list.size());
    return list;
  }

  public KnowledgeDocument getDocument(String documentId) {
    return knowledgeDocumentMapper.getById(documentId);
  }

  public void updateStatus(
      String documentId, KnowledgeDocumentStatus status, String failureReason) {
    KnowledgeDocument document = knowledgeDocumentMapper.getById(documentId);
    if (document == null) {
      log.warn("知识库文档不存在: {}", documentId);
      return;
    }
    document.setStatus(status);
    document.setFailureReason(failureReason);
    document.setUpdatedAt(new Date());
    if (status == KnowledgeDocumentStatus.COMPLETED) {
      document.setProcessedAt(new Date());
    }
    knowledgeDocumentMapper.update(document);
    refreshKnowledgeBaseRagStats(document.getKnowledgeBaseId());
  }

  public void increaseChunkCount(String documentId, int delta) {
    knowledgeDocumentMapper.increaseChunkCount(documentId, delta);
  }

  public void setChunkCount(String documentId, int chunkCount) {
    knowledgeDocumentMapper.setChunkCount(documentId, chunkCount);
  }

  public void updateParsedMetadata(String documentId, Integer pageCount) {
    knowledgeDocumentMapper.updatePageCount(documentId, pageCount);
  }

  /** 删除文档及相关资源 */
  public void deleteDocument(String documentId) {
    KnowledgeDocument document = knowledgeDocumentMapper.getById(documentId);
    if (document == null) {
      return;
    }

    List<KnowledgeDocumentChunk> chunks = knowledgeDocumentChunkMapper.listByDocumentId(documentId);
    KnowledgeBase knowledgeBase = knowledgeBaseMapper.getById(document.getKnowledgeBaseId());

    for (KnowledgeDocumentChunk chunk : chunks) {
      try {
        // TODO: 已废弃 - 应调用Python RAG服务删除向量
        // weaviateVectorStore.deleteChunk(knowledgeBase, chunk);
        log.debug("跳过向量删除（待迁移到Python RAG服务）: chunkId={}", chunk.getId());
      } catch (Exception ex) {
        log.warn("删除向量失败 documentId={}, chunkId={}", documentId, chunk.getId(), ex);
      }
    }

    knowledgeDocumentChunkMapper.deleteByDocumentId(documentId);

    if (document.getStorageObject() != null) {
      try {
        minioService.deleteObject(document.getStorageObject());
      } catch (MinioException | IOException | NoSuchAlgorithmException | InvalidKeyException ex) {
        log.warn("删除 MinIO 对象失败 object={}", document.getStorageObject(), ex);
      }
    }

    knowledgeDocumentMapper.softDelete(documentId);
    refreshKnowledgeBaseRagStats(document.getKnowledgeBaseId());
  }

  private void refreshKnowledgeBaseRagStats(String knowledgeBaseId) {
    if (knowledgeBaseId == null) {
      return;
    }
    int total = knowledgeDocumentMapper.countByKnowledgeBaseId(knowledgeBaseId);
    int completed = knowledgeDocumentMapper.countCompletedByKnowledgeBaseId(knowledgeBaseId);

    KnowledgeBase knowledgeBase = new KnowledgeBase();
    knowledgeBase.setId(knowledgeBaseId);
    knowledgeBase.setUpdatedAt(new Date());
    knowledgeBase.setRagEnabled(total > 0);
    knowledgeBase.setRagDocumentCount(completed);
    knowledgeBaseMapper.update(knowledgeBase);
  }
}

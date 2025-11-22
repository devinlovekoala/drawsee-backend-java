package cn.yifan.drawsee.service.business;

import cn.yifan.drawsee.constant.KnowledgeDocumentStatus;
import cn.yifan.drawsee.constant.RagIngestionStage;
import cn.yifan.drawsee.mapper.KnowledgeDocumentChunkMapper;
import cn.yifan.drawsee.mapper.KnowledgeBaseMapper;
import cn.yifan.drawsee.pojo.entity.KnowledgeDocument;
import cn.yifan.drawsee.pojo.entity.KnowledgeDocumentChunk;
import cn.yifan.drawsee.pojo.entity.RagIngestionTask;
import cn.yifan.drawsee.pojo.entity.KnowledgeBase;
import cn.yifan.drawsee.service.base.MinioService;
import cn.yifan.drawsee.service.business.parser.DocumentParser;
import cn.yifan.drawsee.service.business.parser.TextChunker;
import cn.yifan.drawsee.util.UUIDUtils;
import io.minio.GetObjectResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * 文档入库流水线服务
 *
 * 负责协调解析、分块、向量化等耗时操作
 *
 * @author yifan
 * @date 2025-10-10
 */
@Service
@Slf4j
public class DocumentIngestionService {

    @Autowired
    private KnowledgeDocumentService knowledgeDocumentService;

    @Autowired
    private KnowledgeDocumentChunkMapper knowledgeDocumentChunkMapper;

    @Autowired
    private RagIngestionTaskService ragIngestionTaskService;

    @Autowired
    private MinioService minioService;

    @Autowired
    private DocumentParser documentParser;

    @Autowired
    private TextChunker textChunker;

    @Autowired
    private EmbeddingService embeddingService;

    @Autowired
    private WeaviateVectorStore weaviateVectorStore;

    @Autowired
    private KnowledgeBaseMapper knowledgeBaseMapper;

    /**
     * 异步触发入库流程
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
            knowledgeDocumentService.updateStatus(task.getDocumentId(), KnowledgeDocumentStatus.FAILED, "文档元数据不存在");
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

            ragIngestionTaskService.updateTask(task, RagIngestionStage.PARSING, KnowledgeDocumentStatus.PARSING, 10);
            knowledgeDocumentService.updateStatus(document.getId(), KnowledgeDocumentStatus.PARSING, null);

            DocumentParser.ParsedDocument parsed;
            try (GetObjectResponse objectResponse = minioService.downloadFile(document.getStorageObject())) {
                parsed = documentParser.parse(objectResponse, document.getOriginalFileName(), document.getFileType());
            }

            knowledgeDocumentService.updateParsedMetadata(document.getId(), parsed.getPageCount());

            ragIngestionTaskService.updateTask(task, RagIngestionStage.CHUNKING, KnowledgeDocumentStatus.CHUNKING, 40);
            knowledgeDocumentService.updateStatus(document.getId(), KnowledgeDocumentStatus.CHUNKING, null);

            List<String> chunkTexts = textChunker.chunk(parsed.getContent());
            if (chunkTexts.isEmpty()) {
                throw new IllegalStateException("文档内容为空，无法生成知识块");
            }

            List<KnowledgeDocumentChunk> chunks = new ArrayList<>();
            Date now = new Date();
            int index = 0;

            ragIngestionTaskService.updateTask(task, RagIngestionStage.EMBEDDING, KnowledgeDocumentStatus.EMBEDDING, 70);
            knowledgeDocumentService.updateStatus(document.getId(), KnowledgeDocumentStatus.EMBEDDING, null);

            weaviateVectorStore.ensureClassExists(knowledgeBase);

            for (String chunkText : chunkTexts) {
                double[] embedding = embeddingService.generateEmbedding(chunkText);

                KnowledgeDocumentChunk chunk = new KnowledgeDocumentChunk();
                chunk.setId(UUIDUtils.generateUUID());
                chunk.setDocumentId(document.getId());
                chunk.setKnowledgeBaseId(document.getKnowledgeBaseId());
                chunk.setChunkIndex(index++);
                chunk.setContent(chunkText);
                chunk.setTokenCount(chunkText.length());
                chunk.setVectorId(chunk.getId());
                chunk.setVectorDimension(embedding.length);
                chunk.setCreatedAt(now);
                chunk.setUpdatedAt(now);

                weaviateVectorStore.upsertChunk(knowledgeBase, document, chunk, embedding);
                chunks.add(chunk);
            }

            knowledgeDocumentChunkMapper.insertBatch(chunks);
            knowledgeDocumentService.setChunkCount(document.getId(), chunks.size());

            ragIngestionTaskService.updateTask(task, RagIngestionStage.INDEXING, KnowledgeDocumentStatus.INDEXING, 90);
            knowledgeDocumentService.updateStatus(document.getId(), KnowledgeDocumentStatus.INDEXING, null);

            knowledgeDocumentService.updateStatus(document.getId(), KnowledgeDocumentStatus.COMPLETED, null);
            ragIngestionTaskService.markCompleted(taskId);
        } catch (Exception ex) {
            log.error("文档入库流程失败, taskId={}", taskId, ex);
            ragIngestionTaskService.markFailed(taskId, ex.getMessage());
            knowledgeDocumentService.updateStatus(document.getId(), KnowledgeDocumentStatus.FAILED, ex.getMessage());
        }
    }
}

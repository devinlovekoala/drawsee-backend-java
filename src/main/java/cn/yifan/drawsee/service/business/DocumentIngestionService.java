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
import cn.yifan.drawsee.service.base.PythonRagService;
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
import java.util.Map;

/**
 * 文档入库流水线服务
 *
 * 负责协调解析、分块、向量化等耗时操作
 * 已迁移到Python RAG微服务 - 文档导入调用Python服务的文档入库API
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
    private PythonRagService pythonRagService;

    // TODO: 已废弃 - 迁移到Python RAG服务
    // @Autowired
    // private WeaviateVectorStore weaviateVectorStore;

    @Autowired
    private KnowledgeBaseMapper knowledgeBaseMapper;

    /**
     * 异步触发入库流程
     *
     * 已迁移到Python RAG服务 - 调用Python服务的文档入库API进行ETL处理
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

            // 检查 Python RAG 服务是否可用
            if (!pythonRagService.isServiceAvailable()) {
                throw new IllegalStateException("Python RAG 服务不可用");
            }

            // 更新状态为 PARSING（准备调用 Python 服务）
            ragIngestionTaskService.updateTask(task, RagIngestionStage.PARSING, KnowledgeDocumentStatus.PARSING, 10);
            knowledgeDocumentService.updateStatus(document.getId(), KnowledgeDocumentStatus.PARSING, null);

            // 调用 Python RAG 服务进行 ETL 处理
            log.info("调用Python RAG服务进行文档入库: documentId={}, storage_object={}",
                document.getId(), document.getStorageObject());

            Map<String, Object> result = pythonRagService.ingestDocument(
                document.getId(),
                document.getKnowledgeBaseId(),
                "",  // class_id 留空，由 Python 服务处理
                document.getUploaderId(),
                document.getStorageObject()  // 传递 MinIO 对象名称
            );

            if (result == null) {
                throw new IllegalStateException("Python RAG 服务返回结果为空");
            }

            Boolean success = (Boolean) result.get("success");
            if (!Boolean.TRUE.equals(success)) {
                String message = (String) result.get("message");
                throw new IllegalStateException("Python RAG 服务处理失败: " + message);
            }

            String pythonTaskId = (String) result.get("task_id");
            log.info("Python RAG服务已接受文档入库请求: documentId={}, pythonTaskId={}",
                document.getId(), pythonTaskId);

            // Python 服务将异步处理 ETL，Java 端任务标记为完成
            // 后续状态更新由 Python 服务通过回调或轮询机制更新
            ragIngestionTaskService.markCompleted(taskId);
            log.info("文档入库任务已委托给Python RAG服务: taskId={}, pythonTaskId={}", taskId, pythonTaskId);

        } catch (Exception ex) {
            log.error("文档入库流程失败, taskId={}", taskId, ex);
            ragIngestionTaskService.markFailed(taskId, ex.getMessage());
            knowledgeDocumentService.updateStatus(document.getId(), KnowledgeDocumentStatus.FAILED, ex.getMessage());
        }
    }
}

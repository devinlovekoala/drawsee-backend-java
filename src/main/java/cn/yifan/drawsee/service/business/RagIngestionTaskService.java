package cn.yifan.drawsee.service.business;

import cn.yifan.drawsee.constant.KnowledgeDocumentStatus;
import cn.yifan.drawsee.constant.RagIngestionStage;
import cn.yifan.drawsee.mapper.RagIngestionTaskMapper;
import cn.yifan.drawsee.pojo.entity.KnowledgeDocument;
import cn.yifan.drawsee.pojo.entity.RagIngestionTask;
import cn.yifan.drawsee.util.UUIDUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Date;
import java.util.List;

/**
 * RAG 文档入库任务服务
 *
 * @author yifan
 * @date 2025-10-10
 */
@Service
@Slf4j
public class RagIngestionTaskService {

    @Autowired
    private RagIngestionTaskMapper ragIngestionTaskMapper;

    /**
     * 创建任务
     */
    public RagIngestionTask createTask(KnowledgeDocument document) {
        Date now = new Date();
        RagIngestionTask task = new RagIngestionTask();
        task.setId(UUIDUtils.generateUUID());
        task.setKnowledgeBaseId(document.getKnowledgeBaseId());
        task.setDocumentId(document.getId());
        task.setStage(RagIngestionStage.PENDING);
        task.setStatus(KnowledgeDocumentStatus.UPLOADED);
        task.setProgress(0);
        task.setCreatedAt(now);
        task.setUpdatedAt(now);

        ragIngestionTaskMapper.insert(task);
        return task;
    }

    /**
     * 更新任务进度
     */
    public void updateTask(RagIngestionTask task, RagIngestionStage stage, KnowledgeDocumentStatus status, int progress) {
        task.setStage(stage);
        task.setStatus(status);
        task.setProgress(progress);
        task.setUpdatedAt(new Date());
        ragIngestionTaskMapper.update(task);
    }

    /**
     * 标记完成
     */
    public void markCompleted(String taskId) {
        RagIngestionTask task = ragIngestionTaskMapper.getById(taskId);
        if (task == null) {
            log.warn("任务不存在: {}", taskId);
            return;
        }
        task.setStage(RagIngestionStage.COMPLETED);
        task.setStatus(KnowledgeDocumentStatus.COMPLETED);
        task.setProgress(100);
        task.setCompletedAt(new Date());
        task.setUpdatedAt(new Date());
        ragIngestionTaskMapper.update(task);
    }

    /**
     * 标记失败
     */
    public void markFailed(String taskId, String errorMessage) {
        RagIngestionTask task = ragIngestionTaskMapper.getById(taskId);
        if (task == null) {
            log.warn("任务不存在: {}", taskId);
            return;
        }
        task.setStage(RagIngestionStage.FAILED);
        task.setStatus(KnowledgeDocumentStatus.FAILED);
        task.setErrorMessage(errorMessage);
        task.setProgress(0);
        task.setUpdatedAt(new Date());
        ragIngestionTaskMapper.update(task);
    }

    /**
     * 获取待处理任务
     */
    public List<RagIngestionTask> fetchPendingTasks(int limit) {
        return ragIngestionTaskMapper.listPendingTasks(limit);
    }

    /**
     * 根据ID获取
     */
    public RagIngestionTask getById(String taskId) {
        return ragIngestionTaskMapper.getById(taskId);
    }

    public List<RagIngestionTask> listTasks(String knowledgeBaseId,
                                            String documentId,
                                            String stage,
                                            String status,
                                            int offset,
                                            int limit) {
        return ragIngestionTaskMapper.listTasks(knowledgeBaseId, documentId, stage, status, offset, limit);
    }

    public RagIngestionTask resetForRetry(String taskId) {
        RagIngestionTask task = ragIngestionTaskMapper.getById(taskId);
        if (task == null) {
            return null;
        }
        task.setStage(RagIngestionStage.PENDING);
        task.setStatus(KnowledgeDocumentStatus.UPLOADED);
        task.setProgress(0);
        task.setErrorMessage(null);
        task.setUpdatedAt(new Date());
        ragIngestionTaskMapper.update(task);
        return task;
    }
}

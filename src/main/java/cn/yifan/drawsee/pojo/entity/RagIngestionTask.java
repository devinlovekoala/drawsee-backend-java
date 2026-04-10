package cn.yifan.drawsee.pojo.entity;

import cn.yifan.drawsee.constant.KnowledgeDocumentStatus;
import cn.yifan.drawsee.constant.RagIngestionStage;
import java.io.Serializable;
import java.util.Date;
import lombok.Data;

/**
 * RAG 文档处理任务
 *
 * @author devin
 * @date 2025-10-10
 */
@Data
public class RagIngestionTask implements Serializable {

  /** 任务ID */
  private String id;

  /** 所属知识库ID */
  private String knowledgeBaseId;

  /** 文档ID */
  private String documentId;

  /** 当前阶段 */
  private RagIngestionStage stage;

  /** 当前状态 */
  private KnowledgeDocumentStatus status;

  /** 进度（0-100） */
  private Integer progress;

  /** 错误信息 */
  private String errorMessage;

  /** 处理耗时（毫秒） */
  private Long durationMs;

  /** 创建时间 */
  private Date createdAt;

  /** 更新时间 */
  private Date updatedAt;

  /** 完成时间 */
  private Date completedAt;
}

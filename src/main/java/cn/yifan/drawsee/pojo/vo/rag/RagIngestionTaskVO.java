package cn.yifan.drawsee.pojo.vo.rag;

import cn.yifan.drawsee.constant.KnowledgeDocumentStatus;
import cn.yifan.drawsee.constant.RagIngestionStage;
import java.util.Date;
import lombok.Builder;
import lombok.Data;

/** RAG入库任务返回对象 */
@Data
@Builder
public class RagIngestionTaskVO {

  private String id;
  private String knowledgeBaseId;
  private String documentId;
  private RagIngestionStage stage;
  private KnowledgeDocumentStatus status;
  private Integer progress;
  private String errorMessage;
  private Long durationMs;
  private Date createdAt;
  private Date updatedAt;
  private Date completedAt;
}

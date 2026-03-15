package cn.yifan.drawsee.pojo.vo.rag;

import cn.yifan.drawsee.constant.KnowledgeDocumentStatus;
import java.util.Date;
import lombok.Builder;
import lombok.Data;

/**
 * 知识库文档返回对象
 *
 * @author devin
 * @date 2025-10-10
 */
@Data
@Builder
public class KnowledgeDocumentVO {

  private String id;
  private String knowledgeBaseId;
  private String title;
  private String originalFileName;
  private String fileType;
  private Long fileSize;
  private Integer pageCount;
  private KnowledgeDocumentStatus status;
  private Integer chunkCount;
  private String storageUrl;
  private Date uploadedAt;
  private Date processedAt;
  private String failureReason;
}

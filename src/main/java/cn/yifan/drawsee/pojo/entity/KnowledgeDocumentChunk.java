package cn.yifan.drawsee.pojo.entity;

import java.io.Serializable;
import java.util.Date;
import lombok.Data;

/**
 * 知识库文档分块实体
 *
 * @author yifan
 * @date 2025-10-10
 */
@Data
public class KnowledgeDocumentChunk implements Serializable {

  /** 分块ID */
  private String id;

  /** 所属文档ID */
  private String documentId;

  /** 所属知识库ID */
  private String knowledgeBaseId;

  /** 分块序号 */
  private Integer chunkIndex;

  /** 分块文本内容 */
  private String content;

  /** token 数量 */
  private Integer tokenCount;

  /** 关联的向量ID（Weaviate对象ID等） */
  private String vectorId;

  /** 向量维度 */
  private Integer vectorDimension;

  /** 创建时间 */
  private Date createdAt;

  /** 更新时间 */
  private Date updatedAt;
}

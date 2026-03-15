package cn.yifan.drawsee.pojo.dto;

import java.io.Serializable;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 知识库数据传输对象
 *
 * @author yifan
 * @date 2025-03-28 20:15
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class KnowledgeBaseDTO implements Serializable {

  /** 知识库ID */
  private String id;

  /** 知识库名称 */
  private String name;

  /** 知识库描述 */
  private String description;

  /** 知识库主题/学科 */
  private String subject;

  /** 标签数组 */
  private String[] tags;

  /** 是否启用RAG功能 */
  private Boolean ragEnabled;

  /** 是否发布 */
  private Boolean isPublished;

  /** 成员列表 */
  private List<Long> members;

  /** 是否同步到RAGFlow */
  private Boolean syncToRagFlow;
}

package cn.yifan.drawsee.pojo.vo;

import java.io.Serializable;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 知识点VO
 *
 * @author yifan
 * @date 2025-05-09
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class KnowledgeVO implements Serializable {

  private static final long serialVersionUID = 1L;

  /** 知识点ID */
  private String id;

  /** 知识点名称 */
  private String name;

  /** 学科 */
  private String subject;

  /** 别名列表 */
  private List<String> aliases;

  /** 难度级别 */
  private Integer level;

  /** 父知识点ID */
  private String parentId;

  /** 子知识点ID列表 */
  private List<String> childrenIds;

  /** 所属知识库ID */
  private String knowledgeBaseId;

  /** 资源数量 */
  private Integer resourceCount;
}

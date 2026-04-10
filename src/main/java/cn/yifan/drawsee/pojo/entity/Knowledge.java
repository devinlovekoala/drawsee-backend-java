package cn.yifan.drawsee.pojo.entity;

import java.io.Serializable;
import java.util.Date;
import java.util.List;
import lombok.Data;

/**
 * 知识点实体类 MySQL数据库对应表：knowledge
 *
 * @author devin
 * @date 2025-04-15 15:30
 */
@Data
public class Knowledge implements Serializable {

  /** 主键ID */
  private String id;

  /** 知识点名称 */
  private String name;

  /** 学科 */
  private String subject;

  /** 别名列表（JSON存储） */
  private List<String> aliases;

  /** 难度级别 */
  private Integer level;

  /** 父知识点ID */
  private String parentId;

  /** 子知识点ID列表（JSON存储） */
  private List<String> childrenIds;

  /** 所属知识库ID */
  private String knowledgeBaseId;

  /** 创建者ID */
  private Long creatorId;

  /** 创建时间 */
  private Date createdAt;

  /** 更新时间 */
  private Date updatedAt;

  /** 是否已删除 */
  private Boolean isDeleted;
}

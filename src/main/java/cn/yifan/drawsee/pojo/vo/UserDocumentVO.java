package cn.yifan.drawsee.pojo.vo;

import java.util.Date;
import lombok.Data;

/**
 * 用户文档VO类
 *
 * @author yifan
 * @date 2025-07-25 19:00
 */
@Data
public class UserDocumentVO {

  /** 文档ID */
  private Long id;

  /** 文档UUID */
  private String uuid;

  /** 所属用户ID */
  private Long userId;

  /** 文档类型 */
  private String documentType;

  /** 文档标题 */
  private String title;

  /** 文档描述 */
  private String description;

  /** 文档URL */
  private String fileUrl;

  /** 文件大小（字节） */
  private Long fileSize;

  /** 文档标签 */
  private String tags;

  /** 创建时间 */
  private Date createdAt;

  /** 更新时间 */
  private Date updatedAt;
}

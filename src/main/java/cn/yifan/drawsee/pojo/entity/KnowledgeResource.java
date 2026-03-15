package cn.yifan.drawsee.pojo.entity;

import java.io.Serializable;
import java.util.Date;
import java.util.Map;
import lombok.Data;

/**
 * 知识点资源实体类 MySQL数据库对应表：knowledge_resource 主要用于存储各类资源，包括视频链接、文档URL等非RAG资源
 *
 * @author yifan
 * @date 2025-03-28 19:15
 */
@Data
public class KnowledgeResource implements Serializable {

  /** 主键ID */
  private String id;

  /** 所属知识点ID */
  private String knowledgeId;

  /** 所属知识库ID */
  private String knowledgeBaseId;

  /** 资源类型 document - 文档 video - 视频 audio - 音频 bilibili - B站视频 link - 网络链接 mp4 - MP4视频文件 */
  private String resourceType;

  /** 资源标题 */
  private String title;

  /** 资源描述 */
  private String description;

  /** 资源URL 可以是视频链接、文档下载链接等 */
  private String url;

  /** 本地资源路径 适用于本地存储的资源文件 */
  private String localPath;

  /** 资源封面图URL */
  private String coverUrl;

  /** 资源大小（字节） */
  private Long size;

  /** 资源持续时长（针对视频、音频，秒为单位） */
  private Integer duration;

  /** 创建时间 */
  private Date createdAt;

  /** 更新时间 */
  private Date updatedAt;

  /** 上传者/创建者ID */
  private Long creatorId;

  /** 是否已删除 */
  private Boolean isDeleted;

  /** 资源元数据，存储为JSON字符串，由MyBatis处理 */
  private Map<String, Object> metadata;
}

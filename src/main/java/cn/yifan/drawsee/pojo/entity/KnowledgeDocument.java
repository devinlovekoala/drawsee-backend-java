package cn.yifan.drawsee.pojo.entity;

import cn.yifan.drawsee.constant.KnowledgeDocumentStatus;
import lombok.Data;

import java.io.Serializable;
import java.util.Date;

/**
 * 知识库文档实体
 *
 * @author yifan
 * @date 2025-10-10
 */
@Data
public class KnowledgeDocument implements Serializable {

    /**
     * 文档ID
     */
    private String id;

    /**
     * 所属知识库ID
     */
    private String knowledgeBaseId;

    /**
     * 文档标题（默认使用文件名）
     */
    private String title;

    /**
     * 原始文件名
     */
    private String originalFileName;

    /**
     * 文件MIME类型
     */
    private String fileType;

    /**
     * 文件大小（字节）
     */
    private Long fileSize;

    /**
     * 页数 / 时长等统计数据
     */
    private Integer pageCount;

    /**
     * 当前处理状态
     */
    private KnowledgeDocumentStatus status;

    /**
     * 分块数量
     */
    private Integer chunkCount;

    /**
     * 存储路径（对象存储URL）
     */
    private String storageUrl;

    /**
     * 存储对象名（用于删除）
     */
    private String storageObject;

    /**
     * 上传人ID
     */
    private Long uploaderId;

    /**
     * 上传时间
     */
    private Date uploadedAt;

    /**
     * 处理完成时间
     */
    private Date processedAt;

    /**
     * 最近失败原因
     */
    private String failureReason;

    /**
     * 记录创建时间
     */
    private Date createdAt;

    /**
     * 记录更新时间
     */
    private Date updatedAt;

    /**
     * 是否删除
     */
    private Boolean isDeleted;
}

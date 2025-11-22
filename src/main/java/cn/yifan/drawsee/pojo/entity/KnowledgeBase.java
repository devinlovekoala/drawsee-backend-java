package cn.yifan.drawsee.pojo.entity;

import lombok.Data;
import java.io.Serializable;
import java.util.Date;
import java.util.List;

/**
 * @FileName KnowledgeBase
 * @Description 知识库实体类
 * @Author devin
 * @date 2025-03-28 11:05
 **/
@Data
public class KnowledgeBase implements Serializable {

    /**
     * 知识库ID
     */
    private String id;
    
    /**
     * 知识库名称
     */
    private String name;
    
    /**
     * 知识库描述
     */
    private String description;
    
    /**
     * 学科
     */
    private String subject;
    
    /**
     * 邀请码
     */
    private String invitationCode;
    
    /**
     * 创建者ID
     */
    private Long creatorId;
    
    /**
     * 关联班级ID列表（可选）
     */
    private List<Long> classIds;
    
    /**
     * 成员ID列表
     */
    private List<Long> members;
    
    /**
     * 创建时间
     */
    private Date createdAt;
    
    /**
     * 更新时间
     */
    private Date updatedAt;
    
    /**
     * 是否删除
     */
    private Boolean isDeleted;
    
    /**
     * 是否发布
     */
    private Boolean isPublished;
    
    /**
     * 是否启用RAG
     */
    private Boolean ragEnabled;
    
    /**
     * RAG知识库ID
     */
    private String ragKnowledgeId;
    
    /**
     * RAG文档数量
     */
    private Integer ragDocumentCount;
    
    /**
     * RAG数据集ID
     */
    private String ragDatasetId;
    
    /**
     * 是否同步到RAGFlow
     */
    private Boolean syncToRagFlow;
    
    /**
     * RAG同步状态
     */
    private String ragSyncStatus;
    
    /**
     * 最后同步时间
     */
    private Date lastSyncTime;
} 

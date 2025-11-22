package cn.yifan.drawsee.pojo.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;
import java.util.Date;
import java.util.List;

/**
 * @FileName KnowledgeBaseVO
 * @Description 知识库的VO类
 * @Author yifan
 * @date 2025-03-28 10:57
 **/

@Data
@AllArgsConstructor
@NoArgsConstructor
public class KnowledgeBaseVO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private String id;
    
    private String name;
    
    private String description;
    
    private String subject;
    
    private String invitationCode;
    
    private Long creatorId;
    
    private Date createdAt;
    
    private Date updatedAt;
    
    private List<Long> members;
    
    private Boolean isPublished;
    
    private Boolean isDeleted;
    
    private Integer knowledgeCount;
    
    private Integer memberCount;
    
    /**
     * 是否启用RAG功能
     */
    private Boolean ragEnabled;
    
    /**
     * RAGFlow知识库ID
     */
    private String ragKnowledgeId;
    
    /**
     * RAG知识库中的文档数量
     */
    private Integer ragDocumentCount;
} 

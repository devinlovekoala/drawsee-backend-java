package cn.yifan.drawsee.pojo.vo.rag;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.Date;
import java.util.Map;

/**
 * RAGFlow知识库响应VO
 * 
 * @author devin
 * @date 2023-11-28
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RagKnowledgeVO implements Serializable {
    
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
     * 嵌入模型
     */
    private String embeddingModel;
    
    /**
     * 知识库标签
     */
    private String[] tags;
    
    /**
     * 创建时间
     */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private Date createdAt;
    
    /**
     * 更新时间
     */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private Date updatedAt;
    
    /**
     * 文档数量
     */
    private Integer documentCount;
    
    /**
     * 块数量
     */
    private Integer chunkCount;
    
    /**
     * 元数据
     */
    private Map<String, Object> metadata;
    
    /**
     * 是否公开
     */
    private Boolean isPublic;
}
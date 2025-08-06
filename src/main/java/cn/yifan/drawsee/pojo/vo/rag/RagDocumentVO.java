package cn.yifan.drawsee.pojo.vo.rag;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;
import java.util.Date;
import java.util.Map;

/**
 * RAG文档VO对象
 * 
 * @author devin
 * @date 2025-05-08
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RagDocumentVO implements Serializable {
    
    @Serial
    private static final long serialVersionUID = 1L;
    
    /**
     * 文档ID
     */
    private String id;
    
    /**
     * 知识库ID
     */
    private String knowledgeId;
    
    /**
     * 文件名
     */
    private String fileName;
    
    /**
     * 文件类型
     */
    private String fileType;
    
    /**
     * 文件大小（字节）
     */
    private Long fileSize;
    
    /**
     * 文档块数量
     */
    private Integer chunkCount;
    
    /**
     * 文档处理状态
     */
    private String status;
    
    /**
     * 文档描述
     */
    private String description;
    
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
     * 文件类型
     */
    private String mimeType;
    
    /**
     * 元数据
     */
    private Map<String, Object> metadata;
    
    /**
     * 相关性得分
     */
    private Double score;
}
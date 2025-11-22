package cn.yifan.drawsee.pojo.dto.rag;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * RAGFlow创建知识库请求DTO
 * 
 * @author yifan
 * @date 2023-11-28
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RagCreateKnowledgeDTO implements Serializable {
    
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
     * 元数据
     */
    private Object metadata;
    
    /**
     * 知识库类型
     */
    private String type;
    
    /**
     * 向量存储类型
     */
    private String vectorStoreType;
}
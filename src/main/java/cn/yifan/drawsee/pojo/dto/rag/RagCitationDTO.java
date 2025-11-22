package cn.yifan.drawsee.pojo.dto.rag;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * RAGFlow引用片段DTO
 * 
 * @author yifan
 * @date 2025-05-08
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RagCitationDTO {
    
    /**
     * 文档ID
     */
    private String documentId;
    
    /**
     * 文档名称
     */
    private String documentName;
    
    /**
     * 文本内容
     */
    private String text;
    
    /**
     * 相似度得分
     */
    private Double score;
    
    /**
     * 起始位置
     */
    private Integer startPosition;
    
    /**
     * 结束位置
     */
    private Integer endPosition;
    
    /**
     * 页码 (仅PDF等分页文档)
     */
    private Integer pageNumber;
    
    /**
     * 章节标题
     */
    private String sectionTitle;
} 
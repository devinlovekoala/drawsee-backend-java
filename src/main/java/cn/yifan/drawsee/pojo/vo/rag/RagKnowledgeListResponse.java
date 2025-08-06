package cn.yifan.drawsee.pojo.vo.rag;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * RAGFlow知识库列表响应
 * 
 * @author devin
 * @date 2025-05-08
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class RagKnowledgeListResponse {
    
    /**
     * 知识库列表
     */
    private List<RagKnowledgeVO> knowledges;
    
    /**
     * 总数
     */
    private Integer total;
} 
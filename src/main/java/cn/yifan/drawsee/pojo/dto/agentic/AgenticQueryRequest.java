package cn.yifan.drawsee.pojo.dto.agentic;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

/**
 * Agentic RAG 查询请求
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AgenticQueryRequest {
    /**
     * 用户查询文本
     */
    private String query;

    /**
     * 知识库ID列表
     */
    private List<String> knowledgeBaseIds;

    /**
     * 是否包含图片
     */
    private Boolean hasImage;

    /**
     * 图片URL（如果有）
     */
    private String imageUrl;

    /**
     * 上下文信息（可选）
     */
    private Map<String, Object> context;

    /**
     * 查询选项（可选）
     */
    private Map<String, Object> options;
}

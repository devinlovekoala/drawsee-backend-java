package cn.yifan.drawsee.pojo.dto.rag;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

/**
 * RAGFlow聊天响应DTO
 * 
 * @author yifan
 * @date 2025-05-08
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RagChatResponseDTO {
    
    /**
     * 回答内容
     */
    private String answer;
    
    /**
     * 引用的文档片段
     */
    private List<RagCitationDTO> citations;
    
    /**
     * 相关文档信息
     */
    private List<Map<String, Object>> documents;
    
    /**
     * 查询耗时（毫秒）
     */
    private Long queryTime;
    
    /**
     * 会话ID
     */
    private String sessionId;
    
    /**
     * 网络搜索结果
     */
    private List<Map<String, Object>> webSearchResults;
    
    /**
     * 额外元数据
     */
    private Map<String, Object> metadata;
} 
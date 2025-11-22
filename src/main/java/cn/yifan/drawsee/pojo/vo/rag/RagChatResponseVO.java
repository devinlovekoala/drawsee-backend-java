package cn.yifan.drawsee.pojo.vo.rag;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

/**
 * RAG聊天响应VO
 * 用于接收RAGFlow的知识库问答响应
 * 
 * @author devin
 * @date 2025-05-09
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class RagChatResponseVO {
    
    /**
     * AI回答内容
     */
    private String answer;
    
    /**
     * 会话ID
     */
    private String sessionId;
    
    /**
     * 是否完成
     */
    private Boolean done;
    
    /**
     * 处理耗时（毫秒）
     */
    private Long costTime;
    
    /**
     * 流标记(目前未使用)
     */
    private Boolean stream;
    
    /**
     * 相关文档列表
     */
    private List<RagDocumentVO> documents;
    
    /**
     * 相关上下文块引用
     */
    private List<Map<String, Object>> references;
    
    /**
     * 相关的索引块
     */
    private List<Map<String, Object>> chunks;
}

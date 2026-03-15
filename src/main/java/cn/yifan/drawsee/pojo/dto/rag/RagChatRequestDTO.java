package cn.yifan.drawsee.pojo.dto.rag;

import java.util.List;
import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * RAG聊天请求DTO 用于向RAGFlow发送知识库问答请求
 *
 * @author yifan
 * @date 2025-05-09
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RagChatRequestDTO {

  /** 知识库ID */
  private String knowledgeId;

  /** 用户查询内容 */
  private String query;

  /** 会话ID，用于历史消息关联 */
  private String sessionId;

  /** 历史消息列表，格式为 [{"role": "user/assistant", "content": "消息内容"}] */
  private List<Map<String, String>> history;

  /** 系统提示语 */
  private String systemPrompt;

  /** 是否启用Web搜索增强 */
  private Boolean enableWebSearch;

  /** 是否使用流式响应 */
  private Boolean stream;

  /** 最大相关上下文数量 */
  private Integer maxContexts;

  /** 相似度阈值，低于此阈值的相关上下文将被过滤 */
  private Float similarityThreshold;
}

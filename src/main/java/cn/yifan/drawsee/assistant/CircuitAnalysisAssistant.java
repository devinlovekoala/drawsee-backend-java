package cn.yifan.drawsee.assistant;

import dev.langchain4j.service.TokenStream;

/**
 * 电路分析助教接口
 *
 * <p>使用LangChain4j的AiServices自动处理Tool调用 当需要查询知识库时，会自动调用AgenticRagTool
 *
 * @author Drawsee Team
 */
public interface CircuitAnalysisAssistant {

  /**
   * 分析电路设计（流式输出）
   *
   * <p>LangChain4j会根据查询内容自动决定是否调用searchKnowledgeBase Tool
   *
   * @param userMessage 用户查询内容（包含电路信息和问题）
   * @return TokenStream 流式响应
   */
  TokenStream chat(String userMessage);
}

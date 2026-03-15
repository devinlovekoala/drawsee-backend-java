package cn.yifan.drawsee.tool;

import cn.yifan.drawsee.service.base.PythonRagService;
import dev.langchain4j.agent.tool.Tool;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Agentic RAG Tool for LangChain4j
 *
 * <p>将Python的Agentic RAG系统包装为LangChain4j Tool Java的ChatModel可以在需要时调用此Tool获取知识库检索结果
 *
 * @author Drawsee Team
 */
@Component
@Slf4j
@ConditionalOnProperty(
    prefix = "drawsee.python-service",
    name = "enabled",
    havingValue = "true",
    matchIfMissing = true)
public class AgenticRagTool {

  @Autowired private PythonRagService pythonRagService;

  /**
   * 查询知识库（Agentic RAG v2）
   *
   * <p>使用Agentic RAG系统进行智能路由和多频道处理： - 自动识别输入类型（自然语言、公式、电路代码等） - 自动分类意图（概念查询、计算、分析等） - 路由到最合适的处理频道 -
   * 返回高质量的检索或计算结果
   *
   * @param query 用户查询内容
   * @param knowledgeBaseIds 知识库ID列表（可选，留空则使用用户有权限的所有知识库）
   * @return 知识库检索结果或计算结果的文本描述
   */
  @Tool(
      "Search knowledge base using Agentic RAG system. Use this when you need to retrieve information from course materials, textbooks, or circuit knowledge base.")
  public String searchKnowledgeBase(String query, List<String> knowledgeBaseIds) {
    try {
      log.info(
          "[AgenticRagTool] 调用知识库检索: query='{}...', kb_count={}",
          query.substring(0, Math.min(50, query.length())),
          knowledgeBaseIds != null ? knowledgeBaseIds.size() : 0);

      // 调用Python Agentic RAG同步接口
      Map<String, Object> result =
          pythonRagService.agenticQuerySync(
              query,
              knowledgeBaseIds,
              null, // classId - 将在Service层从上下文获取
              null // userId - 将在Service层从上下文获取
              );

      if (result == null) {
        log.error("[AgenticRagTool] Python服务返回null");
        return "知识库服务暂时不可用，请稍后再试。";
      }

      Boolean success = (Boolean) result.get("success");
      if (Boolean.TRUE.equals(success)) {
        String answer = (String) result.get("answer");
        String channel = (String) result.get("channel");

        log.info(
            "[AgenticRagTool] 检索成功: channel={}, answer_length={}",
            channel,
            answer != null ? answer.length() : 0);

        return answer != null ? answer : "未找到相关信息";
      } else {
        String error = (String) result.get("error");
        log.error("[AgenticRagTool] 检索失败: {}", error);
        return "知识库检索失败: " + (error != null ? error : "未知错误");
      }

    } catch (Exception e) {
      log.error("[AgenticRagTool] 调用异常: {}", e.getMessage(), e);
      return "知识库服务调用异常: " + e.getMessage();
    }
  }

  /** 简化版：仅传入查询文本，使用默认知识库 */
  @Tool(
      "Search knowledge base with default settings. Use when you need quick access to course materials.")
  public String searchKnowledgeBase(String query) {
    return searchKnowledgeBase(query, null);
  }
}

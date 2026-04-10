package cn.yifan.drawsee.service.business;

import cn.yifan.drawsee.config.RagQueryProperties;
import cn.yifan.drawsee.pojo.vo.rag.RagChatResponseVO;
import cn.yifan.drawsee.service.base.PythonRagService;
import cn.yifan.drawsee.util.TokenEstimator;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * RAG 检索服务
 *
 * <p>作为MCP工具调用Python RAG微服务进行知识检索 调用失败时返回null，由调用方回退到纯LLM生成（无RAG增强）
 *
 * @author devin
 * @date 2025-10-10
 */
@Service
@Slf4j
public class RagQueryService {

  private final ObjectProvider<PythonRagService> pythonRagServiceProvider;
  private final RagQueryProperties ragQueryProperties;

  @Autowired
  public RagQueryService(
      ObjectProvider<PythonRagService> pythonRagServiceProvider,
      RagQueryProperties ragQueryProperties) {
    this.pythonRagServiceProvider = pythonRagServiceProvider;
    this.ragQueryProperties = ragQueryProperties;
  }

  /**
   * RAG混合检索（支持多知识库、电路结构化数据）
   *
   * @param knowledgeBaseIds 参与检索的知识库集合
   * @param query 用户问题
   * @param history 对话历史（暂未使用）
   * @param userId 用户ID
   * @param classId 班级ID
   * @return RAG响应，失败时返回null（调用方应回退到纯LLM生成）
   */
  public RagChatResponseVO query(
      List<String> knowledgeBaseIds,
      String query,
      List<String> history,
      Long userId,
      String classId) {
    return queryWithBudget(knowledgeBaseIds, query, history, userId, classId, null);
  }

  public RagChatResponseVO queryWithBudget(
      List<String> knowledgeBaseIds,
      String query,
      List<String> history,
      Long userId,
      String classId,
      RagQueryBudget budget) {
    String mode = ragQueryProperties != null ? ragQueryProperties.getMode() : "JAVA";
    if (mode == null || !mode.equalsIgnoreCase("PYTHON")) {
      log.debug("RAG检索模式为Java（或未开启），跳过Python RAG调用");
      return null;
    }

    PythonRagService pythonRagService = pythonRagServiceProvider.getIfAvailable();
    if (pythonRagService == null) {
      log.warn("Python RAG服务未启用，返回null由调用方回退到纯LLM生成");
      return null;
    }

    // 检查Python RAG服务是否可用
    if (!pythonRagService.isServiceAvailable()) {
      log.warn("Python RAG服务不可用，返回null由调用方回退到纯LLM生成");
      return null;
    }

    try {
      log.info("使用Python RAG MCP服务进行混合检索: 用户{}, 问题: {}, 知识库: {}", userId, query, knowledgeBaseIds);

      // 调用Python RAG混合检索（向量+结构化数据）
      int topK = resolveTopK(budget);
      Map<String, Object> pythonResponse =
          pythonRagService.ragQuery(query, knowledgeBaseIds, classId, userId, topK);

      if (pythonResponse == null) {
        log.warn("Python RAG服务返回null，回退到纯LLM生成");
        return null;
      }

      // 转换Python服务响应为RagChatResponseVO
      return convertPythonResponseToVO(pythonResponse, budget);

    } catch (Exception e) {
      log.warn("Python RAG服务调用异常，返回null: {}", e.getMessage());
      // 像MCP工具失败一样，返回null让调用方回退到纯LLM
      return null;
    }
  }

  /**
   * 转换Python服务响应为VO对象
   *
   * <p>Python响应格式: { "success": true, "message": "检索成功，返回3条结果", "results": [ { "circuit_id": "...",
   * "score": 0.95, "caption": "这是一个共射极放大电路...", "bom": [...], "topology": {...}, "page_number": 3,
   * "image_url": "...", "document_id": "..." } ], "total": 3 }
   */
  private RagChatResponseVO convertPythonResponseToVO(
      Map<String, Object> pythonResponse, RagQueryBudget budget) {
    RagChatResponseVO response = new RagChatResponseVO();
    int maxChunksInContext =
        budget != null
            ? budget.getMaxChunksInContext()
            : getPositiveOrDefault(
                ragQueryProperties != null ? ragQueryProperties.getMaxChunksInContext() : null, 4);
    int contextMaxTokens =
        budget != null ? budget.getContextMaxTokens() : resolveDefaultRetrievalTokens();
    int chunkMaxTokens = budget != null ? budget.getChunkMaxTokens() : 600;

    // 提取检索结果
    @SuppressWarnings("unchecked")
    List<Map<String, Object>> results = (List<Map<String, Object>>) pythonResponse.get("results");

    if (results != null && !results.isEmpty()) {
      // 将电路检索结果转换为知识块格式
      List<Map<String, Object>> chunks = new ArrayList<>();
      StringBuilder combinedContext = new StringBuilder();

      int contextIndex = 0;
      int usedTokens = 0;
      for (Map<String, Object> result : results) {
        // 构造知识块
        Map<String, Object> chunk = new HashMap<>();

        // 基本信息
        chunk.put("circuit_id", result.get("circuit_id"));
        chunk.put("score", result.get("score"));
        chunk.put("page_number", result.get("page_number"));
        chunk.put("image_url", result.get("image_url"));
        chunk.put("document_id", result.get("document_id"));

        // 电路结构化数据
        chunk.put("caption", result.get("caption"));
        chunk.put("bom", result.get("bom"));
        chunk.put("topology", result.get("topology"));

        chunks.add(chunk);

        // 组装上下文（用于LLM生成）
        String caption = (String) result.get("caption");
        Object pageNum = result.get("page_number");
        if (contextIndex < maxChunksInContext && usedTokens < contextMaxTokens) {
          Double score =
              result.get("score") instanceof Number
                  ? ((Number) result.get("score")).doubleValue()
                  : null;
          String scoreText = score != null ? String.format("%.2f", score) : "-";
          String pageText = pageNum != null ? pageNum.toString() : "-";
          String compactCaption =
              TokenEstimator.trimToTokenBudget(
                  TokenEstimator.normalizeWhitespace(caption), chunkMaxTokens);
          String contextLine =
              String.format(
                  "图%d p%s s%s: %s\n", contextIndex + 1, pageText, scoreText, compactCaption);

          int lineTokens = TokenEstimator.estimateTokens(contextLine);
          if (usedTokens + lineTokens > contextMaxTokens) {
            int remainingTokens = Math.max(contextMaxTokens - usedTokens, 0);
            if (remainingTokens > 0) {
              combinedContext.append(
                  TokenEstimator.trimToTokenBudget(contextLine, remainingTokens));
            }
            break;
          }

          combinedContext.append(contextLine);
          usedTokens += lineTokens;
          contextIndex++;
        }
      }

      response.setChunks(chunks);

      // 设置组合的上下文（供LLM参考）
      response.setAnswer(combinedContext.toString());

      log.info("RAG检索成功: 返回{}个电路图相关结果", chunks.size());
    } else {
      response.setChunks(new ArrayList<>());
      response.setAnswer("");
      log.info("RAG检索无结果");
    }

    response.setDone(true);
    return response;
  }

  private int resolveTopK(RagQueryBudget budget) {
    if (budget != null && budget.getTopK() > 0) {
      return budget.getTopK();
    }
    return ragQueryProperties != null && ragQueryProperties.getTopK() != null
        ? ragQueryProperties.getTopK()
        : 5;
  }

  private int resolveDefaultRetrievalTokens() {
    if (ragQueryProperties == null) {
      return 2000;
    }
    int maxContextTokens = getPositiveOrDefault(ragQueryProperties.getMaxContextTokens(), 160000);
    double safeRatio = getRatioOrDefault(ragQueryProperties.getSafeInputRatio(), 0.65);
    int reservedOutput = getPositiveOrDefault(ragQueryProperties.getReservedOutputTokens(), 8000);
    int safeInputTokens =
        Math.min((int) Math.floor(maxContextTokens * safeRatio), maxContextTokens - reservedOutput);
    double retrievalRatio = getRatioOrDefault(ragQueryProperties.getRetrievalRatio(), 0.45);
    int retrievalTokens = (int) Math.floor(safeInputTokens * retrievalRatio);
    return Math.max(retrievalTokens, 0);
  }

  private int getPositiveOrDefault(Integer value, int defaultValue) {
    if (value == null || value <= 0) {
      return defaultValue;
    }
    return value;
  }

  private double getRatioOrDefault(Double value, double defaultValue) {
    if (value == null || value <= 0) {
      return defaultValue;
    }
    return value;
  }
}

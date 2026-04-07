package cn.yifan.drawsee.service.base;

import cn.yifan.drawsee.util.InternalJwtUtil;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.model.StreamingResponseHandler;
import dev.langchain4j.model.output.Response;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.reactive.function.client.WebClient;

/**
 * Python电路RAG微服务调用封装
 *
 * @author Drawsee Team
 */
@Service
@Slf4j
@ConditionalOnProperty(
    prefix = "drawsee.python-service",
    name = "enabled",
    havingValue = "true",
    matchIfMissing = true)
public class PythonRagService {

  @Autowired private RestTemplate restTemplate;

  @Autowired private WebClient.Builder webClientBuilder;

  @Autowired private InternalJwtUtil internalJwtUtil;

  @Value("${drawsee.python-service.base-url}")
  private String pythonServiceBaseUrl;

  @Value("${drawsee.python-service.timeout:60000}")
  private Integer timeout;

  /** 创建带认证的HTTP头 */
  private HttpHeaders createAuthHeaders(Long userId, String classId, String knowledgeBaseId) {
    String jwt = internalJwtUtil.generateToken(userId, classId, knowledgeBaseId);

    HttpHeaders headers = new HttpHeaders();
    headers.setContentType(MediaType.APPLICATION_JSON);
    headers.set("Authorization", "Bearer " + jwt);
    headers.set("X-Service-Name", "drawsee-java");
    return headers;
  }

  /** 健康检查 */
  public boolean healthCheck() {
    try {
      String url = pythonServiceBaseUrl + "/api/v1/rag/health";
      ResponseEntity<Map<String, Object>> response =
          restTemplate.exchange(
              url,
              HttpMethod.GET,
              null,
              new org.springframework.core.ParameterizedTypeReference<Map<String, Object>>() {});
      return response.getStatusCode() == HttpStatus.OK;
    } catch (Exception e) {
      log.error("Python服务健康检查失败: {}", e.getMessage());
      return false;
    }
  }

  /**
   * RAG混合检索（向量检索 + 结构化数据）
   *
   * @param query 用户查询
   * @param knowledgeBaseIds 知识库ID列表
   * @param classId 班级ID（多租户隔离）
   * @param userId 用户ID
   * @param topK 返回Top-K结果
   * @return 检索结果（包含caption、bom、topology等）
   */
  public Map<String, Object> ragQuery(
      String query,
      java.util.List<String> knowledgeBaseIds,
      String classId,
      Long userId,
      Integer topK) {
    String url = pythonServiceBaseUrl + "/api/v1/rag/query";

    Map<String, Object> request = new HashMap<>();
    request.put("query", query);
    request.put("knowledge_base_ids", knowledgeBaseIds);
    request.put("class_id", classId != null ? classId : ""); // 将null转换为空字符串，避免Python验证错误
    request.put("top_k", topK != null ? topK : 5);

    HttpEntity<Map<String, Object>> entity =
        new HttpEntity<>(
            request,
            createAuthHeaders(
                userId,
                classId,
                knowledgeBaseIds != null && !knowledgeBaseIds.isEmpty()
                    ? knowledgeBaseIds.get(0)
                    : null));

    try {
      log.info("调用Python RAG混合检索: 用户{}, 查询: {}, class_id: {}", userId, query, classId);
      ResponseEntity<Map<String, Object>> response =
          restTemplate.exchange(
              url,
              HttpMethod.POST,
              entity,
              new org.springframework.core.ParameterizedTypeReference<Map<String, Object>>() {});

      Map<String, Object> body = response.getBody();
      if (response.getStatusCode() == HttpStatus.OK && body != null) {
        Boolean success = (Boolean) body.get("success");
        if (Boolean.TRUE.equals(success)) {
          log.info("Python RAG检索成功: 返回{}条结果", body.get("total"));
          return body;
        } else {
          log.warn("Python RAG检索返回失败: {}", body.get("message"));
          return null;
        }
      } else {
        log.warn("Python服务返回异常状态码: {}", response.getStatusCode());
        return null;
      }
    } catch (HttpClientErrorException.Unauthorized e) {
      log.error("Python服务认证失败");
      return null;
    } catch (HttpClientErrorException e) {
      log.error("Python服务调用失败: HTTP {}", e.getStatusCode());
      return null;
    } catch (RestClientException e) {
      log.error("Python服务网络异常: {}", e.getMessage());
      return null;
    }
  }

  /** RAG查询（便捷方法，单个知识库） */
  public Map<String, Object> ragQuery(
      String query, String knowledgeBaseId, String classId, Long userId) {
    return ragQuery(
        query, java.util.Collections.singletonList(knowledgeBaseId), classId, userId, 5);
  }

  /**
   * Phase 2: 混合检索（电路图 + 文本chunks）
   *
   * <p>使用RRF算法融合电路图和文本chunks的检索结果
   *
   * @param query 用户查询
   * @param knowledgeBaseIds 知识库ID列表
   * @param topK 返回Top-K结果
   * @param circuitWeight 电路图权重（0-1）
   * @param textWeight 文本chunks权重（0-1）
   * @param scoreThreshold 相似度阈值
   * @param searchMode 检索模式: hybrid/circuit_only/text_only
   * @return 混合检索结果
   */
  public Map<String, Object> hybridSearch(
      String query,
      java.util.List<String> knowledgeBaseIds,
      Integer topK,
      Double circuitWeight,
      Double textWeight,
      Double scoreThreshold,
      String searchMode) {
    String url = pythonServiceBaseUrl + "/api/v1/rag/hybrid-search";

    Map<String, Object> request = new HashMap<>();
    request.put("query", query);
    request.put("knowledge_base_ids", knowledgeBaseIds);
    request.put("top_k", topK != null ? topK : 10);
    request.put("circuit_weight", circuitWeight != null ? circuitWeight : 0.5);
    request.put("text_weight", textWeight != null ? textWeight : 0.5);
    request.put("score_threshold", scoreThreshold != null ? scoreThreshold : 0.6);
    request.put("search_mode", searchMode != null ? searchMode : "hybrid");

    HttpEntity<Map<String, Object>> entity =
        new HttpEntity<>(
            request,
            createAuthHeaders(
                0L,
                null,
                knowledgeBaseIds != null && !knowledgeBaseIds.isEmpty()
                    ? knowledgeBaseIds.get(0)
                    : null));

    try {
      log.info("调用Python混合检索: 查询: {}, mode: {}, top_k: {}", query, searchMode, topK);
      ResponseEntity<Map<String, Object>> response =
          restTemplate.exchange(
              url,
              HttpMethod.POST,
              entity,
              new org.springframework.core.ParameterizedTypeReference<Map<String, Object>>() {});

      Map<String, Object> body = response.getBody();
      if (response.getStatusCode() == HttpStatus.OK && body != null) {
        Boolean success = (Boolean) body.get("success");
        if (Boolean.TRUE.equals(success)) {
          log.info(
              "混合检索成功: 返回{}条结果（电路图{}条，文本{}条）",
              body.get("total"),
              body.get("circuit_count"),
              body.get("text_count"));
          return body;
        } else {
          log.warn("混合检索返回失败: {}", body.get("message"));
          return null;
        }
      } else {
        log.warn("Python服务返回异常状态码: {}", response.getStatusCode());
        return null;
      }
    } catch (HttpClientErrorException e) {
      log.error("混合检索调用失败: HTTP {}", e.getStatusCode());
      return null;
    } catch (RestClientException e) {
      log.error("混合检索网络异常: {}", e.getMessage());
      return null;
    }
  }

  /** Phase 2: 混合检索（便捷方法，使用默认参数） */
  public Map<String, Object> hybridSearch(
      String query, java.util.List<String> knowledgeBaseIds, Integer topK) {
    return hybridSearch(query, knowledgeBaseIds, topK, 0.5, 0.5, 0.6, "hybrid");
  }

  /**
   * Agentic RAG v2 同步查询（专为LangChain4j Tool集成设计）
   *
   * <p>此方法调用Python Agentic RAG服务的同步接口，返回完整结果。 适用于作为LangChain4j的Tool，让Java的ChatModel在需要时调用。
   *
   * @param query 用户查询
   * @param knowledgeBaseIds 知识库ID列表
   * @param classId 班级ID
   * @param userId 用户ID
   * @return 检索结果Map，包含answer、channel、confidence等字段
   */
  public Map<String, Object> agenticQuerySync(
      String query, java.util.List<String> knowledgeBaseIds, String classId, Long userId) {
    String url = pythonServiceBaseUrl + "/api/v1/rag/agentic/query-sync";

    Map<String, Object> request = new HashMap<>();
    request.put("query", query);
    request.put(
        "knowledge_base_ids",
        knowledgeBaseIds != null ? knowledgeBaseIds : Collections.emptyList());
    request.put("has_image", false);
    request.put("skip_classification", true); // 🔥 启用快速路径：跳过分类直接检索
    request.put(
        "context",
        Map.of(
            "class_id", classId != null ? classId : "",
            "user_id", userId != null ? userId : 0L));
    request.put("options", new HashMap<>());

    HttpEntity<Map<String, Object>> entity =
        new HttpEntity<>(request, createAuthHeaders(userId, classId, null));

    try {
      log.info(
          "[AgenticRAG-Sync] 同步查询（快速路径）: query='{}...', kb_count={}",
          query.length() > 50 ? query.substring(0, 50) : query,
          knowledgeBaseIds != null ? knowledgeBaseIds.size() : 0);

      ResponseEntity<Map<String, Object>> response =
          restTemplate.exchange(
              url,
              HttpMethod.POST,
              entity,
              new org.springframework.core.ParameterizedTypeReference<Map<String, Object>>() {});

      Map<String, Object> result = response.getBody();
      if (response.getStatusCode() == HttpStatus.OK && result != null) {
        log.info(
            "[AgenticRAG-Sync] 查询成功: channel={}, answer_length={}, elapsed_time={}s",
            result.get("channel"),
            result.get("answer") != null ? ((String) result.get("answer")).length() : 0,
            result.get("elapsed_time"));
        return result;
      } else {
        log.error("[AgenticRAG-Sync] 查询失败: status={}", response.getStatusCode());
        return Map.of("success", false, "error", "查询失败");
      }

    } catch (Exception e) {
      log.error("[AgenticRAG-Sync] 查询异常: {}", e.getMessage(), e);
      return Map.of("success", false, "error", "查询异常: " + e.getMessage());
    }
  }

  /**
   * Agentic RAG v2 流式查询（专为WorkFlow集成设计）
   *
   * <p>此方法调用Python Agentic RAG服务的简化流式接口，支持智能路由到6个频道： - FormulaChannel: 精确数学公式计算（SymPy） -
   * KnowledgeChannel: 强制RAG检索知识库 - NetlistChannel: SPICE网表解析 - VerilogChannel: Verilog代码分析 -
   * VisionChannel: 电路图图像识别 - CircuitReasoningChannel: 复杂电路推理（多步Agent）
   *
   * @param query 用户查询
   * @param knowledgeBaseIds 知识库ID列表
   * @param classId 班级ID
   * @param userId 用户ID
   * @param handler 流式响应处理器（LangChain4j的StreamingResponseHandler）
   */
  public void agenticStreamQuery(
      String query,
      java.util.List<String> knowledgeBaseIds,
      String classId,
      Long userId,
      StreamingResponseHandler<AiMessage> handler) {
    String url = pythonServiceBaseUrl + "/api/v1/rag/agentic/stream";

    Map<String, Object> request = new HashMap<>();
    request.put("query", query);
    request.put(
        "knowledge_base_ids",
        knowledgeBaseIds != null ? knowledgeBaseIds : Collections.emptyList());
    request.put("has_image", false);
    request.put("context", Map.of("class_id", classId != null ? classId : "", "user_id", userId));

    WebClient webClient = webClientBuilder.build();

    try {
      log.info(
          "[AgenticRAG] 开始流式查询: query='{}...', kb_count={}",
          query.length() > 50 ? query.substring(0, 50) : query,
          knowledgeBaseIds != null ? knowledgeBaseIds.size() : 0);

      // 用于累积完整文本
      StringBuilder accumulatedText = new StringBuilder();
      AtomicBoolean completed = new AtomicBoolean(false);

      webClient
          .post()
          .uri(url)
          .header("Authorization", "Bearer " + internalJwtUtil.generateToken(userId, classId, null))
          .header("Accept", "text/event-stream") // 明确指定接受SSE
          .contentType(MediaType.APPLICATION_JSON)
          .bodyValue(request)
          .retrieve()
          .bodyToFlux(String.class)
          .doOnNext(
              chunk -> {
                if (log.isDebugEnabled()) {
                  log.debug(
                      "[AgenticRAG] 收到SSE chunk: length={}, content='{}'",
                      chunk.length(),
                      chunk.length() > 100 ? chunk.substring(0, 100) + "..." : chunk);
                }
              })
          .filter(chunk -> chunk != null && !chunk.trim().isEmpty())
          .subscribe(
              // onNext: 处理每个SSE chunk
              chunk -> {
                // WebClient的bodyToFlux(String.class)会自动解析SSE格式
                // 已经去掉了"data: "前缀，chunk就是纯内容
                if ("[DONE]".equals(chunk.trim())) {
                  // 流式结束
                  if (completed.compareAndSet(false, true)) {
                    handler.onComplete(Response.from(AiMessage.from(accumulatedText.toString())));
                    log.info("[AgenticRAG] 流式查询完成: total_length={}", accumulatedText.length());
                  }
                } else if (!chunk.trim().isEmpty()) {
                  // 累积文本片段（跳过空行）
                  accumulatedText.append(chunk);
                  // 调用handler的onNext（LangChain4j会处理流式更新）
                  handler.onNext(chunk);
                  if (log.isDebugEnabled()) {
                    log.debug(
                        "[AgenticRAG] 累积文本: chunk_length={}, total_length={}",
                        chunk.length(),
                        accumulatedText.length());
                  }
                }
              },
              // onError: 处理错误
              error -> {
                log.error("[AgenticRAG] 流式查询失败: {}", error.getMessage(), error);
                handler.onError(error);
              },
              // onComplete: 流结束（如果没有收到[DONE]标记）
              () -> {
                if (completed.compareAndSet(false, true)) {
                  if (accumulatedText.length() > 0) {
                    handler.onComplete(Response.from(AiMessage.from(accumulatedText.toString())));
                    log.info(
                        "[AgenticRAG] 流式查询完成（无DONE标记）: total_length={}", accumulatedText.length());
                  } else {
                    log.warn("[AgenticRAG] 流结束但未收到任何内容");
                  }
                }
              });

    } catch (Exception e) {
      log.error("[AgenticRAG] 流式查询异常: {}", e.getMessage(), e);
      handler.onError(e);
    }
  }

  /**
   * 触发文档入库流程（ETL流水线）
   *
   * @param documentId 文档ID
   * @param knowledgeBaseId 知识库ID
   * @param classId 班级ID
   * @param userId 用户ID
   * @param pdfPath PDF文件路径
   * @return ETL任务信息
   */
  public Map<String, Object> ingestDocument(
      String documentId, String knowledgeBaseId, String classId, Long userId, String pdfPath) {
    String url = pythonServiceBaseUrl + "/api/v1/documents/ingest";

    Map<String, Object> request = new HashMap<>();
    request.put("document_id", documentId);
    request.put("knowledge_base_id", knowledgeBaseId);
    request.put("class_id", classId);
    request.put("user_id", userId);
    request.put("pdf_path", pdfPath);

    HttpEntity<Map<String, Object>> entity =
        new HttpEntity<>(request, createAuthHeaders(userId, classId, knowledgeBaseId));

    try {
      log.info("触发Python文档入库: 文档{}, 知识库{}, 班级{}", documentId, knowledgeBaseId, classId);
      ResponseEntity<Map<String, Object>> response =
          restTemplate.exchange(
              url,
              HttpMethod.POST,
              entity,
              new org.springframework.core.ParameterizedTypeReference<Map<String, Object>>() {});

      Map<String, Object> body = response.getBody();
      if (response.getStatusCode() == HttpStatus.OK && body != null) {
        String taskId = (String) body.get("task_id");
        log.info("文档入库触发成功: task_id={}, status={}", taskId, body.get("status"));
        return body;
      } else {
        log.warn("文档入库触发失败: HTTP {}", response.getStatusCode());
        return null;
      }
    } catch (Exception e) {
      log.error("文档入库触发异常: {}", e.getMessage());
      return null;
    }
  }

  /**
   * 查询ETL任务状态
   *
   * @param taskId ETL任务ID
   * @return 任务状态信息
   */
  public Map<String, Object> getTaskStatus(String taskId) {
    String url = pythonServiceBaseUrl + "/api/v1/documents/tasks/" + taskId;

    try {
      ResponseEntity<Map<String, Object>> response =
          restTemplate.exchange(
              url,
              HttpMethod.GET,
              null,
              new org.springframework.core.ParameterizedTypeReference<Map<String, Object>>() {});

      if (response.getStatusCode() == HttpStatus.OK && response.getBody() != null) {
        return response.getBody();
      } else {
        return null;
      }
    } catch (Exception e) {
      log.error("查询ETL任务状态失败: {}", e.getMessage());
      return null;
    }
  }

  /** 检查Python服务是否可用 */
  public boolean isServiceAvailable() {
    return healthCheck();
  }
}

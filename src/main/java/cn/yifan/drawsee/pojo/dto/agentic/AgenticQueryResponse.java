package cn.yifan.drawsee.pojo.dto.agentic;

import java.util.List;
import java.util.Map;
import lombok.Data;

/**
 * Agentic RAG 查询响应（来自Python SSE流）
 *
 * <p>注意：这是SSE事件的聚合结果，实际使用时通过SSE流式接收
 */
@Data
public class AgenticQueryResponse {
  /** 分类结果 */
  private Classification classification;

  /** 路由信息 */
  private Routing routing;

  /** 最终结果 */
  private Result result;

  /** 来源标注（仅KnowledgeChannel） */
  private List<Source> sources;

  /** 任务完成信息 */
  private TaskCompletion done;

  /** 错误信息（如果失败） */
  private Error error;

  /** 分类结果 */
  @Data
  public static class Classification {
    private String inputType;
    private Double inputTypeConfidence;
    private String intent;
    private Double intentConfidence;
  }

  /** 路由信息 */
  @Data
  public static class Routing {
    private String channel;
    private String reason;
    private Boolean fallback;
  }

  /** 处理结果 */
  @Data
  public static class Result {
    private Boolean success;
    private String channel;
    private Double confidence;

    // FormulaChannel 特定字段
    private Map<String, Object> problem;
    private Map<String, Object> solution;
    private String explanation;

    // KnowledgeChannel 特定字段
    private String answer;
    private Map<String, Object> retrievalStats;
  }

  /** 来源标注 */
  @Data
  public static class Source {
    private String type;
    private String content;
    private Map<String, Object> metadata;
    private Double score;
  }

  /** 任务完成 */
  @Data
  public static class TaskCompletion {
    private String taskId;
    private Double totalTime;
    private Boolean success;
  }

  /** 错误信息 */
  @Data
  public static class Error {
    private String error;
    private String code;
    private String detail;
  }
}

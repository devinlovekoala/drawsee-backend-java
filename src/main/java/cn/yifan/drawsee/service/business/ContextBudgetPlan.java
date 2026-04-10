package cn.yifan.drawsee.service.business;

import lombok.Getter;

@Getter
public class ContextBudgetPlan {

  private final int safeInputTokens;
  private final int historyBudgetTokens;
  private final int retrievalBudgetTokens;
  private final int historyTokens;
  private final int queryTokens;
  private final int maxChunksInContext;
  private final int chunkMaxTokens;
  private final int retrievalMaxTokens;
  private final int suggestedTopK;
  private final boolean historyCompressionNeeded;
  private final int historyRecentTurns;
  private final int historySummaryTokens;

  public ContextBudgetPlan(
      int safeInputTokens,
      int historyBudgetTokens,
      int retrievalBudgetTokens,
      int historyTokens,
      int queryTokens,
      int maxChunksInContext,
      int chunkMaxTokens,
      int retrievalMaxTokens,
      int suggestedTopK,
      boolean historyCompressionNeeded,
      int historyRecentTurns,
      int historySummaryTokens) {
    this.safeInputTokens = safeInputTokens;
    this.historyBudgetTokens = historyBudgetTokens;
    this.retrievalBudgetTokens = retrievalBudgetTokens;
    this.historyTokens = historyTokens;
    this.queryTokens = queryTokens;
    this.maxChunksInContext = maxChunksInContext;
    this.chunkMaxTokens = chunkMaxTokens;
    this.retrievalMaxTokens = retrievalMaxTokens;
    this.suggestedTopK = suggestedTopK;
    this.historyCompressionNeeded = historyCompressionNeeded;
    this.historyRecentTurns = historyRecentTurns;
    this.historySummaryTokens = historySummaryTokens;
  }
}

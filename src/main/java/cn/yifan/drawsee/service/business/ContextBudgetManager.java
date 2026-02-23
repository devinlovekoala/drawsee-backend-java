package cn.yifan.drawsee.service.business;

import cn.yifan.drawsee.config.RagQueryProperties;
import cn.yifan.drawsee.util.TokenEstimator;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.data.message.TextContent;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ContextBudgetManager {

    private static final int DEFAULT_MAX_CONTEXT_TOKENS = 160000;
    private static final double DEFAULT_SAFE_INPUT_RATIO = 0.65;
    private static final int DEFAULT_RESERVED_OUTPUT_TOKENS = 8000;
    private static final double DEFAULT_HISTORY_RATIO = 0.30;
    private static final double DEFAULT_RETRIEVAL_RATIO = 0.45;
    private static final double DEFAULT_CRITICAL_TOTAL_RATIO = 0.95;
    private static final int DEFAULT_LIGHT_HISTORY_TURNS = 8;
    private static final int DEFAULT_CRITICAL_HISTORY_TURNS = 4;
    private static final int DEFAULT_LIGHT_SUMMARY_TOKENS = 1600;
    private static final int DEFAULT_CRITICAL_SUMMARY_TOKENS = 2000;
    private static final int DEFAULT_CHUNK_MAX_TOKENS = 600;
    private static final int MIN_CHUNK_MAX_TOKENS = 500;

    private final RagQueryProperties ragQueryProperties;

    public ContextBudgetPlan plan(String query, List<ChatMessage> history) {
        int maxContextTokens = getPositiveOrDefault(ragQueryProperties.getMaxContextTokens(), DEFAULT_MAX_CONTEXT_TOKENS);
        double safeRatio = getRatioOrDefault(ragQueryProperties.getSafeInputRatio(), DEFAULT_SAFE_INPUT_RATIO);
        int reservedOutputTokens = getPositiveOrDefault(
            ragQueryProperties.getReservedOutputTokens(),
            DEFAULT_RESERVED_OUTPUT_TOKENS
        );
        int safeInputTokens = Math.min((int) Math.floor(maxContextTokens * safeRatio), maxContextTokens - reservedOutputTokens);
        int historyBudgetTokens = (int) Math.floor(safeInputTokens * getRatioOrDefault(ragQueryProperties.getHistoryRatio(), DEFAULT_HISTORY_RATIO));
        int retrievalBudgetTokens = (int) Math.floor(safeInputTokens * getRatioOrDefault(ragQueryProperties.getRetrievalRatio(), DEFAULT_RETRIEVAL_RATIO));

        int queryTokens = TokenEstimator.estimateTokens(query);
        int historyTokens = estimateHistoryTokens(history);
        int totalTokens = historyTokens + queryTokens;

        boolean critical = totalTokens > safeInputTokens * DEFAULT_CRITICAL_TOTAL_RATIO;
        boolean historyCompressionNeeded = historyTokens > historyBudgetTokens;

        int remainingTokens = Math.max(safeInputTokens - historyTokens - queryTokens, 0);
        int retrievalMaxTokens = Math.min(retrievalBudgetTokens, remainingTokens > 0 ? remainingTokens : retrievalBudgetTokens);

        int baseTopK = ragQueryProperties.getTopK() != null && ragQueryProperties.getTopK() > 0
            ? ragQueryProperties.getTopK()
            : 5;
        int suggestedTopK = baseTopK;
        int chunkMaxTokens = DEFAULT_CHUNK_MAX_TOKENS;
        if (critical || remainingTokens < retrievalBudgetTokens * 0.7) {
            suggestedTopK = Math.min(baseTopK, 3);
            chunkMaxTokens = MIN_CHUNK_MAX_TOKENS;
        }

        int maxChunksInContext = Math.min(
            getPositiveOrDefault(ragQueryProperties.getMaxChunksInContext(), suggestedTopK),
            Math.max(1, retrievalMaxTokens / Math.max(chunkMaxTokens, 1))
        );

        int historyRecentTurns = critical ? DEFAULT_CRITICAL_HISTORY_TURNS : DEFAULT_LIGHT_HISTORY_TURNS;
        int historySummaryTokens = critical ? DEFAULT_CRITICAL_SUMMARY_TOKENS : DEFAULT_LIGHT_SUMMARY_TOKENS;

        return new ContextBudgetPlan(
            safeInputTokens,
            historyBudgetTokens,
            retrievalBudgetTokens,
            historyTokens,
            queryTokens,
            maxChunksInContext,
            chunkMaxTokens,
            retrievalMaxTokens,
            suggestedTopK,
            historyCompressionNeeded,
            historyRecentTurns,
            historySummaryTokens
        );
    }

    public HistoryCompressionResult compressHistoryIfNeeded(List<ChatMessage> history, ContextBudgetPlan plan) {
        if (history == null || history.isEmpty() || !plan.isHistoryCompressionNeeded()) {
            return new HistoryCompressionResult(history, false);
        }
        int recentMessagesLimit = Math.max(plan.getHistoryRecentTurns() * 2, 2);
        int totalSize = history.size();
        int recentStart = Math.max(totalSize - recentMessagesLimit, 0);
        List<ChatMessage> recentMessages = new ArrayList<>(history.subList(recentStart, totalSize));
        List<ChatMessage> olderMessages = new ArrayList<>(history.subList(0, recentStart));

        String summary = summarizeMessages(olderMessages, plan.getHistorySummaryTokens());
        LinkedList<ChatMessage> compressed = new LinkedList<>();
        if (!summary.isEmpty()) {
            compressed.add(new SystemMessage("对话摘要: " + summary));
        }
        compressed.addAll(recentMessages);
        return new HistoryCompressionResult(compressed, true);
    }

    private int estimateHistoryTokens(List<ChatMessage> history) {
        if (history == null || history.isEmpty()) {
            return 0;
        }
        int total = 0;
        for (ChatMessage message : history) {
            total += TokenEstimator.estimateTokens(extractMessageText(message));
        }
        return total;
    }

    private String summarizeMessages(List<ChatMessage> messages, int maxTokens) {
        if (messages == null || messages.isEmpty() || maxTokens <= 0) {
            return "";
        }
        StringBuilder builder = new StringBuilder();
        int usedTokens = 0;
        for (ChatMessage message : messages) {
            String role = message instanceof UserMessage ? "用户" : "助手";
            String text = TokenEstimator.normalizeWhitespace(extractMessageText(message));
            if (text.isEmpty()) {
                continue;
            }
            String line = role + ": " + text;
            int lineTokens = TokenEstimator.estimateTokens(line);
            if (usedTokens + lineTokens > maxTokens) {
                line = TokenEstimator.trimToTokenBudget(line, Math.max(maxTokens - usedTokens, 0));
                builder.append(line);
                break;
            }
            builder.append(line);
            builder.append(" | ");
            usedTokens += lineTokens;
            if (usedTokens >= maxTokens) {
                break;
            }
        }
        String summary = builder.toString().trim();
        if (summary.endsWith("|")) {
            summary = summary.substring(0, summary.length() - 1).trim();
        }
        return summary;
    }

    private String extractMessageText(ChatMessage message) {
        if (message instanceof UserMessage userMessage) {
            if (userMessage.hasSingleText()) {
                return userMessage.singleText();
            }
            StringBuilder builder = new StringBuilder();
            for (var content : userMessage.contents()) {
                if (content instanceof TextContent textContent) {
                    if (!builder.isEmpty()) {
                        builder.append('\n');
                    }
                    builder.append(textContent.text());
                }
            }
            return builder.toString();
        }
        if (message instanceof AiMessage aiMessage) {
            return aiMessage.text();
        }
        if (message instanceof SystemMessage systemMessage) {
            return systemMessage.text();
        }
        return message != null ? message.toString() : "";
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

    @Getter
    public static class HistoryCompressionResult {
        private final List<ChatMessage> history;
        private final boolean compressed;

        public HistoryCompressionResult(List<ChatMessage> history, boolean compressed) {
            this.history = history;
            this.compressed = compressed;
        }
    }
}

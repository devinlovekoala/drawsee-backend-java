package cn.yifan.drawsee.service.business;

import cn.yifan.drawsee.config.RagQueryProperties;
import cn.yifan.drawsee.pojo.vo.rag.RagChatResponseVO;
import dev.langchain4j.data.message.ChatMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

@Service
@Slf4j
@RequiredArgsConstructor
public class RagEnhancementService {

    private final RagQueryService ragQueryService;
    private final RagQueryProperties ragQueryProperties;
    private final ContextBudgetManager contextBudgetManager;

    public RagChatResponseVO queryWithTimeout(
        List<String> knowledgeBaseIds,
        String prompt,
        List<ChatMessage> history,
        Long userId,
        String classId
    ) {
        if (knowledgeBaseIds == null || knowledgeBaseIds.isEmpty()) {
            return null;
        }
        ContextBudgetPlan budgetPlan = contextBudgetManager.plan(prompt, history);
        RagQueryBudget ragBudget = new RagQueryBudget(
            budgetPlan.getMaxChunksInContext(),
            budgetPlan.getChunkMaxTokens(),
            budgetPlan.getRetrievalMaxTokens(),
            budgetPlan.getSuggestedTopK()
        );
        return queryWithTimeout(knowledgeBaseIds, prompt, userId, classId, ragBudget);
    }

    public RagChatResponseVO queryWithTimeout(
        List<String> knowledgeBaseIds,
        String prompt,
        Long userId,
        String classId,
        RagQueryBudget ragBudget
    ) {
        long timeoutMs = ragQueryProperties != null && ragQueryProperties.getRagTimeoutMs() != null
            ? ragQueryProperties.getRagTimeoutMs()
            : 1200L;
        try {
            CompletableFuture<RagChatResponseVO> future = CompletableFuture.supplyAsync(() ->
                ragQueryService.queryWithBudget(
                    knowledgeBaseIds,
                    prompt,
                    null,
                    userId,
                    classId,
                    ragBudget
                )
            );
            return future.get(timeoutMs, TimeUnit.MILLISECONDS);
        } catch (TimeoutException e) {
            log.warn("RAG检索超时，跳过增强: timeoutMs={}, prompt='{}...'", timeoutMs,
                prompt != null && prompt.length() > 30 ? prompt.substring(0, 30) : prompt);
            return null;
        } catch (Exception e) {
            log.warn("RAG检索失败，跳过增强: {}", e.getMessage());
            return null;
        }
    }
}

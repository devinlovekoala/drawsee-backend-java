package cn.yifan.drawsee.service.business;

import lombok.Getter;

@Getter
public class RagQueryBudget {

    private final int maxChunksInContext;
    private final int chunkMaxTokens;
    private final int contextMaxTokens;
    private final int topK;

    public RagQueryBudget(int maxChunksInContext, int chunkMaxTokens, int contextMaxTokens, int topK) {
        this.maxChunksInContext = maxChunksInContext;
        this.chunkMaxTokens = chunkMaxTokens;
        this.contextMaxTokens = contextMaxTokens;
        this.topK = topK;
    }
}

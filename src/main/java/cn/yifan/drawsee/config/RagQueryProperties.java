package cn.yifan.drawsee.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * RAG检索配置
 */
@Configuration
@ConfigurationProperties(prefix = "drawsee.rag-query")
@Data
public class RagQueryProperties {

    /**
     * 检索模式: JAVA / PYTHON
     */
    private String mode = "JAVA";

    /**
     * 检索Top-K
     */
    private Integer topK = 5;

    /**
     * 模型最大上下文token
     */
    private Integer maxContextTokens = 160000;

    /**
     * 安全输入比例（避免吃满上下文）
     */
    private Double safeInputRatio = 0.65;

    /**
     * 预留输出token
     */
    private Integer reservedOutputTokens = 8000;

    /**
     * 历史对话预算比例
     */
    private Double historyRatio = 0.30;

    /**
     * RAG检索预算比例
     */
    private Double retrievalRatio = 0.45;

    /**
     * 参与拼接上下文的最大结果数（上限）
     */
    private Integer maxChunksInContext = 4;

    /**
     * RAG检索等待超时（毫秒）
     */
    private Long ragTimeoutMs = 1200L;

}

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
}

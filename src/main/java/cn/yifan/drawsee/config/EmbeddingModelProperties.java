package cn.yifan.drawsee.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Embedding 模型配置
 */
@Configuration
@ConfigurationProperties(prefix = "drawsee.models.embedding")
@Data
public class EmbeddingModelProperties {

    private String baseUrl;
    private String apiKey;
    private String modelName;
}

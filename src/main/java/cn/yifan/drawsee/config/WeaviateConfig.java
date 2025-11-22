package cn.yifan.drawsee.config;

import io.weaviate.client.Config;
import io.weaviate.client.WeaviateClient;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;

/**
 * Weaviate 向量数据库配置
 *
 * @author yifan
 * @date 2025-10-10
 */
@Configuration
@ConfigurationProperties(prefix = "rag.weaviate")
@Data
public class WeaviateConfig {

    /**
     * Weaviate 服务地址
     */
    private String endpoint;

    /**
     * API key，可为空
     */
    private String apiKey;

    /**
     * 默认使用的 class 前缀
     */
    private String classPrefix = "KB";

    /**
     * chunk 大小
     */
    private int chunkSize = 800;

    /**
     * chunk 重叠
     */
    private int chunkOverlap = 200;

    /**
     * 创建Weaviate客户端
     */
    @Bean
    public WeaviateClient weaviateClient() {
        Config config = new Config("http", endpoint.replace("http://", "").replace("https://", ""));
        return new WeaviateClient(config);
    }
}

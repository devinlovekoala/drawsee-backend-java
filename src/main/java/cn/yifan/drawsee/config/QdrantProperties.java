package cn.yifan.drawsee.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/** Qdrant 向量库配置 */
@Configuration
@ConfigurationProperties(prefix = "drawsee.qdrant")
@Data
public class QdrantProperties {

  private String host;
  private Integer port;
  private String apiKey;
  private Boolean https;
  private String collection;
}

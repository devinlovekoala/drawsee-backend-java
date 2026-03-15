package cn.yifan.drawsee.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/** RAG入库配置 */
@Configuration
@ConfigurationProperties(prefix = "drawsee.rag-ingestion")
@Data
public class RagIngestionProperties {

  /** 入库模式: JAVA / PYTHON */
  private String mode = "JAVA";

  /** 向量写入批大小 */
  private Integer batchSize = 64;
}

package cn.yifan.drawsee.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

/**
 * RAG 文档入库线程池配置
 *
 * @author yifan
 * @date 2025-10-10
 */
@Configuration
public class RagIngestionExecutorConfig {

    @Bean(name = "ragIngestionExecutor")
    public Executor ragIngestionExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(4);
        executor.setMaxPoolSize(8);
        executor.setQueueCapacity(200);
        executor.setThreadNamePrefix("rag-ingestion-");
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.initialize();
        return executor;
    }
}

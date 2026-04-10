package cn.yifan.drawsee.config;

import java.util.concurrent.Executor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/**
 * 通用异步执行器配置
 *
 * <p>为 @Async 默认执行器和 AI 任务本地派发提供稳定线程池，避免回落到临时执行器。
 */
@Configuration
public class AsyncTaskExecutorConfig {

  @Bean(name = "taskExecutor")
  @Primary
  public Executor taskExecutor() {
    ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
    executor.setCorePoolSize(6);
    executor.setMaxPoolSize(16);
    executor.setQueueCapacity(500);
    executor.setThreadNamePrefix("drawsee-async-");
    executor.setWaitForTasksToCompleteOnShutdown(true);
    executor.initialize();
    return executor;
  }
}

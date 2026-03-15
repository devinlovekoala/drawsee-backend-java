package cn.yifan.drawsee.config;

import cn.yifan.drawsee.pojo.langchain.AiModelConfig;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.model.openai.OpenAiChatRequestParameters;
import dev.langchain4j.model.openai.OpenAiStreamingChatModel;
import java.time.Duration;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * @FileName LangchainConfig @Description @Author yifan
 *
 * @date 2025-03-20 20:29
 */
@Configuration
@ConfigurationProperties(prefix = "drawsee.models")
@Data
public class LangchainConfig {

  private AiModelConfig qwen;

  private AiModelConfig deepseekV3;

  private AiModelConfig qwenVision;

  private AiModelConfig compress;

  @Bean("qwenChatLanguageModel")
  public ChatModel qwenChatLanguageModel() {
    return OpenAiChatModel.builder()
        .baseUrl(qwen.getBaseUrl())
        .apiKey(qwen.getApiKey())
        .modelName(qwen.getModelName())
        .defaultRequestParameters(resolveRequestParameters(qwen))
        .timeout(Duration.ofDays(7))
        .temperature(resolveTemperature(qwen, 0.7))
        .logRequests(false)
        .logResponses(false)
        .build();
  }

  @Bean("deepseekV3ChatLanguageModel")
  public ChatModel deepseekV3ChatLanguageModel() {
    return OpenAiChatModel.builder()
        .baseUrl(deepseekV3.getBaseUrl())
        .apiKey(deepseekV3.getApiKey())
        .modelName(deepseekV3.getModelName())
        .defaultRequestParameters(resolveRequestParameters(deepseekV3))
        .timeout(Duration.ofDays(7))
        .maxTokens(8192)
        .temperature(0.7)
        .logRequests(false)
        .logResponses(false)
        .build();
  }

  @Bean("qwenVisionChatLanguageModel")
  public ChatModel qwenVisionChatLanguageModel() {
    return OpenAiChatModel.builder()
        .baseUrl(qwenVision.getBaseUrl())
        .apiKey(qwenVision.getApiKey())
        .modelName(qwenVision.getModelName())
        .defaultRequestParameters(resolveRequestParameters(qwenVision))
        .timeout(Duration.ofDays(7))
        .temperature(resolveTemperature(qwenVision, 0.1))
        .logRequests(false)
        .logResponses(false)
        .build();
  }

  @Bean("qwenStreamingChatLanguageModel")
  public StreamingChatModel qwenStreamingChatLanguageModel() {
    return OpenAiStreamingChatModel.builder()
        .baseUrl(qwen.getBaseUrl())
        .apiKey(qwen.getApiKey())
        .modelName(qwen.getModelName())
        .defaultRequestParameters(resolveRequestParameters(qwen))
        .temperature(resolveTemperature(qwen, 0.7))
        .logRequests(false)
        .logResponses(false)
        .timeout(java.time.Duration.ofSeconds(180))
        .build();
  }

  @Bean("deepseekV3StreamingChatLanguageModel")
  public StreamingChatModel deepseekV3StreamingChatLanguageModel() {
    return OpenAiStreamingChatModel.builder()
        .baseUrl(deepseekV3.getBaseUrl())
        .apiKey(deepseekV3.getApiKey())
        .modelName(deepseekV3.getModelName())
        .defaultRequestParameters(resolveRequestParameters(deepseekV3))
        .temperature(0.7)
        .logRequests(false)
        .logResponses(false)
        .timeout(java.time.Duration.ofSeconds(180))
        .build();
  }

  @Bean("qwenVisionStreamingChatLanguageModel")
  public StreamingChatModel qwenVisionStreamingChatLanguageModel() {
    return OpenAiStreamingChatModel.builder()
        .baseUrl(qwenVision.getBaseUrl())
        .apiKey(qwenVision.getApiKey())
        .modelName(qwenVision.getModelName())
        .defaultRequestParameters(resolveRequestParameters(qwenVision))
        .temperature(resolveTemperature(qwenVision, 0.1))
        .logRequests(false)
        .logResponses(false)
        .build();
  }

  private double resolveTemperature(AiModelConfig config, double defaultValue) {
    if (config == null || config.getTemperature() == null) {
      return defaultValue;
    }
    return config.getTemperature();
  }

  private OpenAiChatRequestParameters resolveRequestParameters(AiModelConfig config) {
    if (config == null
        || config.getCustomParameters() == null
        || config.getCustomParameters().isEmpty()) {
      return OpenAiChatRequestParameters.builder().build();
    }
    return OpenAiChatRequestParameters.builder()
        .customParameters(config.getCustomParameters())
        .build();
  }
}

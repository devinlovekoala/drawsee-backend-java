package cn.yifan.drawsee.config;

import cn.yifan.drawsee.pojo.langchain.AiModelConfig;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.chat.StreamingChatLanguageModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.model.openai.OpenAiStreamingChatModel;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

/**
 * @FileName LangchainConfig
 * @Description
 * @Author yifan
 * @date 2025-03-20 20:29
 **/

@Configuration
@ConfigurationProperties(prefix = "drawsee.models")
@Data
public class LangchainConfig {

    private AiModelConfig qwen;

    private AiModelConfig deepseekV3;

    private AiModelConfig qwenVision;

    private AiModelConfig compress;

    @Bean("qwenChatLanguageModel")
    public ChatLanguageModel qwenChatLanguageModel() {
        return OpenAiChatModel.builder()
                .baseUrl(qwen.getBaseUrl())
                .apiKey(qwen.getApiKey())
                .modelName(qwen.getModelName())
                .timeout(Duration.ofDays(7))
                .temperature(resolveTemperature(qwen, 0.7))
                .logRequests(false)
                .logResponses(false)
                .build();
    }

    @Bean("deepseekV3ChatLanguageModel")
    public ChatLanguageModel deepseekV3ChatLanguageModel() {
        return OpenAiChatModel.builder()
                .baseUrl(deepseekV3.getBaseUrl())
                .apiKey(deepseekV3.getApiKey())
                .modelName(deepseekV3.getModelName())
                .timeout(Duration.ofDays(7))
                .maxTokens(8192)
                .temperature(0.7)
                .logRequests(false)
                .logResponses(false)
                .build();
    }

    @Bean("qwenVisionChatLanguageModel")
    public ChatLanguageModel qwenVisionChatLanguageModel() {
        return OpenAiChatModel.builder()
                .baseUrl(qwenVision.getBaseUrl())
                .apiKey(qwenVision.getApiKey())
                .modelName(qwenVision.getModelName())
                .timeout(Duration.ofDays(7))
                .temperature(resolveTemperature(qwenVision, 0.1))
                .logRequests(false)
                .logResponses(false)
                .build();
    }

    @Bean("qwenStreamingChatLanguageModel")
    public StreamingChatLanguageModel qwenStreamingChatLanguageModel() {
        return OpenAiStreamingChatModel.builder()
                .baseUrl(qwen.getBaseUrl())
                .apiKey(qwen.getApiKey())
                .modelName(qwen.getModelName())
                .temperature(resolveTemperature(qwen, 0.7))
                .logRequests(false)
                .logResponses(false)
                .timeout(java.time.Duration.ofSeconds(180))
                .build();
    }

    @Bean("deepseekV3StreamingChatLanguageModel")
    public StreamingChatLanguageModel deepseekV3StreamingChatLanguageModel() {
        return OpenAiStreamingChatModel.builder()
              .baseUrl(deepseekV3.getBaseUrl())
              .apiKey(deepseekV3.getApiKey())
              .modelName(deepseekV3.getModelName())
              .temperature(0.7)
              .logRequests(false)
              .logResponses(false)
              .timeout(java.time.Duration.ofSeconds(180))
              .build();
    }

    @Bean("qwenVisionStreamingChatLanguageModel")
    public StreamingChatLanguageModel qwenVisionStreamingChatLanguageModel() {
        return OpenAiStreamingChatModel.builder()
                .baseUrl(qwenVision.getBaseUrl())
                .apiKey(qwenVision.getApiKey())
                .modelName(qwenVision.getModelName())
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

}

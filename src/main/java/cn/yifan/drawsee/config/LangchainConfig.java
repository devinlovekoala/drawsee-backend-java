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

    private AiModelConfig doubao;

    private AiModelConfig deepseekV3;

    private AiModelConfig doubaoVision;

    @Bean("doubaoChatLanguageModel")
    public ChatLanguageModel doubaoChatLanguageModel() {
        return OpenAiChatModel.builder()
                .baseUrl(doubao.getBaseUrl())
                .apiKey(doubao.getApiKey())
                .modelName(doubao.getModelName())
                .timeout(Duration.ofDays(7))
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
                .temperature(0.1)
                .logRequests(false)
                .logResponses(false)
                .build();
    }

    @Bean("doubaoVisionChatLanguageModel")
    public ChatLanguageModel doubaoVisionChatLanguageModel() {
        return OpenAiChatModel.builder()
                .baseUrl(doubaoVision.getBaseUrl())
                .apiKey(doubaoVision.getApiKey())
                .modelName(doubaoVision.getModelName())
                .timeout(Duration.ofDays(7))
                .temperature(0.1)
                .logRequests(false)
                .logResponses(false)
                .build();
    }

    @Bean("doubaoStreamingChatLanguageModel")
    public StreamingChatLanguageModel doubaoStreamingChatLanguageModel() {
        return OpenAiStreamingChatModel.builder()
                .baseUrl(doubao.getBaseUrl())
                .apiKey(doubao.getApiKey())
                .modelName(doubao.getModelName())
                .logRequests(false)
                .logResponses(false)
                .build();
    }

    @Bean("deepseekV3StreamingChatLanguageModel")
    public StreamingChatLanguageModel deepseekV3StreamingChatLanguageModel() {
        return OpenAiStreamingChatModel.builder()
              .baseUrl(deepseekV3.getBaseUrl())
              .apiKey(deepseekV3.getApiKey())
              .modelName(deepseekV3.getModelName())
              .logRequests(false)
              .logResponses(false)
              .build();
    }

}

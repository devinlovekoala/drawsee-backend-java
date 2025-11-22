package cn.yifan.drawsee.service.business;

import cn.yifan.drawsee.config.EmbeddingModelProperties;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.List;

/**
 * 嵌入向量生成服务
 */
@Service
@Slf4j
public class EmbeddingService {

    private final RestTemplate restTemplate;
    private final EmbeddingModelProperties embeddingProperties;

    @Autowired
    public EmbeddingService(EmbeddingModelProperties embeddingProperties) {
        this.restTemplate = new RestTemplate();
        this.embeddingProperties = embeddingProperties;
    }

    public double[] generateEmbedding(String text) {
        if (!hasEmbeddingConfig()) {
            throw new IllegalStateException("Embedding 服务未配置");
        }

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        if (embeddingProperties.getApiKey() != null && !embeddingProperties.getApiKey().isBlank()) {
            headers.setBearerAuth(embeddingProperties.getApiKey());
        }

        EmbeddingRequest request = new EmbeddingRequest();
        request.setModel(embeddingProperties.getModelName());
        request.setInput(List.of(text));

        HttpEntity<EmbeddingRequest> entity = new HttpEntity<>(request, headers);
        String endpoint = embeddingProperties.getBaseUrl();

        EmbeddingResponse response;
        try {
            response = restTemplate.postForObject(endpoint, entity, EmbeddingResponse.class);
        } catch (Exception ex) {
            throw new IllegalStateException("调用嵌入服务失败", ex);
        }
        if (response == null || response.getData() == null || response.getData().isEmpty()) {
            throw new IllegalStateException("嵌入服务返回为空");
        }

        List<Double> embeddings = response.getData().get(0).getEmbedding();
        double[] vector = new double[embeddings.size()];
        for (int i = 0; i < embeddings.size(); i++) {
            vector[i] = embeddings.get(i);
        }
        return vector;
    }

    private boolean hasEmbeddingConfig() {
        return embeddingProperties != null
            && embeddingProperties.getBaseUrl() != null
            && embeddingProperties.getModelName() != null;
    }

    @Data
    private static class EmbeddingRequest {
        private String model;
        private List<String> input;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    private static class EmbeddingResponse {
        private List<EmbeddingData> data;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    private static class EmbeddingData {
        @JsonProperty("embedding")
        private List<Double> embedding;
    }
}

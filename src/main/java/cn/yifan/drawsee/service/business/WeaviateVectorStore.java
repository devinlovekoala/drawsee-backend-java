package cn.yifan.drawsee.service.business;

import cn.yifan.drawsee.config.WeaviateConfig;
import cn.yifan.drawsee.pojo.entity.KnowledgeBase;
import cn.yifan.drawsee.pojo.entity.KnowledgeDocument;
import cn.yifan.drawsee.pojo.entity.KnowledgeDocumentChunk;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Weaviate 向量存储服务
 */
@Service
@Slf4j
public class WeaviateVectorStore {

    private final RestTemplate restTemplate;
    private final WeaviateConfig weaviateConfig;
    private final Map<String, Boolean> classCache = new ConcurrentHashMap<>();

    @Autowired
    public WeaviateVectorStore(WeaviateConfig weaviateConfig) {
        this.restTemplate = new RestTemplate();
        this.weaviateConfig = weaviateConfig;
    }

    public void upsertChunk(KnowledgeBase knowledgeBase,
                            KnowledgeDocument document,
                            KnowledgeDocumentChunk chunk,
                            double[] embedding) {
        String className = buildClassName(knowledgeBase);
        ensureClassExists(className);

        HttpHeaders headers = createHeaders();
        Map<String, Object> body = new HashMap<>();
        body.put("class", className);
        body.put("id", chunk.getId());

        Map<String, Object> properties = new HashMap<>();
        properties.put("documentId", document.getId());
        properties.put("knowledgeBaseId", document.getKnowledgeBaseId());
        properties.put("chunkIndex", chunk.getChunkIndex());
        properties.put("content", chunk.getContent());
        body.put("properties", properties);

        body.put("vector", embedding);

        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(body, headers);
        String url = weaviateConfig.getEndpoint() + "/v1/objects";

        ResponseEntity<WeaviateObjectResponse> response = restTemplate.exchange(url, HttpMethod.POST, entity, WeaviateObjectResponse.class);
        if (!response.getStatusCode().is2xxSuccessful()) {
            throw new IllegalStateException("Weaviate upsert 失败: " + response.getStatusCode());
        }
    }

    public void ensureClassExists(KnowledgeBase knowledgeBase) {
        ensureClassExists(buildClassName(knowledgeBase));
    }

    public void deleteChunk(KnowledgeBase knowledgeBase, KnowledgeDocumentChunk chunk) {
        if (chunk == null || chunk.getVectorId() == null) {
            return;
        }
        ensureClassExists(knowledgeBase);
        String url = weaviateConfig.getEndpoint() + "/v1/objects/" + chunk.getVectorId();
        try {
            restTemplate.exchange(url, HttpMethod.DELETE, new HttpEntity<>(null, createHeaders()), Void.class);
        } catch (HttpClientErrorException.NotFound ignored) {
            // already gone
        }
    }

    private void ensureClassExists(String className) {
        if (classCache.getOrDefault(className, false)) {
            return;
        }

        String schemaUrl = weaviateConfig.getEndpoint() + "/v1/schema/" + className;
        try {
            restTemplate.exchange(schemaUrl, HttpMethod.GET, new HttpEntity<>(null, createHeaders()), Map.class);
            classCache.put(className, true);
            return;
        } catch (HttpClientErrorException.NotFound ignored) {
            // continue to create
        }

        HttpHeaders headers = createHeaders();
        Map<String, Object> classBody = new HashMap<>();
        classBody.put("class", className);
        classBody.put("vectorizer", "none");

        Map<String, Object> propertiesContent = new HashMap<>();
        propertiesContent.put("name", "content");
        propertiesContent.put("dataType", new String[]{"text"});

        Map<String, Object> propertiesDocId = new HashMap<>();
        propertiesDocId.put("name", "documentId");
        propertiesDocId.put("dataType", new String[]{"text"});

        Map<String, Object> propertiesKbId = new HashMap<>();
        propertiesKbId.put("name", "knowledgeBaseId");
        propertiesKbId.put("dataType", new String[]{"text"});

        Map<String, Object> propertiesChunkIndex = new HashMap<>();
        propertiesChunkIndex.put("name", "chunkIndex");
        propertiesChunkIndex.put("dataType", new String[]{"int"});

        classBody.put("properties", new Object[]{propertiesContent, propertiesDocId, propertiesKbId, propertiesChunkIndex});

        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(classBody, headers);
        restTemplate.exchange(weaviateConfig.getEndpoint() + "/v1/schema", HttpMethod.POST, entity, Map.class);
        classCache.put(className, true);
    }

    private String buildClassName(KnowledgeBase knowledgeBase) {
        String prefix = weaviateConfig.getClassPrefix();
        String source = knowledgeBase != null && knowledgeBase.getId() != null
            ? knowledgeBase.getId()
            : knowledgeBase != null && knowledgeBase.getName() != null ? knowledgeBase.getName() : "default";
        String sanitized = source.replaceAll("[^A-Za-z0-9]", "_");
        if (!StringUtils.hasText(sanitized)) {
            sanitized = "KB";
        }
        if (sanitized.length() > 48) {
            sanitized = sanitized.substring(0, 48);
        }
        String className = prefix + "_" + sanitized;
        if (Character.isDigit(className.charAt(0))) {
            className = "K" + className;
        }
        return className;
    }

    private HttpHeaders createHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        if (StringUtils.hasText(weaviateConfig.getApiKey())) {
            headers.set("Authorization", "Bearer " + weaviateConfig.getApiKey());
        }
        return headers;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    private static class WeaviateObjectResponse {
        @JsonProperty("id")
        private String id;
    }
}

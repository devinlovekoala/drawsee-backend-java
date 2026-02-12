package cn.yifan.drawsee.service.business;

import cn.yifan.drawsee.config.QdrantProperties;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Qdrant 向量库服务
 */
@Service
@Slf4j
public class QdrantService {

    private final RestTemplate restTemplate = new RestTemplate();
    private final QdrantProperties properties;

    @Autowired
    public QdrantService(QdrantProperties properties) {
        this.properties = properties;
    }

    public void ensureCollection(int vectorSize) {
        if (!hasConfig()) {
            throw new IllegalStateException("Qdrant 未配置");
        }
        String url = baseUrl() + "/collections/" + properties.getCollection();
        try {
            restTemplate.getForObject(url, Map.class);
            return;
        } catch (HttpClientErrorException.NotFound notFound) {
            log.info("Qdrant collection 不存在，开始创建: {}", properties.getCollection());
        } catch (Exception ex) {
            throw new IllegalStateException("检查Qdrant collection失败", ex);
        }

        Map<String, Object> vectors = new HashMap<>();
        vectors.put("size", vectorSize);
        vectors.put("distance", "Cosine");

        Map<String, Object> body = new HashMap<>();
        body.put("vectors", vectors);

        post(url, body);
        log.info("Qdrant collection 创建完成: {}", properties.getCollection());
    }

    public void upsertPoints(List<QdrantPoint> points) {
        if (points == null || points.isEmpty()) {
            return;
        }
        if (!hasConfig()) {
            throw new IllegalStateException("Qdrant 未配置");
        }
        String url = baseUrl() + "/collections/" + properties.getCollection() + "/points?wait=true";
        Map<String, Object> batch = new HashMap<>();
        List<String> ids = new java.util.ArrayList<>(points.size());
        List<List<Double>> vectors = new java.util.ArrayList<>(points.size());
        List<Map<String, Object>> payloads = new java.util.ArrayList<>(points.size());

        for (QdrantPoint point : points) {
            ids.add(point.getId());
            vectors.add(point.getVector());
            payloads.add(point.getPayload());
        }

        batch.put("ids", ids);
        batch.put("vectors", vectors);
        batch.put("payloads", payloads);

        Map<String, Object> body = new HashMap<>();
        body.put("batch", batch);

        try {
            post(url, body);
            return;
        } catch (IllegalStateException ex) {
            String msg = ex.getMessage() != null ? ex.getMessage() : "";
            log.warn("Qdrant batch wrapper upsert失败，准备降级: {}", msg);
        }

        Map<String, Object> topLevelBatch = new HashMap<>();
        topLevelBatch.put("ids", ids);
        topLevelBatch.put("vectors", vectors);
        topLevelBatch.put("payloads", payloads);
        try {
            post(url, topLevelBatch);
            return;
        } catch (IllegalStateException ex) {
            String msg = ex.getMessage() != null ? ex.getMessage() : "";
            log.warn("Qdrant top-level batch upsert失败，准备降级: {}", msg);
        }

        Map<String, Object> pointsBody = new HashMap<>();
        pointsBody.put("points", points);
        post(url, pointsBody);
    }

    private void post(String url, Object body) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        if (properties.getApiKey() != null && !properties.getApiKey().isBlank()) {
            headers.set("api-key", properties.getApiKey());
        }
        HttpEntity<Object> entity = new HttpEntity<>(body, headers);
        try {
            restTemplate.postForObject(url, entity, Map.class);
        } catch (HttpClientErrorException ex) {
            String responseBody = ex.getResponseBodyAsString();
            throw new IllegalStateException("Qdrant 请求失败: " + responseBody, ex);
        } catch (Exception ex) {
            throw new IllegalStateException("Qdrant 请求失败", ex);
        }
    }

    private boolean hasConfig() {
        return properties != null
            && properties.getHost() != null
            && properties.getPort() != null
            && properties.getCollection() != null
            && !properties.getCollection().isBlank();
    }

    private String baseUrl() {
        String scheme = Boolean.TRUE.equals(properties.getHttps()) ? "https" : "http";
        return scheme + "://" + properties.getHost() + ":" + properties.getPort();
    }

    @Data
    public static class QdrantPoint {
        private String id;
        private List<Double> vector;
        private Map<String, Object> payload;
    }
}

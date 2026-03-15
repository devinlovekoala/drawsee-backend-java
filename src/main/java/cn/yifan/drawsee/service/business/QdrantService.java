package cn.yifan.drawsee.service.business;

import cn.yifan.drawsee.config.QdrantProperties;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

/** Qdrant 向量库服务 */
@Service
@Slf4j
public class QdrantService {

  private final RestTemplate restTemplate = new RestTemplate();
  private final QdrantProperties properties;
  private volatile String vectorNameCache;

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

  public Map<String, Object> getCollectionInfo() {
    if (!hasConfig()) {
      throw new IllegalStateException("Qdrant 未配置");
    }
    String url = baseUrl() + "/collections/" + properties.getCollection();
    try {
      return restTemplate.getForObject(url, Map.class);
    } catch (Exception ex) {
      throw new IllegalStateException("获取Qdrant collection信息失败", ex);
    }
  }

  public Map<String, Object> getEffectiveConfig() {
    Map<String, Object> config = new HashMap<>();
    config.put("host", properties.getHost());
    config.put("port", properties.getPort());
    config.put("https", properties.getHttps());
    config.put("collection", properties.getCollection());
    config.put("baseUrl", baseUrl());
    return config;
  }

  public Map<String, Object> countPoints(String knowledgeBaseId, String documentId) {
    if (!hasConfig()) {
      throw new IllegalStateException("Qdrant 未配置");
    }
    String url =
        baseUrl() + "/collections/" + properties.getCollection() + "/points/count?exact=true";
    Map<String, Object> body = new HashMap<>();
    Map<String, Object> filter = buildFilter(knowledgeBaseId, documentId);
    if (filter != null) {
      body.put("filter", filter);
    }
    return postForMap(url, body);
  }

  public Map<String, Object> getPointsByIds(List<String> ids) {
    if (!hasConfig()) {
      throw new IllegalStateException("Qdrant 未配置");
    }
    String url = baseUrl() + "/collections/" + properties.getCollection() + "/points";
    Map<String, Object> body = new HashMap<>();
    body.put("ids", ids);
    return postForMap(url, body);
  }

  public void upsertPoints(List<QdrantPoint> points) {
    if (points == null || points.isEmpty()) {
      return;
    }
    if (!hasConfig()) {
      throw new IllegalStateException("Qdrant 未配置");
    }
    log.info(
        "Qdrant upsert: baseUrl={}, collection={}, points={}",
        baseUrl(),
        properties.getCollection(),
        points.size());
    String url = baseUrl() + "/collections/" + properties.getCollection() + "/points?wait=true";
    String vectorName = resolveVectorName();
    List<String> ids = new java.util.ArrayList<>(points.size());
    List<List<Double>> vectors = new java.util.ArrayList<>(points.size());
    List<Map<String, Object>> payloads = new java.util.ArrayList<>(points.size());
    List<Map<String, Object>> pointMaps = new java.util.ArrayList<>(points.size());

    for (QdrantPoint point : points) {
      ids.add(point.getId());
      vectors.add(point.getVector());
      payloads.add(point.getPayload());
      Map<String, Object> map = new HashMap<>();
      map.put("id", point.getId());
      if (vectorName == null) {
        map.put("vector", point.getVector());
      } else {
        Map<String, Object> namedVector = new HashMap<>();
        namedVector.put(vectorName, point.getVector());
        map.put("vector", namedVector);
      }
      map.put("payload", point.getPayload());
      pointMaps.add(map);
    }

    Map<String, Object> pointsBody = new HashMap<>();
    pointsBody.put("points", pointMaps);

    try {
      put(url, pointsBody);
      return;
    } catch (IllegalStateException ex) {
      String msg = ex.getMessage() != null ? ex.getMessage() : "";
      log.warn("Qdrant points upsert失败，准备降级: {}", msg);
    }

    if (vectorName != null) {
      throw new IllegalStateException("Qdrant points upsert失败，且collection使用命名向量，已停止降级");
    }

    Map<String, Object> batch = new HashMap<>();
    batch.put("ids", ids);
    batch.put("vectors", vectors);
    batch.put("payloads", payloads);

    Map<String, Object> body = new HashMap<>();
    body.put("batch", batch);

    try {
      put(url, body);
      return;
    } catch (IllegalStateException ex) {
      String msg = ex.getMessage() != null ? ex.getMessage() : "";
      log.warn("Qdrant batch wrapper upsert失败，准备降级: {}", msg);
    }

    Map<String, Object> topLevelBatch = new HashMap<>();
    topLevelBatch.put("ids", ids);
    topLevelBatch.put("vectors", vectors);
    topLevelBatch.put("payloads", payloads);
    put(url, topLevelBatch);
  }

  private void post(String url, Object body) {
    HttpHeaders headers = new HttpHeaders();
    headers.setContentType(MediaType.APPLICATION_JSON);
    if (properties.getApiKey() != null && !properties.getApiKey().isBlank()) {
      headers.set("api-key", properties.getApiKey());
    }
    HttpEntity<Object> entity = new HttpEntity<>(body, headers);
    try {
      Map response = restTemplate.postForObject(url, entity, Map.class);
      validateQdrantResponse(response);
    } catch (HttpClientErrorException ex) {
      String responseBody = ex.getResponseBodyAsString();
      throw new IllegalStateException("Qdrant 请求失败: " + responseBody, ex);
    } catch (Exception ex) {
      throw new IllegalStateException("Qdrant 请求失败", ex);
    }
  }

  private void put(String url, Object body) {
    HttpHeaders headers = new HttpHeaders();
    headers.setContentType(MediaType.APPLICATION_JSON);
    if (properties.getApiKey() != null && !properties.getApiKey().isBlank()) {
      headers.set("api-key", properties.getApiKey());
    }
    HttpEntity<Object> entity = new HttpEntity<>(body, headers);
    try {
      Map response =
          restTemplate
              .exchange(url, org.springframework.http.HttpMethod.PUT, entity, Map.class)
              .getBody();
      validateQdrantResponse(response);
    } catch (HttpClientErrorException ex) {
      String responseBody = ex.getResponseBodyAsString();
      throw new IllegalStateException("Qdrant 请求失败: " + responseBody, ex);
    } catch (Exception ex) {
      throw new IllegalStateException("Qdrant 请求失败", ex);
    }
  }

  private Map<String, Object> postForMap(String url, Object body) {
    HttpHeaders headers = new HttpHeaders();
    headers.setContentType(MediaType.APPLICATION_JSON);
    if (properties.getApiKey() != null && !properties.getApiKey().isBlank()) {
      headers.set("api-key", properties.getApiKey());
    }
    HttpEntity<Object> entity = new HttpEntity<>(body, headers);
    try {
      Map<String, Object> response = restTemplate.postForObject(url, entity, Map.class);
      validateQdrantResponse(response);
      return response;
    } catch (HttpClientErrorException ex) {
      String responseBody = ex.getResponseBodyAsString();
      throw new IllegalStateException("Qdrant 请求失败: " + responseBody, ex);
    } catch (Exception ex) {
      throw new IllegalStateException("Qdrant 请求失败", ex);
    }
  }

  private void validateQdrantResponse(Map<String, Object> response) {
    if (response == null) {
      throw new IllegalStateException("Qdrant 响应为空");
    }
    Object status = response.get("status");
    if (status instanceof String) {
      if (!"ok".equalsIgnoreCase((String) status)) {
        throw new IllegalStateException("Qdrant 响应异常: " + response);
      }
      return;
    }
    if (status instanceof Map) {
      Object error = ((Map<?, ?>) status).get("error");
      if (error != null) {
        throw new IllegalStateException("Qdrant 响应异常: " + error);
      }
    }
  }

  private Map<String, Object> buildFilter(String knowledgeBaseId, String documentId) {
    if ((knowledgeBaseId == null || knowledgeBaseId.isBlank())
        && (documentId == null || documentId.isBlank())) {
      return null;
    }
    Map<String, Object> filter = new HashMap<>();
    java.util.List<Map<String, Object>> must = new java.util.ArrayList<>();
    if (knowledgeBaseId != null && !knowledgeBaseId.isBlank()) {
      Map<String, Object> match = new HashMap<>();
      match.put("value", knowledgeBaseId);
      Map<String, Object> cond = new HashMap<>();
      cond.put("key", "knowledgeBaseId");
      cond.put("match", match);
      must.add(cond);
    }
    if (documentId != null && !documentId.isBlank()) {
      Map<String, Object> match = new HashMap<>();
      match.put("value", documentId);
      Map<String, Object> cond = new HashMap<>();
      cond.put("key", "documentId");
      cond.put("match", match);
      must.add(cond);
    }
    filter.put("must", must);
    return filter;
  }

  @SuppressWarnings("unchecked")
  private String resolveVectorName() {
    if (vectorNameCache != null) {
      return vectorNameCache;
    }
    try {
      Map<String, Object> info = getCollectionInfo();
      Object result = info != null ? info.get("result") : null;
      if (result instanceof Map) {
        Object config = ((Map<String, Object>) result).get("config");
        if (config instanceof Map) {
          Object params = ((Map<String, Object>) config).get("params");
          if (params instanceof Map) {
            Object vectors = ((Map<String, Object>) params).get("vectors");
            if (vectors instanceof Map) {
              Map<String, Object> vectorsMap = (Map<String, Object>) vectors;
              if (vectorsMap.containsKey("size") || vectorsMap.containsKey("distance")) {
                vectorNameCache = null;
                return null;
              }
              if (!vectorsMap.isEmpty()) {
                String name = vectorsMap.keySet().iterator().next();
                vectorNameCache = name;
                log.info("检测到Qdrant命名向量: {}", name);
                return name;
              }
            }
          }
        }
      }
    } catch (Exception ex) {
      log.warn("解析Qdrant向量配置失败，使用默认向量: {}", ex.getMessage());
    }
    vectorNameCache = null;
    return null;
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

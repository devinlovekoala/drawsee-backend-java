package cn.yifan.drawsee.service.base;

import cn.yifan.drawsee.pojo.dto.agentic.AgenticQueryRequest;
import cn.yifan.drawsee.util.InternalJwtUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Agentic RAG v2 服务
 * 对接Python Agentic RAG系统，提供SSE流式查询
 *
 * @author Drawsee Team
 */
@Service
@Slf4j
@ConditionalOnProperty(prefix = "drawsee.python-service", name = "enabled", havingValue = "true", matchIfMissing = true)
public class AgenticRagService {

    @Autowired
    private RestTemplate restTemplate;

    @Autowired
    private InternalJwtUtil internalJwtUtil;

    @Value("${drawsee.python-service.base-url}")
    private String pythonServiceBaseUrl;

    @Value("${drawsee.python-service.timeout:60000}")
    private Integer timeout;

    private final ExecutorService executorService = Executors.newCachedThreadPool();

    /**
     * 创建带认证的HTTP头
     */
    private HttpHeaders createAuthHeaders(Long userId, String classId, String knowledgeBaseId) {
        String jwt = internalJwtUtil.generateToken(userId, classId, knowledgeBaseId);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("Authorization", "Bearer " + jwt);
        headers.set("X-Service-Name", "drawsee-java");
        return headers;
    }

    /**
     * Agentic RAG 查询（SSE流式）
     *
     * @param query            用户查询
     * @param knowledgeBaseIds 知识库ID列表
     * @param hasImage         是否包含图片
     * @param imageUrl         图片URL
     * @param context          上下文信息
     * @param userId           用户ID
     * @param classId          班级ID
     * @return SseEmitter 用于流式传输
     */
    public SseEmitter agenticQueryStream(
            String query,
            List<String> knowledgeBaseIds,
            Boolean hasImage,
            String imageUrl,
            Map<String, Object> context,
            Long userId,
            String classId
    ) {
        SseEmitter emitter = new SseEmitter(60000L); // 60秒超时

        executorService.execute(() -> {
            try {
                String url = pythonServiceBaseUrl + "/api/v1/rag/agentic/query";

                // 构建请求体
                AgenticQueryRequest request = AgenticQueryRequest.builder()
                        .query(query)
                        .knowledgeBaseIds(knowledgeBaseIds)
                        .hasImage(hasImage != null ? hasImage : false)
                        .imageUrl(imageUrl)
                        .context(context)
                        .options(new HashMap<>())
                        .build();

                // 创建HTTP连接（用于SSE）
                HttpURLConnection connection = (HttpURLConnection) new URL(url).openConnection();
                connection.setRequestMethod("POST");
                connection.setDoOutput(true);
                connection.setRequestProperty("Content-Type", "application/json");
                connection.setRequestProperty("Accept", "text/event-stream");

                // 添加认证头
                String jwt = internalJwtUtil.generateToken(
                        userId,
                        classId,
                        knowledgeBaseIds != null && !knowledgeBaseIds.isEmpty() ? knowledgeBaseIds.get(0) : null
                );
                connection.setRequestProperty("Authorization", "Bearer " + jwt);

                // 发送请求体
                String jsonRequest = objectToJson(request);
                connection.getOutputStream().write(jsonRequest.getBytes(StandardCharsets.UTF_8));
                connection.getOutputStream().flush();

                // 读取SSE流
                BufferedReader reader = new BufferedReader(
                        new InputStreamReader(connection.getInputStream(), StandardCharsets.UTF_8)
                );

                String line;
                StringBuilder eventData = new StringBuilder();
                String eventType = null;

                log.info("[AgenticRAG] 开始接收SSE流: query='{}'", query.substring(0, Math.min(50, query.length())));

                while ((line = reader.readLine()) != null) {
                    if (line.startsWith("event:")) {
                        eventType = line.substring(7).trim();
                    } else if (line.startsWith("data:")) {
                        eventData.append(line.substring(6).trim());
                    } else if (line.isEmpty() && eventType != null) {
                        // 事件完成，发送给前端
                        String data = eventData.toString();
                        emitter.send(SseEmitter.event()
                                .name(eventType)
                                .data(data));

                        log.debug("[AgenticRAG] SSE事件: type={}, data_length={}", eventType, data.length());

                        // 如果是done事件，结束流
                        if ("done".equals(eventType)) {
                            log.info("[AgenticRAG] 查询完成");
                            emitter.complete();
                            break;
                        }

                        // 如果是error事件，也结束流
                        if ("error".equals(eventType)) {
                            log.error("[AgenticRAG] 查询出错: {}", data);
                            emitter.complete();
                            break;
                        }

                        // 重置
                        eventData.setLength(0);
                        eventType = null;
                    }
                }

                reader.close();
                connection.disconnect();

            } catch (Exception e) {
                log.error("[AgenticRAG] SSE流处理失败: {}", e.getMessage(), e);
                try {
                    emitter.send(SseEmitter.event()
                            .name("error")
                            .data("{\"error\":\"查询失败\",\"detail\":\"" + e.getMessage() + "\"}"));
                    emitter.complete();
                } catch (Exception ex) {
                    emitter.completeWithError(ex);
                }
            }
        });

        return emitter;
    }

    /**
     * 获取Agentic RAG频道状态
     */
    public Map<String, Object> getChannelsStatus(Long userId, String classId) {
        String url = pythonServiceBaseUrl + "/api/v1/rag/agentic/stats";

        HttpEntity<?> entity = new HttpEntity<>(
                createAuthHeaders(userId, classId, null)
        );

        try {
            log.info("[AgenticRAG] 获取频道状态");
            ResponseEntity<Map<String, Object>> response = restTemplate.exchange(
                    url,
                    HttpMethod.GET,
                    entity,
                    new org.springframework.core.ParameterizedTypeReference<Map<String, Object>>() {}
            );

            if (response.getStatusCode() == HttpStatus.OK && response.getBody() != null) {
                Map<String, Object> result = response.getBody();
                if (Boolean.TRUE.equals(result.get("success"))) {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> data = (Map<String, Object>) result.get("data");
                    return data;
                } else {
                    log.error("[AgenticRAG] 获取频道状态失败: {}", result.get("error"));
                    return null;
                }
            }

            return null;

        } catch (Exception e) {
            log.error("[AgenticRAG] 获取频道状态异常: {}", e.getMessage(), e);
            return null;
        }
    }

    /**
     * 健康检查
     */
    public boolean healthCheck() {
        try {
            String url = pythonServiceBaseUrl + "/api/v1/rag/agentic/health";
            ResponseEntity<Map<String, Object>> response = restTemplate.exchange(
                    url,
                    HttpMethod.GET,
                    null,
                    new org.springframework.core.ParameterizedTypeReference<Map<String, Object>>() {}
            );
            return response.getStatusCode() == HttpStatus.OK;
        } catch (Exception e) {
            log.error("[AgenticRAG] 健康检查失败: {}", e.getMessage());
            return false;
        }
    }

    /**
     * 简单的对象转JSON（避免引入Jackson依赖）
     */
    private String objectToJson(AgenticQueryRequest request) {
        StringBuilder json = new StringBuilder();
        json.append("{");
        json.append("\"query\":\"").append(escapeJson(request.getQuery())).append("\",");
        json.append("\"knowledge_base_ids\":[");
        if (request.getKnowledgeBaseIds() != null) {
            for (int i = 0; i < request.getKnowledgeBaseIds().size(); i++) {
                if (i > 0) json.append(",");
                json.append("\"").append(request.getKnowledgeBaseIds().get(i)).append("\"");
            }
        }
        json.append("],");
        json.append("\"has_image\":").append(request.getHasImage() != null ? request.getHasImage() : false).append(",");
        if (request.getImageUrl() != null) {
            json.append("\"image_url\":\"").append(escapeJson(request.getImageUrl())).append("\",");
        }
        json.append("\"context\":").append(request.getContext() != null ? "{}" : "{}").append(",");
        json.append("\"options\":").append(request.getOptions() != null ? "{}" : "{}");
        json.append("}");
        return json.toString();
    }

    /**
     * 转义JSON字符串
     */
    private String escapeJson(String str) {
        if (str == null) return "";
        return str.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }
}

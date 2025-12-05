package cn.yifan.drawsee.service.base;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.*;

/**
 * @FileName WebSearchService
 * @Description Web搜索服务，用于联网查询电子元件资料、引脚图、数据手册等信息
 * @Author yifan
 * @date 2025-12-05
 **/

@Service
@Slf4j
public class WebSearchService {

    @Autowired
    private RestTemplate restTemplate;

    @Value("${drawsee.search.api-key:}")
    private String searchApiKey;

    @Value("${drawsee.search.engine-id:}")
    private String searchEngineId;

    @Value("${drawsee.search.enabled:false}")
    private boolean searchEnabled;

    /**
     * 搜索电子元件相关资料
     * @param componentName 元件名称
     * @param searchType 搜索类型：datasheet(数据手册)、pinout(引脚图)、tutorial(教程)、general(通用)
     * @return 搜索结果摘要
     */
    public String searchComponentInfo(String componentName, String searchType) {
        if (!searchEnabled || searchApiKey == null || searchApiKey.isBlank()) {
            log.warn("Web搜索功能未启用或未配置API密钥");
            return null;
        }

        try {
            String query = buildSearchQuery(componentName, searchType);
            List<SearchResult> results = performSearch(query);

            if (results == null || results.isEmpty()) {
                log.info("未找到元件{}的{}信息", componentName, searchType);
                return null;
            }

            return formatSearchResults(componentName, searchType, results);
        } catch (Exception e) {
            log.error("搜索元件信息失败: component={}, type={}", componentName, searchType, e);
            return null;
        }
    }

    /**
     * 批量搜索多个元件信息
     * @param componentNames 元件名称列表
     * @return 元件名称到搜索结果的映射
     */
    public Map<String, String> batchSearchComponents(List<String> componentNames) {
        Map<String, String> results = new HashMap<>();

        for (String component : componentNames) {
            // 优先搜索数据手册和引脚图
            String datasheetInfo = searchComponentInfo(component, "datasheet");
            String pinoutInfo = searchComponentInfo(component, "pinout");

            StringBuilder summary = new StringBuilder();
            if (datasheetInfo != null) {
                summary.append(datasheetInfo).append("\n\n");
            }
            if (pinoutInfo != null) {
                summary.append(pinoutInfo);
            }

            if (summary.length() > 0) {
                results.put(component, summary.toString());
            }
        }

        return results;
    }

    /**
     * 构建搜索查询语句
     */
    private String buildSearchQuery(String componentName, String searchType) {
        switch (searchType) {
            case "datasheet":
                return componentName + " datasheet PDF 中文";
            case "pinout":
                return componentName + " 引脚图 pinout diagram";
            case "tutorial":
                return componentName + " 使用教程 实验";
            case "general":
            default:
                return componentName + " 电子元件 参数 应用";
        }
    }

    /**
     * 执行搜索请求（使用Google Custom Search API或其他搜索引擎API）
     */
    private List<SearchResult> performSearch(String query) {
        try {
            // 使用Google Custom Search API
            String url = String.format(
                "https://www.googleapis.com/customsearch/v1?key=%s&cx=%s&q=%s&num=5",
                searchApiKey, searchEngineId, query
            );

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<String> entity = new HttpEntity<>(headers);

            @SuppressWarnings("rawtypes")
            ResponseEntity<Map> response = restTemplate.exchange(
                url,
                HttpMethod.GET,
                entity,
                Map.class
            );

            if (response.getStatusCode() == HttpStatus.OK && response.getBody() != null) {
                return parseSearchResponse(response.getBody());
            }
        } catch (Exception e) {
            log.error("执行搜索请求失败: query={}", query, e);
        }

        return Collections.emptyList();
    }

    /**
     * 解析搜索API响应
     */
    @SuppressWarnings("unchecked")
    private List<SearchResult> parseSearchResponse(Map<String, Object> responseBody) {
        List<SearchResult> results = new ArrayList<>();

        try {
            List<Map<String, Object>> items = (List<Map<String, Object>>) responseBody.get("items");
            if (items == null) {
                return results;
            }

            for (Map<String, Object> item : items) {
                SearchResult result = new SearchResult();
                result.setTitle((String) item.get("title"));
                result.setLink((String) item.get("link"));
                result.setSnippet((String) item.get("snippet"));
                results.add(result);
            }
        } catch (Exception e) {
            log.error("解析搜索结果失败", e);
        }

        return results;
    }

    /**
     * 格式化搜索结果为可读文本
     */
    private String formatSearchResults(String componentName, String searchType, List<SearchResult> results) {
        StringBuilder formatted = new StringBuilder();

        String typeLabel = getTypeLabel(searchType);
        formatted.append(String.format("【%s - %s】\n", componentName, typeLabel));

        int count = Math.min(3, results.size()); // 最多取前3条结果
        for (int i = 0; i < count; i++) {
            SearchResult result = results.get(i);
            formatted.append(String.format("%d. %s\n", i + 1, result.getTitle()));
            if (result.getSnippet() != null && !result.getSnippet().isBlank()) {
                formatted.append(String.format("   %s\n", result.getSnippet()));
            }
            formatted.append(String.format("   链接: %s\n", result.getLink()));
            if (i < count - 1) {
                formatted.append("\n");
            }
        }

        return formatted.toString();
    }

    private String getTypeLabel(String searchType) {
        switch (searchType) {
            case "datasheet": return "数据手册";
            case "pinout": return "引脚图";
            case "tutorial": return "使用教程";
            case "general":
            default: return "基本信息";
        }
    }

    /**
     * 搜索结果内部类
     */
    private static class SearchResult {
        private String title;
        private String link;
        private String snippet;

        public String getTitle() { return title; }
        public void setTitle(String title) { this.title = title; }
        public String getLink() { return link; }
        public void setLink(String link) { this.link = link; }
        public String getSnippet() { return snippet; }
        public void setSnippet(String snippet) { this.snippet = snippet; }
    }
}

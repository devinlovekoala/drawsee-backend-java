package cn.yifan.drawsee.service.base;

import java.util.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

/**
 * @FileName WebSearchService @Description Web搜索服务，使用百度AI搜索API查询电子元件资料、引脚图、数据手册等信息 @Author devin
 *
 * @date 2025-12-05
 */
@Service
@Slf4j
public class WebSearchService {

  @Autowired private RestTemplate restTemplate;

  private String searchApiKey;

  private boolean searchEnabled;

  // 百度AI搜索API V1版本端点
  private static final String BAIDU_SEARCH_API_URL =
      "https://appbuilder.baidu.com/rpc/2.0/cloud_hub/v1/ai_engine/copilot_engine/service/v1/baidu_search_rag/general";

  /**
   * 搜索电子元件相关资料
   *
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
      String result = performBaiduSearch(query);

      if (result == null || result.isBlank()) {
        log.info("未找到元件{}的{}信息", componentName, searchType);
        return null;
      }

      return formatSearchResult(componentName, searchType, result);
    } catch (Exception e) {
      log.error("搜索元件信息失败: component={}, type={}", componentName, searchType, e);
      return null;
    }
  }

  /**
   * 批量搜索多个元件信息
   *
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

      // 添加延迟避免频繁调用API
      try {
        Thread.sleep(500);
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
      }
    }

    return results;
  }

  /** 构建搜索查询语句 */
  private String buildSearchQuery(String componentName, String searchType) {
    switch (searchType) {
      case "datasheet":
        return componentName + " 数据手册 datasheet PDF 参数";
      case "pinout":
        return componentName + " 引脚图 引脚定义 pinout";
      case "tutorial":
        return componentName + " 使用教程 应用电路 实验";
      case "general":
      default:
        return componentName + " 电子元件 芯片参数 功能介绍";
    }
  }

  /**
   * 执行百度AI搜索请求
   *
   * @param query 搜索查询
   * @return 搜索结果文本
   */
  private String performBaiduSearch(String query) {
    try {
      // 构建请求体
      Map<String, Object> requestBody = new HashMap<>();

      // 构建消息列表
      List<Map<String, String>> messages = new ArrayList<>();
      Map<String, String> userMessage = new HashMap<>();
      userMessage.put("role", "user");
      userMessage.put("content", query);
      messages.add(userMessage);

      requestBody.put("message", messages);
      requestBody.put("instruction", "你是一位专业的电子工程助手，请简洁准确地回答用户关于电子元器件的问题，重点提供参数、引脚定义、应用场景等实用信息。");
      requestBody.put("stream", false);
      requestBody.put("model", "ERNIE-4.0-8K");

      // 设置请求头
      HttpHeaders headers = new HttpHeaders();
      headers.setContentType(MediaType.APPLICATION_JSON);
      headers.set("X-Appbuilder-Authorization", "Bearer " + searchApiKey);

      HttpEntity<Map<String, Object>> entity = new HttpEntity<>(requestBody, headers);

      // 发送请求
        ResponseEntity<Map<String, Object>> response =
            restTemplate.exchange(
              BAIDU_SEARCH_API_URL,
              HttpMethod.POST,
              entity,
              new org.springframework.core.ParameterizedTypeReference<Map<String, Object>>() {});

      if (response.getStatusCode() == HttpStatus.OK && response.getBody() != null) {
        return parseBaiduSearchResponse(response.getBody());
      }
    } catch (Exception e) {
      log.error("执行百度搜索请求失败: query={}", query, e);
    }

    return null;
  }

  /** 解析百度AI搜索API响应 */
  @SuppressWarnings("unchecked")
  private String parseBaiduSearchResponse(Map<String, Object> responseBody) {
    try {
      // 百度AI搜索API返回结构: {"result": {"answer": "..."}, ...}
      Map<String, Object> result = (Map<String, Object>) responseBody.get("result");
      if (result != null) {
        String answer = (String) result.get("answer");
        if (answer != null && !answer.isBlank()) {
          // 限制答案长度
          if (answer.length() > 500) {
            answer = answer.substring(0, 500) + "...";
          }
          return answer;
        }
      }
    } catch (Exception e) {
      log.error("解析百度搜索结果失败", e);
    }

    return null;
  }

  /** 格式化搜索结果为可读文本 */
  private String formatSearchResult(String componentName, String searchType, String searchResult) {
    String typeLabel = getTypeLabel(searchType);

    StringBuilder formatted = new StringBuilder();
    formatted.append(String.format("【%s - %s】\n", componentName, typeLabel));
    formatted.append(searchResult);

    return formatted.toString();
  }

  private String getTypeLabel(String searchType) {
    switch (searchType) {
      case "datasheet":
        return "数据手册";
      case "pinout":
        return "引脚图";
      case "tutorial":
        return "使用教程";
      case "general":
      default:
        return "基本信息";
    }
  }
}

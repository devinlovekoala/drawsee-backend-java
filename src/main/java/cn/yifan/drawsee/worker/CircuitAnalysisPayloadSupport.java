package cn.yifan.drawsee.worker;

import cn.yifan.drawsee.pojo.entity.CircuitDesign;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

final class CircuitAnalysisPayloadSupport {

  private CircuitAnalysisPayloadSupport() {}

  record ParsedPayload(
      CircuitDesign circuitDesign,
      Map<String, Object> analysisContext,
      String workspaceMode,
      String version) {}

  static ParsedPayload parsePrompt(String prompt, ObjectMapper objectMapper)
      throws JsonProcessingException {
    JsonNode root = objectMapper.readTree(prompt);
    if (root != null && root.isObject() && root.has("circuitDesign")) {
      CircuitDesign circuitDesign =
          objectMapper.treeToValue(root.get("circuitDesign"), CircuitDesign.class);
      Map<String, Object> analysisContext =
          root.has("analysisContext") && !root.get("analysisContext").isNull()
              ? objectMapper.convertValue(
                  root.get("analysisContext"), new TypeReference<Map<String, Object>>() {})
              : Collections.emptyMap();
      return new ParsedPayload(
          circuitDesign,
          analysisContext,
          root.path("workspaceMode").asText("analog"),
          root.path("version").asText("legacy"));
    }
    return new ParsedPayload(
        objectMapper.readValue(prompt, CircuitDesign.class),
        Collections.emptyMap(),
        "analog",
        "legacy");
  }

  static CircuitDesign extractCircuitDesign(Map<String, Object> nodeData, ObjectMapper objectMapper)
      throws JsonProcessingException {
    Object circuitDesignObj = nodeData.get("circuitDesign");
    if (circuitDesignObj == null) {
      return null;
    }
    return objectMapper.readValue(objectMapper.writeValueAsString(circuitDesignObj), CircuitDesign.class);
  }

  static Map<String, Object> extractAnalysisContext(
      Map<String, Object> nodeData, ObjectMapper objectMapper) throws JsonProcessingException {
    Object analysisContextObj = nodeData.get("analysisContext");
    if (analysisContextObj == null) {
      return Collections.emptyMap();
    }
    return objectMapper.readValue(
        objectMapper.writeValueAsString(analysisContextObj), new TypeReference<Map<String, Object>>() {});
  }

  static String buildAnalysisContextSummary(Map<String, Object> analysisContext) {
    if (analysisContext == null || analysisContext.isEmpty()) {
      return "";
    }
    StringBuilder summary = new StringBuilder();

    Map<String, Object> analog = asMap(analysisContext.get("analog"));
    if (!analog.isEmpty()) {
      summary.append("**模拟仿真上下文**:\n");
      appendIfPresent(summary, "- 实时引擎: ", analog.get("simulator"));
      appendIfPresent(summary, "- 分析模式: ", analog.get("mode"));

      Map<String, Object> diagnostics = asMap(analog.get("diagnostics"));
      if (!diagnostics.isEmpty()) {
        appendIfPresent(summary, "- 拓扑诊断: ", diagnostics.get("title"));
        appendIfPresent(summary, "  - 诊断摘要: ", diagnostics.get("summary"));
        List<String> suggestions = asStringList(diagnostics.get("suggestions"));
        for (String suggestion : suggestions) {
          summary.append("  - 建议: ").append(suggestion).append('\n');
        }
      }

      Map<String, Object> netlist = asMap(analog.get("netlist"));
      List<Map<String, Object>> probes = asMapList(netlist.get("probes"));
      if (!probes.isEmpty()) {
        summary.append("- 示波器/探针定义:\n");
        for (Map<String, Object> probe : probes) {
          String label = getString(probe, "label", getString(probe, "id", "未命名探针"));
          summary
              .append("  - ")
              .append(label)
              .append(": nets=")
              .append(asStringList(probe.get("nets")))
              .append('\n');
          List<Map<String, Object>> ports = asMapList(probe.get("ports"));
          for (Map<String, Object> port : ports) {
            summary
                .append("    - ")
                .append(getString(port, "portName", getString(port, "portId", "port")))
                .append(" -> ")
                .append(getString(port, "net", "0"))
                .append('\n');
          }
          appendIfPresent(summary, "    - 测量意图: ", probe.get("hint"));
        }
      }

      List<Map<String, Object>> measurementTargets = asMapList(analog.get("measurementTargets"));
      if (!measurementTargets.isEmpty()) {
        summary.append("- 仪表连接摘要:\n");
        for (Map<String, Object> measurement : measurementTargets) {
          summary
              .append("  - ")
              .append(getString(measurement, "label", getString(measurement, "elementId", "仪表")))
              .append(" [")
              .append(getString(measurement, "type", ""))
              .append("] nets=")
              .append(asStringList(measurement.get("nets")))
              .append('\n');
          appendIfPresent(summary, "    - 用途: ", measurement.get("hint"));
        }
      }

      Map<String, Object> latestRealtimeFrame = asMap(analog.get("latestRealtimeFrame"));
      if (!latestRealtimeFrame.isEmpty()) {
        appendIfPresent(summary, "- 最近实时仿真时间: ", latestRealtimeFrame.get("time"));
        appendIfPresent(summary, "- 最近实时仿真状态: ", latestRealtimeFrame.get("converged"));
        List<Map<String, Object>> scopePanels = asMapList(latestRealtimeFrame.get("scopePanels"));
        for (Map<String, Object> scopePanel : scopePanels) {
          summary
              .append("  - 最近波形 ")
              .append(getString(scopePanel, "label", getString(scopePanel, "elementId", "scope")))
              .append('\n');
          for (Map<String, Object> trace : asMapList(scopePanel.get("traces"))) {
            summary
                .append("    - ")
                .append(getString(trace, "label", getString(trace, "key", "trace")))
                .append(": latest=")
                .append(stringify(trace.get("latestValue")))
                .append(", points=")
                .append(stringify(trace.get("sampleCount")))
                .append('\n');
          }
        }
      }

      List<Map<String, Object>> precisionMeasurements =
          asMapList(analog.get("latestPrecisionMeasurements"));
      if (!precisionMeasurements.isEmpty()) {
        summary.append("- 最近测量结果摘要:\n");
        for (Map<String, Object> result : precisionMeasurements.stream().limit(8).toList()) {
          summary
              .append("  - ")
              .append(getString(result, "label", getString(result, "elementId", "测量元件")))
              .append(" [")
              .append(getString(result, "elementType", ""))
              .append("] metrics=")
              .append(stringify(result.get("metrics")))
              .append('\n');
        }
      }
      summary.append('\n');
    }

    Map<String, Object> digital = asMap(analysisContext.get("digital"));
    if (!digital.isEmpty()) {
      summary.append("**数字仿真上下文**:\n");
      appendIfPresent(summary, "- 数字仿真引擎: ", digital.get("simulator"));
      appendIfPresent(summary, "- 数字计划错误: ", digital.get("planError"));

      Map<String, Object> plan = asMap(digital.get("plan"));
      if (!plan.isEmpty()) {
        appendIfPresent(summary, "- 顶层模块: ", plan.get("topModule"));
        appendIfPresent(summary, "- 持续时间(ns): ", plan.get("durationNs"));
        summary
            .append("- I/O摘要: inputs=")
            .append(asMapList(plan.get("inputs")).size())
            .append(", outputs=")
            .append(asMapList(plan.get("outputs")).size())
            .append(", inouts=")
            .append(asMapList(plan.get("inouts")).size())
            .append('\n');
        List<Map<String, Object>> probes = asMapList(plan.get("probes"));
        for (Map<String, Object> probe : probes.stream().limit(12).toList()) {
          summary
              .append("  - 观测信号 ")
              .append(getString(probe, "label", getString(probe, "signal", "signal")))
              .append(": ")
              .append(getString(probe, "signal", ""))
              .append(" [")
              .append(getString(probe, "role", "probe"))
              .append("]\n");
        }
        for (String warning : asStringList(plan.get("warnings"))) {
          summary.append("  - 计划警告: ").append(warning).append('\n');
        }
      }

      Map<String, Object> latestResult = asMap(digital.get("latestResult"));
      if (!latestResult.isEmpty()) {
        appendIfPresent(summary, "- 最近数字仿真时长(ns): ", latestResult.get("durationNs"));
        for (String warning : asStringList(latestResult.get("warnings"))) {
          summary.append("  - 最近仿真警告: ").append(warning).append('\n');
        }
        for (Map<String, Object> waveform : asMapList(latestResult.get("waveforms")).stream()
            .limit(12)
            .toList()) {
          summary
              .append("  - 波形 ")
              .append(getString(waveform, "label", getString(waveform, "signal", "signal")))
              .append(": latest=")
              .append(getString(waveform, "latestValue", ""))
              .append(", samples=")
              .append(stringify(waveform.get("sampleCount")))
              .append('\n');
        }
      }
      summary.append('\n');
    }

    return summary.toString();
  }

  static String describeElements(CircuitDesign circuitDesign) {
    if (circuitDesign == null || circuitDesign.getElements() == null || circuitDesign.getElements().isEmpty()) {
      return "";
    }
    return circuitDesign.getElements().stream()
        .map(
            elem -> {
              String label = Optional.ofNullable(elem.getLabel()).filter(s -> !s.isBlank()).orElse(elem.getId());
              String value =
                  Optional.ofNullable(elem.getValue())
                      .filter(s -> !s.isBlank())
                      .orElseGet(
                          () ->
                              Optional.ofNullable(elem.getProperties())
                                  .map(props -> props.get("value"))
                                  .map(CircuitAnalysisPayloadSupport::stringify)
                                  .orElse(""));
              return "- "
                  + label
                  + " ["
                  + elem.getId()
                  + "] (类型: "
                  + elem.getType()
                  + (value.isBlank() ? "" : ", 值: " + value)
                  + ")";
            })
        .collect(Collectors.joining("\n"));
  }

  private static Map<String, Object> asMap(Object value) {
    if (value instanceof Map<?, ?> map) {
      return map.entrySet().stream()
          .filter(entry -> entry.getKey() != null)
          .collect(
              Collectors.toMap(
                  entry -> String.valueOf(entry.getKey()),
                  Map.Entry::getValue,
                  (left, right) -> right));
    }
    return Collections.emptyMap();
  }

  private static List<Map<String, Object>> asMapList(Object value) {
    if (!(value instanceof List<?> list)) {
      return Collections.emptyList();
    }
    List<Map<String, Object>> result = new ArrayList<>();
    for (Object item : list) {
      Map<String, Object> map = asMap(item);
      if (!map.isEmpty()) {
        result.add(map);
      }
    }
    return result;
  }

  private static List<String> asStringList(Object value) {
    if (!(value instanceof List<?> list)) {
      return Collections.emptyList();
    }
    return list.stream()
        .map(CircuitAnalysisPayloadSupport::stringify)
        .filter(Objects::nonNull)
        .filter(s -> !s.isBlank())
        .toList();
  }

  private static String getString(Map<String, Object> map, String key, String fallback) {
    String value = stringify(map.get(key));
    return value == null || value.isBlank() ? fallback : value;
  }

  private static void appendIfPresent(StringBuilder builder, String prefix, Object value) {
    String text = stringify(value);
    if (text == null || text.isBlank() || "null".equalsIgnoreCase(text)) {
      return;
    }
    if ("true".equalsIgnoreCase(text)) {
      text = "已收敛";
    } else if ("false".equalsIgnoreCase(text)) {
      text = "未收敛";
    }
    builder.append(prefix).append(text).append('\n');
  }

  private static String stringify(Object value) {
    if (value == null) {
      return null;
    }
    if (value instanceof Number number) {
      return String.format(Locale.ROOT, "%s", number);
    }
    return String.valueOf(value);
  }
}

package cn.yifan.drawsee.config;

import cn.yifan.drawsee.parser.CircuitAnalysisParser;
import cn.yifan.drawsee.parser.DefaultResponseParser;
import cn.yifan.drawsee.parser.ResponseParser;
import cn.yifan.drawsee.parser.SolverResponseParser;
import java.util.HashMap;
import java.util.Map;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 解析器配置 配置不同类型的响应解析器
 *
 * @author yifan
 * @date 2025-04-13 15:40
 */
@Configuration
public class ParserConfig {

  /**
   * 注册解析器映射 将不同类型的解析器注册到系统中
   *
   * @param defaultResponseParser 默认响应解析器
   * @param circuitAnalysisParser 电路分析解析器
   * @param solverResponseParser 解题流程解析器
   * @return 解析器映射
   */
  @Bean
  public Map<String, ResponseParser> parserRegistry(
      DefaultResponseParser defaultResponseParser,
      CircuitAnalysisParser circuitAnalysisParser,
      SolverResponseParser solverResponseParser) {

    Map<String, ResponseParser> registry = new HashMap<>();

    // 注册默认解析器
    registry.put("default", defaultResponseParser);

    // 注册电路分析相关解析器
    registry.put("circuit_basic_analysis", circuitAnalysisParser);
    registry.put("circuit_node_analysis", circuitAnalysisParser);
    registry.put("circuit_function", circuitAnalysisParser);
    registry.put("circuit_optimization", circuitAnalysisParser);

    // 注册知识点详情解析器
    registry.put("knowledge_detail", defaultResponseParser);

    // 注册HTML生成器解析器
    registry.put("html_maker", defaultResponseParser);

    // 注册解题引导解析器
    registry.put("solver_first", solverResponseParser);
    registry.put("solver_continue", solverResponseParser);
    registry.put("solver_summary", solverResponseParser);

    // 注册学习计划解析器
    registry.put("planner_first", defaultResponseParser);
    registry.put("planner_split", defaultResponseParser);

    // 注册通用对话解析器
    registry.put("general_chat", defaultResponseParser);

    return registry;
  }
}

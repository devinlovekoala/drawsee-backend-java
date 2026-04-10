package cn.yifan.drawsee.service.business;

import cn.yifan.drawsee.converter.SpiceConverter;
import cn.yifan.drawsee.pojo.entity.CircuitDesign;
import cn.yifan.drawsee.pojo.vo.SpiceNetlistVO;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * @FileName CircuitAnalysisService @Description 电路分析服务 @Author yifan
 *
 * @date 2025-04-13 21:38
 */
@Service
@Slf4j
public class CircuitAnalysisService {

  @Autowired private SpiceConverter spiceConverter;

  /**
   * 生成电路的SPICE网表
   *
   * @param circuitDesign 电路设计
   * @return SPICE网表数据
   */
  public SpiceNetlistVO generateSpiceNetlist(CircuitDesign circuitDesign) {
    // 生成网表
    String netlist = spiceConverter.generateNetlist(circuitDesign);

    // 提取节点和元件
    List<String> nodes = extractNodes(netlist);
    List<String> components = extractComponents(netlist);

    // 构建返回对象
    SpiceNetlistVO result = new SpiceNetlistVO();
    result.setNetlist(netlist);
    result.setNodes(nodes);
    result.setComponents(components);

    return result;
  }

  /**
   * 从网表中提取节点
   *
   * @param netlist SPICE网表
   * @return 节点列表
   */
  private List<String> extractNodes(String netlist) {
    // 正则表达式匹配所有节点标识（N开头的数字）
    Pattern pattern = Pattern.compile("\\b(N\\d+)\\b");
    Matcher matcher = pattern.matcher(netlist);

    // 使用集合去重
    java.util.Set<String> nodeSet = new java.util.HashSet<>();
    while (matcher.find()) {
      nodeSet.add(matcher.group(1));
    }

    // 添加地节点
    nodeSet.add("0");

    // 转换为列表并排序
    return nodeSet.stream().sorted().toList();
  }

  /**
   * 从网表中提取元件
   *
   * @param netlist SPICE网表
   * @return 元件列表
   */
  private List<String> extractComponents(String netlist) {
    // 分割为行
    String[] lines = netlist.split("\n");

    // 过滤出元件定义行（非注释、非控制命令）
    return Arrays.stream(lines)
        .filter(line -> !line.startsWith("*") && !line.startsWith("."))
        .toList();
  }
}

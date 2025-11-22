package cn.yifan.drawsee.parser;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 电路分析专用响应解析器
 * 针对电路分析结果提供更精确的节点提取
 *
 * @author yifan
 * @date 2025-04-13 15:20
 */
@Slf4j
@Component
public class CircuitAnalysisParser implements ResponseParser {

    /**
     * 节段标记的正则表达式
     * 匹配 [[SECTION:标题]] 格式的标记
     */
    private static final Pattern SECTION_PATTERN = Pattern.compile("\\[\\[SECTION:(.*?)\\]\\]([\\s\\S]*?)(?=\\[\\[SECTION:|$)");

    /**
     * 电路节点分析的正则表达式
     * 特别用于提取Essential Nodes部分的信息
     */
    private static final Pattern NODE_PATTERN = Pattern.compile("节点名称[：:](.*?)\\n.*?作用[：:](.*?)(?=\\n|$)", Pattern.DOTALL);

    @Override
    public List<NodeContent> parseResponse(String aiResponse, String nodeType) {
        List<NodeContent> results = new ArrayList<>();
        
        // 使用节段标记解析
        Matcher sectionMatcher = SECTION_PATTERN.matcher(aiResponse);
        int order = 1;
        
        while (sectionMatcher.find()) {
            String title = sectionMatcher.group(1).trim();
            String content = sectionMatcher.group(2).trim();
            
            // 对于关键节点部分，进行特殊处理
            if (title.contains("关键节点") || title.contains("Essential Nodes")) {
                // 提取节点数量信息
                Pattern countPattern = Pattern.compile("节点数量[：:](\\d+)");
                Matcher countMatcher = countPattern.matcher(content);
                int nodeCount = 0;
                if (countMatcher.find()) {
                    nodeCount = Integer.parseInt(countMatcher.group(1).trim());
                }
                
                // 为关键节点创建一个特殊的节点内容
                NodeContent nodeContent = NodeContent.builder()
                        .title(title)
                        .content(content)
                        .subType(nodeType + "_essential_nodes")
                        .order(order++)
                        .extraData(nodeCount) // 将节点数量作为额外数据存储
                        .build();
                
                results.add(nodeContent);
                
                // 提取各个节点的详细信息
                Matcher nodeMatcher = NODE_PATTERN.matcher(content);
                int nodeOrder = 1;
                
                while (nodeMatcher.find()) {
                    String nodeName = nodeMatcher.group(1).trim();
                    String nodeDescription = nodeMatcher.group(2).trim();
                    
                    NodeContent individualNode = NodeContent.builder()
                            .title("节点: " + nodeName)
                            .content(nodeDescription)
                            .subType(nodeType + "_node_" + nodeOrder)
                            .order(order++)
                            .build();
                    
                    results.add(individualNode);
                    nodeOrder++;
                }
            } else {
                // 对于其他部分，按照普通方式处理
                NodeContent nodeContent = NodeContent.builder()
                        .title(title)
                        .content(content)
                        .subType(nodeType + "_" + title.toLowerCase().replace(" ", "_"))
                        .order(order++)
                        .build();
                
                results.add(nodeContent);
            }
        }
        
        // 如果没有找到任何分段，将整个内容作为一个节点
        if (results.isEmpty()) {
            NodeContent nodeContent = NodeContent.builder()
                    .title("电路分析结果")
                    .content(aiResponse)
                    .subType(nodeType + "_complete")
                    .order(1)
                    .build();
            
            results.add(nodeContent);
        }
        
        return results;
    }
}
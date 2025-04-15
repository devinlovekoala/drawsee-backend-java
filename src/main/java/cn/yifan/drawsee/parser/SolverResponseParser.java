package cn.yifan.drawsee.parser;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 解题工作流专用响应解析器
 * 针对解题过程提供更精确的节点提取
 *
 * @author devin
 * @date 2025-04-13 19:20
 */
@Slf4j
@Component
public class SolverResponseParser implements ResponseParser {

    /**
     * 节段标记的正则表达式
     * 匹配 [[SECTION:标题]] 格式的标记
     */
    private static final Pattern SECTION_PATTERN = Pattern.compile("\\[\\[SECTION:(.*?)\\]\\]([\\s\\S]*?)(?=\\[\\[SECTION:|$)");

    /**
     * 解题步骤的正则表达式
     * 匹配解题步骤中的数字编号和内容
     */
    private static final Pattern STEP_PATTERN = Pattern.compile("(\\d+)\\. (.+?)(?=\\d+\\.|$)", Pattern.DOTALL);

    @Override
    public List<NodeContent> parseResponse(String aiResponse, String nodeType) {
        List<NodeContent> results = new ArrayList<>();
        
        // 使用节段标记解析
        Matcher sectionMatcher = SECTION_PATTERN.matcher(aiResponse);
        int order = 1;
        
        while (sectionMatcher.find()) {
            String title = sectionMatcher.group(1).trim();
            String content = sectionMatcher.group(2).trim();
            
            // 对于解题步骤部分，进行特殊处理
            if (title.contains("解题步骤") || title.contains("推导过程")) {
                // 提取步骤信息
                NodeContent stepsContent = NodeContent.builder()
                        .title(title)
                        .content(content)
                        .subType(nodeType + "_steps")
                        .order(order++)
                        .build();
                
                results.add(stepsContent);
                
                // 提取每个步骤的详细信息
                Matcher stepMatcher = STEP_PATTERN.matcher(content);
                int stepOrder = 1;
                
                while (stepMatcher.find()) {
                    String stepNumber = stepMatcher.group(1).trim();
                    String stepContent = stepMatcher.group(2).trim();
                    
                    NodeContent stepNode = NodeContent.builder()
                            .title("步骤 " + stepNumber)
                            .content(stepContent)
                            .subType(nodeType + "_step_" + stepNumber)
                            .order(order++)
                            .build();
                    
                    results.add(stepNode);
                    stepOrder++;
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
                    .title("解题分析")
                    .content(aiResponse)
                    .subType(nodeType + "_complete")
                    .order(1)
                    .build();
            
            results.add(nodeContent);
        }
        
        return results;
    }
}
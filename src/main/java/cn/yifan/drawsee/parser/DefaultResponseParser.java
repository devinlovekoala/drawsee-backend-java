package cn.yifan.drawsee.parser;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 默认响应解析器实现，基于节段标记规则提取内容
 *
 * @author devin
 * @date 2025-04-13 15:15
 */
@Slf4j
@Component
public class DefaultResponseParser implements ResponseParser {

    /**
     * 节段标记的正则表达式
     * 匹配 [[SECTION:标题]] 格式的标记
     */
    private static final Pattern SECTION_PATTERN = Pattern.compile("\\[\\[SECTION:(.*?)\\]\\]([\\s\\S]*?)(?=\\[\\[SECTION:|$)");

    /**
     * 传统编号标记的正则表达式
     * 匹配 ## 1. 标题 或 ## 第一部分 标题 等格式
     */
    private static final Pattern NUMBERED_PATTERN = Pattern.compile("##\\s*(?:(?:\\d+\\.)|(?:第[一二三四五六七八九十]+部分))\\s*(.*?)\\n([\\s\\S]*?)(?=##|$)");

    @Override
    public List<NodeContent> parseResponse(String aiResponse, String nodeType) {
        List<NodeContent> results = new ArrayList<>();
        
        // 优先使用节段标记解析
        Matcher sectionMatcher = SECTION_PATTERN.matcher(aiResponse);
        boolean foundSections = false;
        int order = 1;
        
        while (sectionMatcher.find()) {
            foundSections = true;
            String title = sectionMatcher.group(1).trim();
            String content = sectionMatcher.group(2).trim();
            
            NodeContent nodeContent = NodeContent.builder()
                    .title(title)
                    .content(content)
                    .subType(nodeType + "_" + title.toLowerCase().replace(" ", "_"))
                    .order(order++)
                    .build();
            
            results.add(nodeContent);
        }
        
        // 如果没有找到节段标记，尝试使用传统编号格式解析
        if (!foundSections) {
            Matcher numberedMatcher = NUMBERED_PATTERN.matcher(aiResponse);
            order = 1;
            
            while (numberedMatcher.find()) {
                String title = numberedMatcher.group(1).trim();
                String content = numberedMatcher.group(2).trim();
                
                NodeContent nodeContent = NodeContent.builder()
                        .title(title)
                        .content(content)
                        .subType(nodeType + "_part_" + order)
                        .order(order++)
                        .build();
                
                results.add(nodeContent);
            }
        }
        
        // 如果上述两种方式都没有找到分段，将整个内容作为一个节点
        if (results.isEmpty()) {
            NodeContent nodeContent = NodeContent.builder()
                    .title("完整内容")
                    .content(aiResponse)
                    .subType(nodeType + "_complete")
                    .order(1)
                    .build();
            
            results.add(nodeContent);
        }
        
        return results;
    }
}
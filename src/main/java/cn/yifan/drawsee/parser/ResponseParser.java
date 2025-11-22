package cn.yifan.drawsee.parser;

import java.util.List;

/**
 * 响应解析器接口，用于解析AI回答内容
 *
 * @author yifan
 * @date 2025-04-13 15:10
 */
public interface ResponseParser {
    
    /**
     * 解析AI输出内容，返回多个子节点的内容
     *
     * @param aiResponse AI回答内容
     * @param nodeType 节点类型
     * @return 解析后的节点内容列表
     */
    List<NodeContent> parseResponse(String aiResponse, String nodeType);
}
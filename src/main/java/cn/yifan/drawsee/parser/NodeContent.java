package cn.yifan.drawsee.parser;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 节点内容模型，用于存储解析后的节点内容信息
 *
 * @author yifan
 * @date 2025-04-13 15:10
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NodeContent {
    
    /**
     * 节点标题
     */
    private String title;
    
    /**
     * 节点内容
     */
    private String content;
    
    /**
     * 节点子类型，用于区分同一类型下的不同子节点
     */
    private String subType;
    
    /**
     * 排序顺序
     */
    private Integer order;
    
    /**
     * 额外数据，可用于存储特定节点类型的额外信息
     */
    private Object extraData;
}
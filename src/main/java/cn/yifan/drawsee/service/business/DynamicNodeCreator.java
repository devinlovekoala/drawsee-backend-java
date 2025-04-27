package cn.yifan.drawsee.service.business;

import cn.yifan.drawsee.parser.NodeContent;
import cn.yifan.drawsee.parser.ResponseParser;
import cn.yifan.drawsee.pojo.XYPosition;
import cn.yifan.drawsee.pojo.entity.Node;
import cn.yifan.drawsee.worker.WorkContext;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 动态节点创建服务
 * 根据AI响应内容动态创建节点树
 *
 * @author devin
 * @date 2025-04-13 15:30
 */
@Slf4j
@Service
public class DynamicNodeCreator {

    @Autowired
    private ObjectMapper objectMapper;
    
    @Autowired
    private Map<String, ResponseParser> parserRegistry;
    
    /**
     * 根据AI响应内容创建子节点
     *
     * @param workContext 工作上下文
     * @param aiResponse AI响应内容
     * @param parentNode 父节点
     * @param nodeType 节点类型
     * @return 创建的节点列表
     * @throws JsonProcessingException JSON处理异常
     */
    public List<Node> createSubNodes(
            WorkContext workContext,
            String aiResponse,
            Node parentNode,
            String nodeType) throws JsonProcessingException {
        
        // 1. 选择合适的解析器
        ResponseParser parser = parserRegistry.getOrDefault(
                nodeType, parserRegistry.get("default"));
                
        if (parser == null) {
            log.warn("未找到节点类型[{}]对应的解析器，将使用默认解析器", nodeType);
            parser = parserRegistry.get("default");
            
            if (parser == null) {
                log.error("未配置默认解析器，无法创建子节点");
                return new ArrayList<>();
            }
        }
            
        // 2. 解析AI回答为多个节点内容
        List<NodeContent> nodeContents = parser.parseResponse(aiResponse, nodeType);
        
        // 3. 为每个内容创建节点
        List<Node> createdNodes = new ArrayList<>();
        for (NodeContent content : nodeContents) {
            Node node = createNodeFromContent(
                    workContext, content, parentNode);
            createdNodes.add(node);
        }
        
        return createdNodes;
    }
    
    /**
     * 根据节点内容创建节点
     *
     * @param workContext 工作上下文
     * @param content 节点内容
     * @param parentNode 父节点
     * @return 创建的节点
     * @throws JsonProcessingException JSON处理异常
     */
    private Node createNodeFromContent(
            WorkContext workContext,
            NodeContent content,
            Node parentNode) throws JsonProcessingException {
        
        Map<String, Object> nodeData = new ConcurrentHashMap<>();
        nodeData.put("title", content.getTitle());
        nodeData.put("text", content.getContent());
        nodeData.put("subtype", content.getSubType());
        
        if (content.getExtraData() != null) {
            nodeData.put("extraData", content.getExtraData());
        }
        
        Node node = new Node(
                parentNode.getType(),
                objectMapper.writeValueAsString(nodeData),
                objectMapper.writeValueAsString(XYPosition.origin()),
                parentNode.getId(),
                workContext.getAiTaskMessage().getUserId(),
                workContext.getAiTaskMessage().getConvId(),
                true
        );
        
        return node;
    }
}
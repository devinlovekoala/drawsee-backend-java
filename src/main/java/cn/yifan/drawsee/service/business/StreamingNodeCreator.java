package cn.yifan.drawsee.service.business;

import cn.yifan.drawsee.constant.AiTaskMessageType;
import cn.yifan.drawsee.constant.NodeType;
import cn.yifan.drawsee.exception.ApiError;
import cn.yifan.drawsee.exception.ApiException;
import cn.yifan.drawsee.parser.ResponseParser;
import cn.yifan.drawsee.pojo.XYPosition;
import cn.yifan.drawsee.pojo.entity.Node;
import cn.yifan.drawsee.pojo.rabbit.AiTaskMessage;
import cn.yifan.drawsee.worker.WorkContext;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RStream;
import org.redisson.api.stream.StreamAddArgs;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 流式节点创建服务
 * 在模型输出过程中实时创建和更新节点树
 *
 * @author devin
 * @date 2025-04-14 10:30
 */
@Slf4j
@Service
public class StreamingNodeCreator {

    @Autowired
    private ObjectMapper objectMapper;
    
    @Autowired
    private Map<String, ResponseParser> parserRegistry;
    
    // 记录节点和内容缓冲区之间的映射关系
    private final Map<String, Map<String, StringBuilder>> taskBuffers = new ConcurrentHashMap<>();
    
    // 每个任务的节点映射关系
    private final Map<String, Map<String, Node>> taskNodes = new ConcurrentHashMap<>();
    
    // 节点标题的格式模式
    private static final Pattern SECTION_PATTERN = Pattern.compile("\\[\\[SECTION:(.*?)\\]\\]");
    
    /**
     * 处理流式输出的token，进行分段和创建节点
     *
     * @param workContext 工作上下文
     * @param token 当前接收到的token
     * @throws JsonProcessingException JSON处理异常
     */
    public void processStreamingToken(WorkContext workContext, String token) throws JsonProcessingException {
        try {
            AiTaskMessage aiTaskMessage = workContext.getAiTaskMessage();
            String taskId = aiTaskMessage.getTaskId().toString();
            Node streamNode = workContext.getStreamNode();
            RStream<String, Object> redisStream = workContext.getRedisStream();
            
            if (streamNode == null || streamNode.getId() == null) {
                log.error("流节点为空或ID为空, taskId: {}, token: {}", taskId, token);
                throw new ApiException(ApiError.SYSTEM_ERROR, "文件不能为空");
            }
            
            // 初始化任务的buffer和nodes
            taskBuffers.computeIfAbsent(taskId, k -> new ConcurrentHashMap<>());
            taskNodes.computeIfAbsent(taskId, k -> new ConcurrentHashMap<>());
            
            Map<String, StringBuilder> buffers = taskBuffers.get(taskId);
            Map<String, Node> nodes = taskNodes.get(taskId);
            
            // 检查token是否包含章节标记
            Matcher matcher = SECTION_PATTERN.matcher(token);
            if (matcher.find()) {
                // 发现章节标记，创建新节点
                String sectionName = matcher.group(1);
                log.debug("发现新章节, taskId: {}, sectionName: {}", taskId, sectionName);
                createOrUpdateNode(workContext, sectionName, "", nodes, redisStream);
                
                // 清除章节标记
                token = token.replaceAll("\\[\\[SECTION:.*?\\]\\]", "");
                
                // 创建新的buffer
                buffers.put(sectionName, new StringBuilder());
            }
            
            // 将token添加到所有活跃的buffer中
            for (Map.Entry<String, StringBuilder> entry : buffers.entrySet()) {
                String sectionName = entry.getKey();
                StringBuilder buffer = entry.getValue();
                buffer.append(token);
                
                // 更新节点内容
                createOrUpdateNode(workContext, sectionName, buffer.toString(), nodes, redisStream);
            }
        } catch (Exception e) {
            log.error("处理流式token失败", e);
            throw e;
        }
    }
    
    /**
     * 创建或更新节点
     */
    private void createOrUpdateNode(
            WorkContext workContext, 
            String sectionName, 
            String content,
            Map<String, Node> nodes,
            RStream<String, Object> redisStream) throws JsonProcessingException {
        
        try {
            AiTaskMessage aiTaskMessage = workContext.getAiTaskMessage();
            Node streamNode = workContext.getStreamNode();
            
            if (!nodes.containsKey(sectionName)) {
                log.debug("创建新节点, sectionName: {}", sectionName);
                // 创建新节点
                Map<String, Object> nodeData = new ConcurrentHashMap<>();
                nodeData.put("title", sectionName);
                nodeData.put("text", content);
                
                Node node = new Node(
                    NodeType.ANSWER,
                    objectMapper.writeValueAsString(nodeData),
                    objectMapper.writeValueAsString(XYPosition.origin()),
                    streamNode.getId(),
                    aiTaskMessage.getUserId(),
                    aiTaskMessage.getConvId(),
                    true
                );
                
                // 保存节点
                nodes.put(sectionName, node);
                
                // 数据库插入并发布
                insertAndPublishNode(workContext, node, nodeData, redisStream);
            } else {
                // 更新现有节点
                updateNodeContent(workContext, sectionName, content, nodes, redisStream);
            }
        } catch (Exception e) {
            log.error("创建或更新节点失败, sectionName: {}", sectionName, e);
            throw e;
        }
    }
    
    /**
     * 更新节点内容
     */
    private void updateNodeContent(
            WorkContext workContext,
            String sectionName,
            String content,
            Map<String, Node> nodes,
            RStream<String, Object> redisStream) throws JsonProcessingException {
        
        try {
            Node node = nodes.get(sectionName);
            
            // 更新节点数据
            Map<String, Object> nodeData = objectMapper.readValue(
                    node.getData(), Map.class);
            nodeData.put("text", content);
            node.setData(objectMapper.writeValueAsString(nodeData));
            
            // 发送节点更新
            Map<String, Object> textData = new ConcurrentHashMap<>();
            textData.put("nodeId", node.getId());
            textData.put("content", content);
            redisStream.add(StreamAddArgs.entries(
                "type", AiTaskMessageType.TEXT, 
                "data", textData
            ));
        } catch (Exception e) {
            log.error("更新节点内容失败, sectionName: {}", sectionName, e);
            throw e;
        }
    }
    
    /**
     * 插入并发布节点
     */
    private void insertAndPublishNode(
            WorkContext workContext, 
            Node node, 
            Map<String, Object> nodeData,
            RStream<String, Object> redisStream) {
        
        try {
            // 插入节点到数据库
            workContext.getNodesToUpdate().add(node);
            
            // 发布节点创建消息
            Map<String, Object> data = new ConcurrentHashMap<>();
            NodeVO nodeVO = new NodeVO();
            BeanUtils.copyProperties(node, nodeVO);
            nodeVO.setData(nodeData);
            data.put("node", nodeVO);
            redisStream.add(StreamAddArgs.entries(
                "type", AiTaskMessageType.NODE, 
                "data", data
            ));
        } catch (Exception e) {
            log.error("插入并发布节点失败, node: {}", node, e);
            throw new RuntimeException("节点发布失败", e);
        }
    }
    
    /**
     * 任务完成时清理资源
     *
     * @param taskId 任务ID
     */
    public void cleanupTask(Long taskId) {
        if (taskId != null) {
            taskBuffers.remove(taskId.toString());
            taskNodes.remove(taskId.toString());
        }
    }
    
    // 内部类用于前端显示
    @Setter
    @Getter
    public static class NodeVO {
        // getter and setter
        private Long id;
        private String type;
        private Map<String, Object> data;
        private String position;
        private Long parentId;
        private Long userId;
        private Long convId;

    }
}


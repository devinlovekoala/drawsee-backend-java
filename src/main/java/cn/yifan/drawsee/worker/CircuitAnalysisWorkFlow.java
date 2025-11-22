package cn.yifan.drawsee.worker;

import cn.yifan.drawsee.constant.NodeTitle;
import cn.yifan.drawsee.constant.AiTaskMessageType;
import cn.yifan.drawsee.constant.NodeSubType;
import cn.yifan.drawsee.constant.NodeType;
import cn.yifan.drawsee.constant.AiModel;
import cn.yifan.drawsee.converter.SpiceConverter;
import cn.yifan.drawsee.mapper.AiTaskMapper;
import cn.yifan.drawsee.mapper.ConversationMapper;
import cn.yifan.drawsee.mapper.NodeMapper;
import cn.yifan.drawsee.mapper.UserMapper;
import cn.yifan.drawsee.pojo.XYPosition;
import cn.yifan.drawsee.pojo.entity.CircuitDesign;
import cn.yifan.drawsee.pojo.entity.Node;
import cn.yifan.drawsee.pojo.rabbit.AiTaskMessage;
import cn.yifan.drawsee.service.base.AiService;
import cn.yifan.drawsee.service.base.PromptService;
import cn.yifan.drawsee.service.base.StreamAiService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.StreamingResponseHandler;
import dev.langchain4j.model.output.Response;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RStream;
import org.redisson.api.RedissonClient;
import org.redisson.api.stream.StreamAddArgs;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.LinkedList;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * @FileName CircuitAnalysisWorkFlow
 * @Description 电路分析工作流，处理电路分析任务
 * @Author yifan
 * @date 2025-04-04 16:20
 **/

@Slf4j
@Service
public class CircuitAnalysisWorkFlow extends WorkFlow {

    private final PromptService promptService;
    private final SpiceConverter spiceConverter;

    public CircuitAnalysisWorkFlow(
            UserMapper userMapper,
            AiService aiService,
            StreamAiService streamAiService,
            RedissonClient redissonClient,
            NodeMapper nodeMapper,
            ConversationMapper conversationMapper,
            AiTaskMapper aiTaskMapper,
            ObjectMapper objectMapper,
            PromptService promptService,
            SpiceConverter spiceConverter
    ) {
        super(userMapper, aiService, streamAiService, redissonClient, nodeMapper, conversationMapper, aiTaskMapper, objectMapper);
        this.promptService = promptService;
        this.spiceConverter = spiceConverter;
    }

    @Override
    public Boolean validateAndInit(WorkContext workContext) {
        workContext.setIsSendDone(false);
        return super.validateAndInit(workContext);
    }

    @Override
    public void createInitNodes(WorkContext workContext) throws JsonProcessingException {
        AiTaskMessage aiTaskMessage = workContext.getAiTaskMessage();
        
        // 创建电路画布节点（替代之前的查询节点）
        Map<String, Object> canvasNodeData = new ConcurrentHashMap<>();
        canvasNodeData.put("title", "电路设计");
        canvasNodeData.put("text", "电路分析请求");
        canvasNodeData.put("circuitDesign", objectMapper.readValue(aiTaskMessage.getPrompt(), CircuitDesign.class));
        canvasNodeData.put("mode", aiTaskMessage.getType());
        
        Node canvasNode = new Node(
            NodeType.CIRCUIT_CANVAS,  // 使用新的节点类型
            objectMapper.writeValueAsString(canvasNodeData),
            objectMapper.writeValueAsString(XYPosition.origin()),
            aiTaskMessage.getParentId(),
            aiTaskMessage.getUserId(),
            aiTaskMessage.getConvId(),
            true
        );
        
        insertAndPublishNoneStreamNode(workContext, canvasNode, canvasNodeData);
        
        // 暂不创建AI回答节点，跟通用问答类似，直接在canvasNode下创建分点节点
        setupVirtualStreamNode(workContext, canvasNode.getId());
    }
    
    /**
     * 设置一个虚拟的流式节点，用于后续的分析点节点创建
     * 该节点不会实际插入数据库，仅用于后续处理
     */
    private void setupVirtualStreamNode(WorkContext workContext, Long parentId) throws JsonProcessingException {
        AiTaskMessage aiTaskMessage = workContext.getAiTaskMessage();
        
        // 创建流式节点数据，但不实际保存到数据库
        Map<String, Object> streamNodeData = new ConcurrentHashMap<>();
        streamNodeData.put("title", "电路分析");
        streamNodeData.put("text", "");
        
        // 创建一个Node对象，但不实际插入数据库
        Node virtualNode = new Node(
            NodeType.ANSWER,  // 类型无关紧要，不会被保存
            objectMapper.writeValueAsString(streamNodeData),
            objectMapper.writeValueAsString(XYPosition.origin()),
            parentId,
            aiTaskMessage.getUserId(),
            aiTaskMessage.getConvId(),
            false // 标记为非活跃
        );
        
        // 为虚拟节点设置一个临时ID
        virtualNode.setId(-1L);
        
        // 设置到WorkContext中
        workContext.setStreamNode(virtualNode);
        workContext.setStreamNodeData(streamNodeData);
    }

    @Override
    public void streamChat(WorkContext workContext, StreamingResponseHandler<AiMessage> handler) throws JsonProcessingException {
        AiTaskMessage aiTaskMessage = workContext.getAiTaskMessage();
        LinkedList<ChatMessage> history = workContext.getHistory();
        String model = aiTaskMessage.getModel();
        
        // 解析电路设计JSON
        CircuitDesign circuitDesign = objectMapper.readValue(aiTaskMessage.getPrompt(), CircuitDesign.class);
        
        // 生成SPICE网表
        String spiceNetlist = spiceConverter.generateNetlist(circuitDesign);
        
        // 生成分析点提示词
        String analysisPrompt = promptService.getCircuitPointAnalysisPrompt(circuitDesign, spiceNetlist);
        
        // 调用AI进行分析
        streamAiService.circuitAnalysisChat(history, analysisPrompt, model, handler);
    }

    @Override
    public void createOtherNodesOrUpdateNodeData(WorkContext workContext) throws JsonProcessingException {
        RStream<String, Object> redisStream = workContext.getRedisStream();
        AiTaskMessage aiTaskMessage = workContext.getAiTaskMessage();
        Node streamNode = workContext.getStreamNode();
        Response<AiMessage> streamResponse = workContext.getStreamResponse();
        
        // 获取父节点ID（电路画布节点ID）
        Long parentId = streamNode.getParentId();
        
        // 解析分析点结果
        String responseText = streamResponse.content().text();
        
        try {
            // 解析回答角度（分析点）
            processCircuitAnalysisPoints(workContext, responseText, parentId);
        } catch (Exception e) {
            log.error("解析电路分析点失败: {}", responseText, e);
        }
        
        // 更新进度信息
        Map<String, Object> data = new ConcurrentHashMap<>();
        data.put("progress", "电路分析点生成完成");
        redisStream.add(StreamAddArgs.entries(
            "type", AiTaskMessageType.DATA,
            "data", data
        ));
        
        // 发送结束消息
        redisStream.add(StreamAddArgs.entries(
            "type", AiTaskMessageType.DONE, 
            "data", ""
        ));
    }
    
    /**
     * 处理电路分析点，创建分析点节点
     */
    private void processCircuitAnalysisPoints(WorkContext workContext, String responseText, Long parentId) throws JsonProcessingException {
        AiTaskMessage aiTaskMessage = workContext.getAiTaskMessage();
        
        // 使用文本方式解析
        String[] lines = responseText.split("\n");
        String currentTitle = null;
        String currentDescription = null;
        
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i].trim();
            
            if (line.isEmpty()) {
                // 如果是空行，且已有标题和描述，创建节点
                if (currentTitle != null && currentDescription != null) {
                    createCircuitPointNode(workContext, parentId, aiTaskMessage, currentTitle, currentDescription);
                    currentTitle = null;
                    currentDescription = null;
                }
                continue;
            }
            
            // 匹配"角度X：[标题]"格式
            if (line.matches("^角度\\d+：.+")) {
                // 如果已有标题和描述，先创建之前的节点
                if (currentTitle != null && currentDescription != null) {
                    createCircuitPointNode(workContext, parentId, aiTaskMessage, currentTitle, currentDescription);
                }
                
                // 提取标题
                currentTitle = line.substring(line.indexOf("：") + 1).trim();
                currentDescription = null;
            } 
            // 如果有标题但没有描述，当前行作为描述
            else if (currentTitle != null && currentDescription == null) {
                currentDescription = line;
            }
        }
        
        // 处理最后一个角度
        if (currentTitle != null && currentDescription != null) {
            createCircuitPointNode(workContext, parentId, aiTaskMessage, currentTitle, currentDescription);
        }
    }
    
    /**
     * 创建电路分析点节点
     */
    private void createCircuitPointNode(WorkContext workContext, Long parentId, AiTaskMessage aiTaskMessage, 
                                      String title, String description) throws JsonProcessingException {
        Map<String, Object> circuitPointNodeData = new ConcurrentHashMap<>();
        circuitPointNodeData.put("title", title);
        circuitPointNodeData.put("text", description);
        circuitPointNodeData.put("subtype", "circuit-point");
        
        Node circuitPointNode = new Node(
            NodeType.CIRCUIT_POINT,
            objectMapper.writeValueAsString(circuitPointNodeData),
            objectMapper.writeValueAsString(XYPosition.origin()),
            parentId,
            aiTaskMessage.getUserId(),
            aiTaskMessage.getConvId(),
            true
        );
        
        try {
            insertAndPublishNoneStreamNode(workContext, circuitPointNode, circuitPointNodeData);
        } catch (Exception e) {
            log.error("创建电路分析点节点失败: {}", e.getMessage(), e);
        }
    }

    @Override
    public void updateStreamNode(WorkContext workContext) throws JsonProcessingException {
        // 由于使用虚拟节点，不需要更新节点内容
        log.info("跳过虚拟流节点更新");
    }
} 
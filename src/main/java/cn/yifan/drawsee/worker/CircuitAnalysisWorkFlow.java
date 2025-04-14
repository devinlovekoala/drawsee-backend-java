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
import cn.yifan.drawsee.repository.KnowledgeRepository;
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
 * @FileName ToolService
 * @Description
 * @Author devin
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
            KnowledgeRepository knowledgeRepository,
            NodeMapper nodeMapper,
            ConversationMapper conversationMapper,
            AiTaskMapper aiTaskMapper,
            ObjectMapper objectMapper,
            PromptService promptService,
            SpiceConverter spiceConverter
    ) {
        super(userMapper, aiService, streamAiService, redissonClient, knowledgeRepository, nodeMapper, conversationMapper, aiTaskMapper, objectMapper);
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
        
        // 创建用户查询节点
        Map<String, Object> queryNodeData = new ConcurrentHashMap<>();
        queryNodeData.put("title", NodeTitle.QUERY);
        queryNodeData.put("text", "电路分析请求");
        queryNodeData.put("circuitDesign", objectMapper.readValue(aiTaskMessage.getPrompt(), CircuitDesign.class));
        queryNodeData.put("mode", aiTaskMessage.getType());
        
        Node queryNode = new Node(
            NodeType.QUERY,
            objectMapper.writeValueAsString(queryNodeData),
            objectMapper.writeValueAsString(XYPosition.origin()),
            aiTaskMessage.getParentId(),
            aiTaskMessage.getUserId(),
            aiTaskMessage.getConvId(),
            true
        );
        
        insertAndPublishNoneStreamNode(workContext, queryNode, queryNodeData);
        
        // 创建AI回答节点
        createInitStreamNode(workContext, queryNode.getId());
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
        
        // 生成分析提示词
        String analysisPrompt = promptService.getCircuitAnalysisPrompt(circuitDesign, spiceNetlist);
        
        // 调用AI进行分析
        streamAiService.circuitAnalysisChat(history, analysisPrompt, model, handler);
    }

    @Override
    public void createOtherNodesOrUpdateNodeData(WorkContext workContext) throws JsonProcessingException {
        RStream<String, Object> redisStream = workContext.getRedisStream();
        AiTaskMessage aiTaskMessage = workContext.getAiTaskMessage();
        Node streamNode = workContext.getStreamNode();
        LinkedList<ChatMessage> history = workContext.getHistory();
        String model = aiTaskMessage.getModel();

        // 解析电路设计JSON
        CircuitDesign circuitDesign = objectMapper.readValue(aiTaskMessage.getPrompt(), CircuitDesign.class);

        // 生成SPICE网表
        String spiceNetlist = spiceConverter.generateNetlist(circuitDesign);
        
        // 1. 创建电路基本情况分析节点
        Map<String, Object> circuitBasicNodeData = new ConcurrentHashMap<>();
        circuitBasicNodeData.put("title", "电路基本分析");
        circuitBasicNodeData.put("subtype", "CIRCUIT_BASIC");
        circuitBasicNodeData.put("progress", "正在分析电路基本情况...");
        
        Node circuitBasicNode = new Node(
            NodeType.ANSWER,
            objectMapper.writeValueAsString(circuitBasicNodeData),
            objectMapper.writeValueAsString(XYPosition.origin()),
            streamNode.getId(),
            aiTaskMessage.getUserId(),
            aiTaskMessage.getConvId(),
            true
        );
        
        insertAndPublishNoneStreamNode(workContext, circuitBasicNode, circuitBasicNodeData);

        // 生成电路基本分析
        String basicAnalysisPrompt = promptService.getCircuitBasicAnalysisPrompt(circuitDesign, spiceNetlist);
        
        // 创建临时响应处理器来获取结果
        StringResponseHandler basicAnalysisHandler = new StringResponseHandler();
        LinkedList<ChatMessage> basicAnalysisHistory = new LinkedList<>();
        basicAnalysisHistory.add(new UserMessage(basicAnalysisPrompt));
        
        // 使用streamAiService进行调用
        streamAiService.circuitAnalysisChat(basicAnalysisHistory, basicAnalysisPrompt, model, basicAnalysisHandler);
        String basicAnalysisResult = basicAnalysisHandler.getResponse();
        
        // 更新电路基本分析节点数据
        circuitBasicNodeData.put("basicAnalysis", basicAnalysisResult);
        insertAndPublishNoneStreamNode(workContext, circuitBasicNode, circuitBasicNodeData);
        
        // 2. 获取Essential Nodes信息
        String essentialNodesPrompt = promptService.getCircuitEssentialNodesPrompt(circuitDesign, spiceNetlist);
        
        // 创建临时响应处理器来获取结果
        StringResponseHandler essentialNodesHandler = new StringResponseHandler();
        LinkedList<ChatMessage> essentialNodesHistory = new LinkedList<>();
        essentialNodesHistory.add(new UserMessage(essentialNodesPrompt));
        
        // 使用streamAiService进行调用
        streamAiService.circuitAnalysisChat(essentialNodesHistory, essentialNodesPrompt, model, essentialNodesHandler);
        String essentialNodesJson = essentialNodesHandler.getResponse();
        
        // 解析JSON响应
        EssentialNodesResult essentialNodesResult = null;
        try {
            essentialNodesResult = objectMapper.readValue(essentialNodesJson, EssentialNodesResult.class);
        } catch (Exception e) {
            log.error("解析Essential Nodes JSON失败", e);
            // 创建一个默认的结果
            essentialNodesResult = new EssentialNodesResult();
            essentialNodesResult.setEssentialNodeCount(1);
            EssentialNode defaultNode = new EssentialNode();
            defaultNode.setName("未知节点");
            defaultNode.setDescription("解析失败，无法获取节点信息");
            essentialNodesResult.setEssentialNodes(Collections.singletonList(defaultNode));
        }
        
        // 如果找到Essential Nodes，为每个创建分析节点
        if (essentialNodesResult != null && essentialNodesResult.getEssentialNodes() != null && !essentialNodesResult.getEssentialNodes().isEmpty()) {
            for (EssentialNode essentialNode : essentialNodesResult.getEssentialNodes()) {
                String nodeName = essentialNode.getName();
                
                // 为每个Essential Node创建分析节点
                Map<String, Object> nodeAnalysisNodeData = new ConcurrentHashMap<>();
                nodeAnalysisNodeData.put("title", "节点[" + nodeName + "]分析");
                nodeAnalysisNodeData.put("subtype", "CIRCUIT_NODE_ANALYSIS");
                nodeAnalysisNodeData.put("progress", "正在分析节点[" + nodeName + "]...");
                nodeAnalysisNodeData.put("nodeName", nodeName);
                nodeAnalysisNodeData.put("nodeDescription", essentialNode.getDescription());
                
                Node nodeAnalysisNode = new Node(
                    NodeType.ANSWER,
                    objectMapper.writeValueAsString(nodeAnalysisNodeData),
                    objectMapper.writeValueAsString(XYPosition.origin()),
                    circuitBasicNode.getId(),
                    aiTaskMessage.getUserId(),
                    aiTaskMessage.getConvId(),
                    true
                );
                
                insertAndPublishNoneStreamNode(workContext, nodeAnalysisNode, nodeAnalysisNodeData);
                
                // 生成节点分析
                String nodeAnalysisPrompt = promptService.getCircuitNodeAnalysisPrompt(circuitDesign, spiceNetlist, nodeName);
                
                // 创建临时响应处理器
                StringResponseHandler nodeAnalysisHandler = new StringResponseHandler();
                LinkedList<ChatMessage> nodeAnalysisHistory = new LinkedList<>();
                nodeAnalysisHistory.add(new UserMessage(nodeAnalysisPrompt));
                
                // 使用streamAiService进行调用
                streamAiService.circuitAnalysisChat(nodeAnalysisHistory, nodeAnalysisPrompt, model, nodeAnalysisHandler);
                String nodeAnalysisResult = nodeAnalysisHandler.getResponse();
                
                // 更新节点分析数据
                nodeAnalysisNodeData.put("nodeAnalysis", nodeAnalysisResult);
                insertAndPublishNoneStreamNode(workContext, nodeAnalysisNode, nodeAnalysisNodeData);
            }
        }
        
        // 3. 创建电路功能分析节点
        Map<String, Object> functionNodeData = new ConcurrentHashMap<>();
        functionNodeData.put("title", "电路功能分析");
        functionNodeData.put("subtype", "CIRCUIT_FUNCTION");
        functionNodeData.put("progress", "正在分析电路功能...");
        
        Node functionNode = new Node(
            NodeType.ANSWER,
            objectMapper.writeValueAsString(functionNodeData),
            objectMapper.writeValueAsString(XYPosition.origin()),
            circuitBasicNode.getId(),
            aiTaskMessage.getUserId(),
            aiTaskMessage.getConvId(),
            true
        );
        
        insertAndPublishNoneStreamNode(workContext, functionNode, functionNodeData);
        
        // 生成电路功能分析
        String functionPrompt = promptService.getCircuitFunctionPrompt(circuitDesign, spiceNetlist, basicAnalysisResult);
        
        // 创建临时响应处理器
        StringResponseHandler functionHandler = new StringResponseHandler();
        LinkedList<ChatMessage> functionHistory = new LinkedList<>();
        functionHistory.add(new UserMessage(functionPrompt));
        
        // 使用streamAiService进行调用
        streamAiService.circuitAnalysisChat(functionHistory, functionPrompt, model, functionHandler);
        String functionResult = functionHandler.getResponse();
        
        // 更新电路功能分析节点数据
        functionNodeData.put("functionAnalysis", functionResult);
        insertAndPublishNoneStreamNode(workContext, functionNode, functionNodeData);
        
        // 4. 创建电路优化建议节点
        Map<String, Object> optimizationNodeData = new ConcurrentHashMap<>();
        optimizationNodeData.put("title", "电路优化建议");
        optimizationNodeData.put("subtype", "CIRCUIT_OPTIMIZATION");
        optimizationNodeData.put("progress", "正在生成优化建议...");
        
        Node optimizationNode = new Node(
            NodeType.ANSWER,
            objectMapper.writeValueAsString(optimizationNodeData),
            objectMapper.writeValueAsString(XYPosition.origin()),
            functionNode.getId(),
            aiTaskMessage.getUserId(),
            aiTaskMessage.getConvId(),
            true
        );
        
        insertAndPublishNoneStreamNode(workContext, optimizationNode, optimizationNodeData);
        
        // 生成优化建议
        String optimizationPrompt = promptService.getCircuitOptimizationSuggestionPrompt(basicAnalysisResult, functionResult);
        
        // 创建临时响应处理器
        StringResponseHandler optimizationHandler = new StringResponseHandler();
        LinkedList<ChatMessage> optimizationHistory = new LinkedList<>();
        optimizationHistory.add(new UserMessage(optimizationPrompt));
        
        // 使用streamAiService进行调用
        streamAiService.circuitAnalysisChat(optimizationHistory, optimizationPrompt, model, optimizationHandler);
        String optimizationResult = optimizationHandler.getResponse();
        
        // 更新优化节点数据
        optimizationNodeData.put("optimizationResult", optimizationResult);
        insertAndPublishNoneStreamNode(workContext, optimizationNode, optimizationNodeData);

        // 更新进度信息
        Map<String, Object> data = new ConcurrentHashMap<>();
        data.put("progress", "电路分析完成");
        data.put("nodeId", optimizationNode.getId());
        redisStream.add(StreamAddArgs.entries(
            "type", AiTaskMessageType.DATA,
            "data", data
        ));
    }
    
    /**
     * Essential Nodes结果类
     */
    private static class EssentialNodesResult {
        private int essentialNodeCount;
        private List<EssentialNode> essentialNodes;
        
        public int getEssentialNodeCount() {
            return essentialNodeCount;
        }
        
        public void setEssentialNodeCount(int essentialNodeCount) {
            this.essentialNodeCount = essentialNodeCount;
        }
        
        public List<EssentialNode> getEssentialNodes() {
            return essentialNodes;
        }
        
        public void setEssentialNodes(List<EssentialNode> essentialNodes) {
            this.essentialNodes = essentialNodes;
        }
    }
    
    /**
     * Essential Node信息类
     */
    private static class EssentialNode {
        private String name;
        private String description;
        
        public String getName() {
            return name;
        }
        
        public void setName(String name) {
            this.name = name;
        }
        
        public String getDescription() {
            return description;
        }
        
        public void setDescription(String description) {
            this.description = description;
        }
    }
    
    /**
     * 临时响应处理器，用于获取流式AI调用的结果
     */
    private static class StringResponseHandler implements StreamingResponseHandler<AiMessage> {
        private final StringBuilder response = new StringBuilder();
        
        @Override
        public void onNext(String token) {
            response.append(token);
        }
        
        @Override
        public void onComplete(Response<AiMessage> response) {
            // 不需要处理完成回调
        }
        
        @Override
        public void onError(Throwable error) {
            log.error("流式AI调用出错", error);
        }
        
        public String getResponse() {
            return response.toString();
        }
    }
} 
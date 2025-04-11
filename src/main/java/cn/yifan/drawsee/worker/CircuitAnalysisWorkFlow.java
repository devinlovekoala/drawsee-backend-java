package cn.yifan.drawsee.worker;

import cn.yifan.drawsee.constant.NodeTitle;
import cn.yifan.drawsee.constant.AiTaskMessageType;
import cn.yifan.drawsee.constant.NodeSubType;
import cn.yifan.drawsee.constant.NodeType;
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
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.StreamingResponseHandler;
import dev.langchain4j.model.chat.ChatLanguageModel;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RStream;
import org.redisson.api.RedissonClient;
import org.redisson.api.stream.StreamAddArgs;
import org.springframework.stereotype.Service;

import java.util.LinkedList;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

@Slf4j
@Service
public class CircuitAnalysisWorkFlow extends WorkFlow {

    private final PromptService promptService;
    private final ChatLanguageModel deepseekV3ChatLanguageModel;
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
            ChatLanguageModel deepseekV3ChatLanguageModel,
            SpiceConverter spiceConverter
    ) {
        super(userMapper, aiService, streamAiService, redissonClient, knowledgeRepository, nodeMapper, conversationMapper, aiTaskMapper, objectMapper);
        this.promptService = promptService;
        this.deepseekV3ChatLanguageModel = deepseekV3ChatLanguageModel;
        this.spiceConverter = spiceConverter;
    }

    @Override
    public Boolean validateAndInit(WorkContext workContext) {
        workContext.setIsSendDone(false);
        return super.validateAndInit(workContext);
    }

    @Override
    public void streamChat(WorkContext workContext, StreamingResponseHandler<AiMessage> handler) throws JsonProcessingException {
        AiTaskMessage aiTaskMessage = workContext.getAiTaskMessage();
        LinkedList<ChatMessage> history = workContext.getHistory();
        
        // 解析电路设计JSON
        CircuitDesign circuitDesign = objectMapper.readValue(aiTaskMessage.getPrompt(), CircuitDesign.class);
        
        // 生成SPICE网表
        String spiceNetlist = spiceConverter.generateNetlist(circuitDesign);
        
        // 生成分析提示词
        String analysisPrompt = promptService.getCircuitAnalysisPrompt(circuitDesign, spiceNetlist);
        
        // 调用AI进行分析
        streamAiService.circuitAnalysisChat(history, analysisPrompt, handler);
    }

    @Override
    public void createOtherNodesOrUpdateNodeData(WorkContext workContext) throws JsonProcessingException {
        RStream<String, Object> redisStream = workContext.getRedisStream();
        AiTaskMessage aiTaskMessage = workContext.getAiTaskMessage();
        Node streamNode = workContext.getStreamNode();
        AtomicLong tokens = workContext.getTokens();
        LinkedList<ChatMessage> history = workContext.getHistory();

        // 解析电路设计JSON
        CircuitDesign circuitDesign = objectMapper.readValue(aiTaskMessage.getPrompt(), CircuitDesign.class);

        // 1. 创建SPICE网表生成节点
        Map<String, Object> spiceNodeData = new ConcurrentHashMap<>();
        spiceNodeData.put("title", NodeTitle.CIRCUIT_SPICE);
        spiceNodeData.put("subtype", NodeSubType.CIRCUIT_SPICE);
        spiceNodeData.put("progress", "正在生成SPICE网表...");
        
        Node spiceNode = new Node(
            NodeType.ANSWER,
            objectMapper.writeValueAsString(spiceNodeData),
            objectMapper.writeValueAsString(XYPosition.origin()),
            streamNode.getId(),
            aiTaskMessage.getUserId(),
            aiTaskMessage.getConvId(),
            true
        );
        
        insertAndPublishNoneStreamNode(workContext, spiceNode, spiceNodeData);

        // 生成SPICE网表
        String spiceNetlist = spiceConverter.generateNetlist(circuitDesign);
        String spicePrompt = promptService.getCircuitSpicePrompt(circuitDesign);
        String spiceAnalysis = deepseekV3ChatLanguageModel.generate(spicePrompt);
        
        // 更新SPICE节点数据
        spiceNodeData.put("spiceNetlist", spiceNetlist);
        spiceNodeData.put("spiceAnalysis", spiceAnalysis);
        insertAndPublishNoneStreamNode(workContext, spiceNode, spiceNodeData);

        // 2. 创建电路分析节点
        Map<String, Object> analysisNodeData = new ConcurrentHashMap<>();
        analysisNodeData.put("title", NodeTitle.CIRCUIT_ANALYSIS);
        analysisNodeData.put("subtype", NodeSubType.CIRCUIT_ANALYSIS);
        analysisNodeData.put("progress", "正在分析电路...");
        
        Node analysisNode = new Node(
            NodeType.ANSWER,
            objectMapper.writeValueAsString(analysisNodeData),
            objectMapper.writeValueAsString(XYPosition.origin()),
            spiceNode.getId(),  // 将分析节点连接到SPICE节点
            aiTaskMessage.getUserId(),
            aiTaskMessage.getConvId(),
            true
        );
        
        insertAndPublishNoneStreamNode(workContext, analysisNode, analysisNodeData);

        // 生成分析提示词并进行分析
        String analysisPrompt = promptService.getCircuitAnalysisPrompt(circuitDesign, spiceNetlist);
        history.add(new UserMessage(analysisPrompt));
        String analysisResult = deepseekV3ChatLanguageModel.generate(analysisPrompt);
        AiMessage analysisResponse = new AiMessage(analysisResult);
        history.add(analysisResponse);
        
        // 更新分析节点数据
        analysisNodeData.put("analysisPrompt", analysisPrompt);
        analysisNodeData.put("analysisResult", analysisResponse.text());
        insertAndPublishNoneStreamNode(workContext, analysisNode, analysisNodeData);

        // 3. 创建电路优化建议节点
        Map<String, Object> optimizationNodeData = new ConcurrentHashMap<>();
        optimizationNodeData.put("title", NodeTitle.CIRCUIT_OPTIMIZATION);
        optimizationNodeData.put("subtype", NodeSubType.CIRCUIT_OPTIMIZATION);
        optimizationNodeData.put("progress", "正在生成优化建议...");
        
        Node optimizationNode = new Node(
            NodeType.ANSWER,
            objectMapper.writeValueAsString(optimizationNodeData),
            objectMapper.writeValueAsString(XYPosition.origin()),
            analysisNode.getId(),  // 将优化节点连接到分析节点
            aiTaskMessage.getUserId(),
            aiTaskMessage.getConvId(),
            true
        );
        
        insertAndPublishNoneStreamNode(workContext, optimizationNode, optimizationNodeData);

        // 生成优化建议
        String optimizationPrompt = promptService.getCircuitOptimizationPrompt(analysisResponse.text());
        String optimizationResult = deepseekV3ChatLanguageModel.generate(optimizationPrompt);
        
        // 更新优化节点数据
        optimizationNodeData.put("optimizationPrompt", optimizationPrompt);
        optimizationNodeData.put("optimizationResult", optimizationResult);
        insertAndPublishNoneStreamNode(workContext, optimizationNode, optimizationNodeData);

        // 更新进度信息
        Map<String, Object> data = new ConcurrentHashMap<>();
        data.put("progress", "电路分析完成");
        data.put("nodeId", optimizationNode.getId());
        redisStream.add(StreamAddArgs.entries(Map.of(
            "type", AiTaskMessageType.DATA,
            "data", data
        )));
    }
} 
package cn.yifan.drawsee.worker;

import cn.yifan.drawsee.constant.AiTaskMessageType;
import cn.yifan.drawsee.constant.NodeType;
import cn.yifan.drawsee.mapper.AiTaskMapper;
import cn.yifan.drawsee.mapper.ConversationMapper;
import cn.yifan.drawsee.mapper.NodeMapper;
import cn.yifan.drawsee.mapper.UserMapper;
import cn.yifan.drawsee.pojo.XYPosition;
import cn.yifan.drawsee.pojo.entity.Node;
import cn.yifan.drawsee.pojo.entity.UserDocument;
import cn.yifan.drawsee.pojo.rabbit.AiTaskMessage;
import cn.yifan.drawsee.service.business.UserDocumentService;
import cn.yifan.drawsee.service.base.AiService;
import cn.yifan.drawsee.service.base.MinioService;
import cn.yifan.drawsee.service.base.StreamAiService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.ImageContent;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.StreamingResponseHandler;
import dev.langchain4j.model.output.Response;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RStream;
import org.redisson.api.RedissonClient;
import org.redisson.api.stream.StreamAddArgs;
import org.springframework.stereotype.Service;

import java.util.LinkedList;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * PDF电路分析详情工作流
 * 处理用户上传的PDF实验文档，进行AI分析
 * 
 * @author devin
 * @date 2025-01-28
 */
@Slf4j
@Service
public class PdfCircuitAnalysisDetailWorkFlow extends WorkFlow {

    private final UserDocumentService userDocumentService;
    private final MinioService minioService;

    public PdfCircuitAnalysisDetailWorkFlow(
            UserMapper userMapper,
            AiService aiService,
            StreamAiService streamAiService,
            RedissonClient redissonClient,
            NodeMapper nodeMapper,
            ConversationMapper conversationMapper,
            AiTaskMapper aiTaskMapper,
            ObjectMapper objectMapper,
            UserDocumentService userDocumentService,
            MinioService minioService
    ) {
        super(userMapper, aiService, streamAiService, redissonClient, nodeMapper, conversationMapper, aiTaskMapper, objectMapper);
        this.userDocumentService = userDocumentService;
        this.minioService = minioService;
    }

    @Override
    public Boolean validateAndInit(WorkContext workContext) {
        workContext.setIsSendDone(false);
        return super.validateAndInit(workContext);
    }

    @Override
    public void createInitNodes(WorkContext workContext) throws JsonProcessingException {
        AiTaskMessage aiTaskMessage = workContext.getAiTaskMessage();
        
        // 从prompt中解析文档UUID
        String documentUuid = aiTaskMessage.getPrompt();
        log.info("开始处理PDF电路分析任务，文档UUID: {}", documentUuid);
        
        // 获取文档信息
        UserDocument document = userDocumentService.getDocumentByUuid(documentUuid, aiTaskMessage.getUserId());
        
        // 创建查询节点
        Map<String, Object> queryNodeData = new ConcurrentHashMap<>();
        queryNodeData.put("title", "实验文档分析");
        queryNodeData.put("text", "请分析以下实验文档并提供详细的电路分析");
        queryNodeData.put("documentId", document.getId());
        queryNodeData.put("documentTitle", document.getTitle());
        queryNodeData.put("documentType", document.getDocumentType());
        
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
        Map<String, Object> streamNodeData = new ConcurrentHashMap<>();
        streamNodeData.put("title", "电路分析回答");
        streamNodeData.put("text", "");
        
        Node streamNode = new Node(
            NodeType.ANSWER,
            objectMapper.writeValueAsString(streamNodeData),
            objectMapper.writeValueAsString(XYPosition.origin()),
            queryNode.getId(),
            aiTaskMessage.getUserId(),
            aiTaskMessage.getConvId(),
            true
        );
        
        insertAndPublishStreamNode(workContext, streamNode, streamNodeData);
        
        // 将文档信息存储到workContext中供后续使用
        workContext.putExtraData("document", document);
    }

    @Override
    public void streamChat(WorkContext workContext, StreamingResponseHandler<AiMessage> handler) throws JsonProcessingException {
        AiTaskMessage aiTaskMessage = workContext.getAiTaskMessage();
        LinkedList<ChatMessage> history = workContext.getHistory();
        String model = aiTaskMessage.getModel();
        
        // 获取文档信息
        UserDocument document = (UserDocument) workContext.getExtraData("document");
        
        try {
            // 构建包含文档的聊天消息
            UserMessage userMessage = createUserMessageWithDocument(document);
            
            // 添加到历史记录
            history.add(userMessage);
            
            // 调用AI进行PDF文档分析 - 使用通用的generalChat方法
            streamAiService.generalChat(history, "请分析这个实验文档", model, handler);
            
        } catch (Exception e) {
            log.error("PDF文档分析失败: ", e);
            throw new RuntimeException("PDF文档分析失败: " + e.getMessage());
        }
    }

    /**
     * 创建包含文档的用户消息
     */
    private UserMessage createUserMessageWithDocument(UserDocument document) throws Exception {
        // 获取文档分析提示词 - 暂时使用简单的提示词
        String prompt = "请详细分析以下实验文档，重点关注电路设计、实验步骤、预期结果等方面：";
        
        if ("pdf".equals(document.getDocumentType())) {
            // 对于PDF文档，使用文档URL进行分析
            String documentUrl = minioService.getObjectUrl(document.getObjectPath());
            return UserMessage.from(prompt + "\n\n文档链接: " + documentUrl);
        } else if ("image".equals(document.getDocumentType())) {
            // 对于图片文档，使用视觉分析
            String documentUrl = minioService.getObjectUrl(document.getObjectPath());
            ImageContent imageContent = ImageContent.from(documentUrl);
            return UserMessage.from(prompt, imageContent);
        } else {
            // 其他类型文档暂时使用文本分析
            return UserMessage.from(prompt + "\n\n请分析文档: " + document.getTitle());
        }
    }

    @Override
    public void createOtherNodesOrUpdateNodeData(WorkContext workContext) throws JsonProcessingException {
        RStream<String, Object> redisStream = workContext.getRedisStream();
        Response<AiMessage> streamResponse = workContext.getStreamResponse();
        
        // 更新进度信息
        Map<String, Object> data = new ConcurrentHashMap<>();
        data.put("progress", "PDF电路分析完成");
        data.put("analysisResult", streamResponse.content().text());
        
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

    @Override
    public void updateStreamNode(WorkContext workContext) throws JsonProcessingException {
        // 使用默认的流节点更新逻辑
        super.updateStreamNode(workContext);
    }
}

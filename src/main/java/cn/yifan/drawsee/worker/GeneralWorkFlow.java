package cn.yifan.drawsee.worker;

import cn.yifan.drawsee.constant.NodeTitle;
import cn.yifan.drawsee.mapper.AiTaskMapper;
import cn.yifan.drawsee.mapper.ConversationMapper;
import cn.yifan.drawsee.mapper.NodeMapper;
import cn.yifan.drawsee.mapper.UserMapper;
import cn.yifan.drawsee.pojo.XYPosition;
import cn.yifan.drawsee.pojo.entity.Node;
import cn.yifan.drawsee.pojo.rabbit.AiTaskMessage;
import cn.yifan.drawsee.repository.KnowledgeRepository;
import cn.yifan.drawsee.service.base.AiService;
import cn.yifan.drawsee.service.base.StreamAiService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.StreamingResponseHandler;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Service;

import java.util.LinkedList;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * @FileName GeneralWorkFlow
 * @Description 处理通用对话任务的工作流
 * @Author yifan
 * @date 2025-03-09 13:32
 **/

@Slf4j
@Service
public class GeneralWorkFlow extends WorkFlow {

    public GeneralWorkFlow(
        UserMapper userMapper,
        AiService aiService,
        StreamAiService streamAiService,
        RedissonClient redissonClient,
        KnowledgeRepository knowledgeRepository,
        NodeMapper nodeMapper,
        ConversationMapper conversationMapper,
        AiTaskMapper aiTaskMapper,
        ObjectMapper objectMapper
    ) {
        super(userMapper, aiService, streamAiService, redissonClient, knowledgeRepository, nodeMapper, conversationMapper, aiTaskMapper, objectMapper);
    }
    
    /**
     * 覆盖父类方法，确保查询节点仅包含用户原始问题
     */
    @Override
    public Long createInitQueryNode(WorkContext workContext) throws JsonProcessingException {
        AiTaskMessage aiTaskMessage = workContext.getAiTaskMessage();

        // 创建用户查询节点，确保只包含原始问题
        Map<String, Object> queryNodeData = new ConcurrentHashMap<>();
        queryNodeData.put("title", NodeTitle.QUERY);
        queryNodeData.put("text", aiTaskMessage.getPrompt()); // 确保只包含用户的原始问题
        queryNodeData.put("mode", aiTaskMessage.getType());
        
        Node queryNode = new Node(
            "query", // 使用字符串而非常量，确保类型正确
            objectMapper.writeValueAsString(queryNodeData),
            objectMapper.writeValueAsString(XYPosition.origin()),
            aiTaskMessage.getParentId(),
            aiTaskMessage.getUserId(),
            aiTaskMessage.getConvId(),
            true
        );
        
        insertAndPublishNoneStreamNode(workContext, queryNode, queryNodeData);
        
        // 确保历史记录仅包含原始问题
        LinkedList<ChatMessage> history = workContext.getHistory();
        if (history == null) {
            history = new LinkedList<>();
            workContext.setHistory(history);
        }
        
        // 添加用户消息到历史记录
        history.add(new UserMessage(aiTaskMessage.getPrompt()));
        
        return queryNode.getId();
    }
    
    /**
     * 覆盖父类方法，为通用问答流程提供优化的实现
     * 不创建父角度节点，而是仅保留QUERY节点作为后续角度节点的父节点
     */
    @Override
    public void createInitNodes(WorkContext workContext) throws JsonProcessingException {
        // 只创建查询节点，不创建流式节点
        Long queryNodeId = createInitQueryNode(workContext);
        
        // 创建一个虚拟的流式节点用于后续处理，但不实际存储到数据库
        setupVirtualStreamNode(workContext, queryNodeId);
    }
    
    /**
     * 设置一个虚拟的流式节点，用于后续的回答角度节点创建
     * 该节点不会实际插入数据库，仅用于后续处理
     */
    private void setupVirtualStreamNode(WorkContext workContext, Long parentNodeId) throws JsonProcessingException {
        AiTaskMessage aiTaskMessage = workContext.getAiTaskMessage();
        
        // 创建流式节点数据，但不实际保存到数据库
        Map<String, Object> streamNodeData = new ConcurrentHashMap<>();
        streamNodeData.put("title", NodeTitle.ANSWER_POINT);
        streamNodeData.put("text", "");
        
        // 创建一个Node对象，但不实际插入数据库
        Node virtualNode = new Node(
            null,  // 不设置类型
            objectMapper.writeValueAsString(streamNodeData),
            objectMapper.writeValueAsString(XYPosition.origin()),
            parentNodeId,
            aiTaskMessage.getUserId(),
            aiTaskMessage.getConvId(),
            false // 标记为非活跃
        );
        
        // 为虚拟节点设置一个临时ID，但不使用查询节点的ID
        virtualNode.setId(-1L); // 使用一个明显的临时ID
        
        // 设置到WorkContext中，但不调用insertAndPublishStreamNode
        workContext.setStreamNode(virtualNode);
        workContext.setStreamNodeData(streamNodeData);
    }
    
    /**
     * 覆盖父类方法，为通用问答流程提供优化的实现
     * 不创建流式节点，直接使用查询节点作为父节点
     */
    @Override
    public void createInitStreamNode(WorkContext workContext, Long parentNodeId) throws JsonProcessingException {
        // 不创建流式节点，保持空实现
        // 实际节点创建在setupVirtualStreamNode方法中
    }
    
    /**
     * 覆盖父类方法，确保不将AI回答合并到查询节点中
     */
    @Override
    public void streamChat(WorkContext workContext, StreamingResponseHandler<AiMessage> handler) throws JsonProcessingException {
        AiTaskMessage aiTaskMessage = workContext.getAiTaskMessage();
        LinkedList<ChatMessage> history = workContext.getHistory();
        
        // 直接调用角度生成方法，不修改任何节点数据
        streamAiService.answerPointChat(history, aiTaskMessage.getPrompt(), aiTaskMessage.getModel(), handler);
    }
    
    /**
     * 覆盖父类方法，修改createAnswerPointNode的行为
     * 使角度节点直接挂在QUERY节点下
     */
    @Override
    protected void createAnswerPointNodes(WorkContext workContext) throws JsonProcessingException {
        Node streamNode = workContext.getStreamNode();
        dev.langchain4j.model.output.Response<AiMessage> streamResponse = workContext.getStreamResponse();
        AiTaskMessage aiTaskMessage = workContext.getAiTaskMessage();
        
        // 获取实际要挂载的父节点ID（查询节点ID）
        Long parentId = streamNode.getParentId();
        
        String responseText = streamResponse.content().text();
        
        try {
            // 尝试解析回答角度，与原方法类似，但修改角度节点的父节点
            processAnswerAngles(workContext, responseText, parentId);
        } catch (Exception e) {
            log.error("解析回答角度失败: {}", responseText, e);
        }
    }
    
    /**
     * 处理回答角度，将角度节点直接挂到查询节点下
     */
    private void processAnswerAngles(WorkContext workContext, String responseText, Long parentId) throws JsonProcessingException {
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
                    createAnswerPointNode(workContext, parentId, aiTaskMessage, currentTitle, currentDescription);
                    currentTitle = null;
                    currentDescription = null;
                }
                continue;
            }
            
            // 匹配"角度X：[标题]"格式
            if (line.matches("^角度\\d+：.+")) {
                // 如果已有标题和描述，先创建之前的节点
                if (currentTitle != null && currentDescription != null) {
                    createAnswerPointNode(workContext, parentId, aiTaskMessage, currentTitle, currentDescription);
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
            createAnswerPointNode(workContext, parentId, aiTaskMessage, currentTitle, currentDescription);
        }
    }
    
    /**
     * 创建答案角度节点，直接挂在查询节点下
     */
    private void createAnswerPointNode(WorkContext workContext, Long parentId, AiTaskMessage aiTaskMessage, 
                                      String title, String description) throws JsonProcessingException {
        Map<String, Object> answerPointNodeData = new ConcurrentHashMap<>();
        answerPointNodeData.put("title", title);
        answerPointNodeData.put("text", description);
        answerPointNodeData.put("subtype", "answer-point");
        
        Node answerPointNode = new Node(
            "answer-point",
            objectMapper.writeValueAsString(answerPointNodeData),
            objectMapper.writeValueAsString(XYPosition.origin()),
            parentId,
            aiTaskMessage.getUserId(),
            aiTaskMessage.getConvId(),
            true
        );
        
        try {
            // 使用传入的workContext
            insertAndPublishNoneStreamNode(workContext, answerPointNode, answerPointNodeData);
        } catch (Exception e) {
            log.error("创建回答角度节点失败: {}", e.getMessage(), e);
        }
    }

    @Override
    public void updateStreamNode(WorkContext workContext) throws JsonProcessingException {
        // 由于我们使用的是虚拟节点，不需要更新节点内容
        // 覆盖父类方法，防止对QUERY节点进行更新
        // 不调用super.updateStreamNode(workContext);
        
        // 虚拟节点的处理已在createAnswerPointNodes中完成
        log.info("跳过虚拟流节点更新");
    }
}

package cn.yifan.drawsee.worker;

import cn.yifan.drawsee.constant.AiTaskMessageType;
import cn.yifan.drawsee.constant.NodeSubType;
import cn.yifan.drawsee.constant.NodeTitle;
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
import cn.yifan.drawsee.service.base.AiService;
import cn.yifan.drawsee.service.base.PromptService;
import cn.yifan.drawsee.service.base.StreamAiService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.model.StreamingResponseHandler;
import dev.langchain4j.model.output.Response;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RStream;
import org.redisson.api.RedissonClient;
import org.redisson.api.stream.StreamAddArgs;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

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
    public void createInitNodes(WorkContext workContext) throws JsonProcessingException {
        AiTaskMessage aiTaskMessage = workContext.getAiTaskMessage();
        CircuitDesign circuitDesign = objectMapper.readValue(aiTaskMessage.getPrompt(), CircuitDesign.class);
        
        // 创建电路画布节点（替代之前的查询节点）
        Map<String, Object> canvasNodeData = new ConcurrentHashMap<>();
        canvasNodeData.put("title", NodeTitle.CIRCUIT_CANVAS);
        canvasNodeData.put("text", "电路分析请求");
        canvasNodeData.put("circuitDesign", circuitDesign);
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
        
        // 创建新的电路分析节点，后续所有内容都在该节点内流式生成
        Map<String, Object> analyzeNodeData = new ConcurrentHashMap<>();
        analyzeNodeData.put("title", NodeTitle.CIRCUIT_ANALYSIS);
        analyzeNodeData.put("text", "");
        analyzeNodeData.put("subtype", NodeSubType.CIRCUIT_ANALYZE);
        analyzeNodeData.put("contextTitle", resolveDesignTitle(circuitDesign));
        analyzeNodeData.put("followUps", new ArrayList<>());
        analyzeNodeData.put("isGenerated", false);
        
        Node analyzeNode = new Node(
            NodeType.CIRCUIT_ANALYZE,
            objectMapper.writeValueAsString(analyzeNodeData),
            objectMapper.writeValueAsString(new XYPosition(420, 0)),
            canvasNode.getId(),
            aiTaskMessage.getUserId(),
            aiTaskMessage.getConvId(),
            true
        );
        insertAndPublishStreamNode(workContext, analyzeNode, analyzeNodeData);
        log.info("[CircuitAnalysis] 初始化节点完成, canvasNodeId={}, analyzeNodeId={}", canvasNode.getId(), analyzeNode.getId());
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
        String analysisPrompt = promptService.getCircuitWarmupPrompt(circuitDesign, spiceNetlist);
        
        // 调用AI进行分析
        streamAiService.circuitAnalysisChat(history, analysisPrompt, model, handler);
    }

    @Override
    public void createOtherNodesOrUpdateNodeData(WorkContext workContext) throws JsonProcessingException {
        RStream<String, Object> redisStream = workContext.getRedisStream();
        Node streamNode = workContext.getStreamNode();
        Map<String, Object> streamNodeData = workContext.getStreamNodeData();
        Response<AiMessage> streamResponse = workContext.getStreamResponse();
        
        String responseText = streamResponse.content().text();
        CircuitWarmupResult warmupResult = parseWarmupResponse(responseText);
        String displayText = buildDisplayText(warmupResult);
        List<Map<String, Object>> followUpPayload = warmupResult.getFollowUps()
            .stream()
            .map(FollowUpSuggestion::toPayload)
            .collect(Collectors.toList());
        
        streamNodeData.put("text", displayText);
        streamNodeData.put("followUps", followUpPayload);
        streamNodeData.put("isGenerated", true);
        streamNodeData.put("isDone", true);
        log.info("[CircuitAnalysis] 解析完成, nodeId={}, warmupLength={}, followUps={}", 
            streamNode.getId(), 
            displayText.length(),
            followUpPayload.size());
        
        Map<String, Object> dataPayload = new ConcurrentHashMap<>();
        dataPayload.put("nodeId", streamNode.getId());
        dataPayload.put("text", displayText);
        dataPayload.put("followUps", followUpPayload);
        dataPayload.put("isGenerated", true);
        dataPayload.put("isDone", true);
        redisStream.add(StreamAddArgs.entries(
            "type", AiTaskMessageType.DATA,
            "data", dataPayload
        ));
    }

    @Override
    public void updateStreamNode(WorkContext workContext) throws JsonProcessingException {
        Node streamNode = workContext.getStreamNode();
        Map<String, Object> streamNodeData = workContext.getStreamNodeData();
        streamNode.setIsDeleted(false);
        streamNode.setData(objectMapper.writeValueAsString(streamNodeData));
        workContext.getNodesToUpdate().add(streamNode);
    }
    
    private CircuitWarmupResult parseWarmupResponse(String responseText) {
        String normalized = Optional.ofNullable(responseText).orElse("").replace("\r", "").trim();
        String warmupSection = normalized;
        String followUpSection = "";
        
        final String warmupFlag = "【预热导语】";
        final String followFlag = "【追问预判】";
        int warmupIndex = normalized.indexOf(warmupFlag);
        if (warmupIndex >= 0) {
            int followIndex = normalized.indexOf(followFlag, warmupIndex + warmupFlag.length());
            if (followIndex >= 0) {
                warmupSection = normalized.substring(warmupIndex + warmupFlag.length(), followIndex).trim();
                followUpSection = normalized.substring(followIndex + followFlag.length()).trim();
            } else {
                warmupSection = normalized.substring(warmupIndex + warmupFlag.length()).trim();
            }
        }
        
        List<FollowUpSuggestion> followUps = followUpSection.isEmpty()
            ? Collections.emptyList()
            : parseFollowUpSection(followUpSection);
        
        String resolvedWarmup = warmupSection.isEmpty() ? normalized : warmupSection;
        return new CircuitWarmupResult(resolvedWarmup, followUps);
    }
    
    private List<FollowUpSuggestion> parseFollowUpSection(String followUpSection) {
        List<String> entries = splitFollowUpEntries(followUpSection);
        List<FollowUpSuggestion> followUps = new ArrayList<>();
        for (int i = 0; i < entries.size(); i++) {
            FollowUpSuggestion suggestion = buildFollowUpSuggestion(entries.get(i), i + 1);
            if (suggestion != null) {
                followUps.add(suggestion);
            }
        }
        return followUps;
    }
    
    private List<String> splitFollowUpEntries(String section) {
        String[] lines = section.split("\n");
        List<String> entries = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        Pattern entryPattern = Pattern.compile("^\\d+[\\.|．、)]\\s*.*");
        
        for (String rawLine : lines) {
            String line = rawLine.trim();
            if (line.isEmpty()) {
                continue;
            }
            Matcher matcher = entryPattern.matcher(line);
            if (matcher.find()) {
                if (!current.isEmpty()) {
                    entries.add(current.toString().trim());
                }
                current.setLength(0);
                current.append(line);
            } else if (!current.isEmpty()) {
                current.append(' ').append(line);
            }
        }
        if (!current.isEmpty()) {
            entries.add(current.toString().trim());
        }
        if (entries.isEmpty() && !section.isBlank()) {
            entries.add(section.trim());
        }
        return entries;
    }
    
    private FollowUpSuggestion buildFollowUpSuggestion(String entry, int order) {
        if (entry == null || entry.isBlank()) {
            return null;
        }
        String normalized = entry.replace("\r", "").trim();
        normalized = normalized.replace('｜', '|');
        normalized = normalized.replace('：', ':');
        normalized = normalized.replaceAll("^\\d+[\\.|．、)]\\s*", "");
        
        String title = null;
        int titleStart = normalized.indexOf('[');
        int titleEnd = normalized.indexOf(']');
        if (titleStart >= 0 && titleEnd > titleStart) {
            title = normalized.substring(titleStart + 1, titleEnd).trim();
            normalized = normalized.substring(titleEnd + 1).trim();
        }
        if (normalized.startsWith("|")) {
            normalized = normalized.substring(1);
        }
        
        String hint = null;
        String followUp = null;
        String intent = null;
        Double confidence = null;
        
        String[] segments = normalized.split("\\|");
        for (String rawSegment : segments) {
            String segment = rawSegment.trim();
            if (segment.isEmpty()) {
                continue;
            }
            String lowered = segment.toLowerCase();
            if (segment.startsWith("洞见") || segment.startsWith("洞察")) {
                hint = stripLabel(segment, segment.startsWith("洞见") ? "洞见" : "洞察");
            } else if (segment.startsWith("追问")) {
                followUp = stripLabel(segment, "追问");
            } else if (segment.startsWith("意图")) {
                intent = stripLabel(segment, "意图");
            } else if (segment.startsWith("信心") || lowered.startsWith("confidence")) {
                String confText = stripLabel(segment, segment.startsWith("信心") ? "信心" : "confidence");
                confidence = parseConfidence(confText);
            } else if (title == null && segment.startsWith("[")) {
                int end = segment.indexOf(']');
                if (end > 0) {
                    title = segment.substring(1, end).trim();
                }
            }
        }
        
        if (followUp == null && hint != null) {
            followUp = hint;
        }
        if (title == null || title.isBlank()) {
            title = "追问 " + order;
        }
        return new FollowUpSuggestion(title, hint, followUp, intent, confidence);
    }
    
    private String stripLabel(String segment, String label) {
        if (segment == null) {
            return null;
        }
        String normalized = segment.replace("：", ":").trim();
        String prefix = label + ":";
        if (normalized.startsWith(prefix)) {
            return normalized.substring(prefix.length()).trim();
        }
        if (normalized.toLowerCase().startsWith((label + ":").toLowerCase())) {
            return normalized.substring(label.length() + 1).trim();
        }
        return normalized.replace(label, "").trim();
    }
    
    private Double parseConfidence(String text) {
        if (text == null || text.isBlank()) {
            return null;
        }
        String normalized = text.replace("%", "").trim();
        try {
            double value = Double.parseDouble(normalized);
            if (value > 1) {
                value = value / 100.0;
            }
            return Math.max(0, Math.min(value, 1));
        } catch (NumberFormatException e) {
            log.warn("解析追问信心度失败: {}", text);
            return null;
        }
    }
    
    private String buildDisplayText(CircuitWarmupResult result) {
        StringBuilder builder = new StringBuilder();
        if (hasText(result.getWarmupText())) {
            builder.append("### 预热导语\n");
            builder.append(result.getWarmupText().trim()).append("\n\n");
        }
        if (!result.getFollowUps().isEmpty()) {
            builder.append("### 追问方向概览\n");
            for (FollowUpSuggestion followUp : result.getFollowUps()) {
                String hint = hasText(followUp.getHint()) ? followUp.getHint() : "可以继续追问这一方向获取更深入的洞察。";
                builder.append("- **")
                    .append(followUp.getTitle())
                    .append("**：")
                    .append(hint);
                if (hasText(followUp.getIntent())) {
                    builder.append(" _(意图: ").append(followUp.getIntent()).append(")_");
                }
                builder.append("\n");
            }
        }
        String content = builder.toString().trim();
        return content.isEmpty() ? result.getWarmupText() : content;
    }
    
    private boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }
    
    private String resolveDesignTitle(CircuitDesign circuitDesign) {
        return Optional.ofNullable(circuitDesign)
            .map(CircuitDesign::getMetadata)
            .map(CircuitDesign.CircuitMetadata::getTitle)
            .filter(this::hasText)
            .orElse("电路分析");
    }
    
    @Getter
    private static class CircuitWarmupResult {
        private final String warmupText;
        private final List<FollowUpSuggestion> followUps;
        
        CircuitWarmupResult(String warmupText, List<FollowUpSuggestion> followUps) {
            this.warmupText = warmupText;
            this.followUps = followUps;
        }

    }
    
    @Getter
    private static class FollowUpSuggestion {
        private final String title;
        private final String hint;
        private final String followUp;
        private final String intent;
        private final Double confidence;
        
        FollowUpSuggestion(String title, String hint, String followUp, String intent, Double confidence) {
            this.title = title;
            this.hint = hint;
            this.followUp = followUp;
            this.intent = intent;
            this.confidence = confidence;
        }

        public Map<String, Object> toPayload() {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("title", title);
            if (hint != null) {
                map.put("hint", hint);
            }
            if (followUp != null) {
                map.put("followUp", followUp);
            }
            if (intent != null) {
                map.put("intent", intent);
            }
            if (confidence != null) {
                map.put("confidence", confidence);
            }
            return map;
        }
    }
}

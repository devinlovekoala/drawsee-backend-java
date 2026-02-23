package cn.yifan.drawsee.service.base;

import cn.yifan.drawsee.parser.CircuitImageNetlistParser;
import cn.yifan.drawsee.pojo.entity.CircuitDesign;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import dev.langchain4j.data.message.*;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.output.Response;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

/**
 * @FileName AiService
 * @Description
 * @Author yifan
 * @date 2025-03-09 23:29
 **/

@Service
@Slf4j
public class AiService {

    @Autowired
    private PromptService promptService;
    @Autowired
    private ChatModel deepseekV3ChatLanguageModel;
    @Autowired
    private ChatModel qwenVisionChatLanguageModel;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private CircuitImageNetlistParser circuitImageNetlistParser;

    public String getConvTitle(String question) {
        String prompt = promptService.getConvTitlePrompt(question);
        return deepseekV3ChatLanguageModel.chat(prompt);
    }

    public List<String> getRelatedKnowledgePoints(List<String> knowledgePoints, String question, AtomicLong tokens) {
        String prompt = promptService.getRelatedKnowledgePointsPrompt(knowledgePoints, question);
        Response<AiMessage> response = toResponse(deepseekV3ChatLanguageModel.chat(UserMessage.from(prompt)));
        tokens.addAndGet(response.tokenUsage().totalTokenCount());
        TypeReference<List<String>> typeReference = new TypeReference<>() {};
        try {
            return objectMapper.readValue(response.content().text(), typeReference);
        } catch (JsonProcessingException e) {
            log.error("解析知识点列表失败，AI输出为：{}", response.content().text(), e);
            //return getRelatedKnowledgePoints(knowledgePoints, question);
            return null;
        }
    }

    /**
     * 使用Qwen Vision识别图片中的文本
     * @param imageUrl 图片的URL地址
     * @return 识别出的文本内容
     */
    public String recognizeTextFromImage(String imageUrl) {
        String imageTextPrompt = promptService.getImageTextPrompt();
        UserMessage userMessage = UserMessage.from(
                TextContent.from(imageTextPrompt),
                ImageContent.from(imageUrl)
        );
        // 使用Qwen Vision模型进行识别
        Response<AiMessage> response = toResponse(qwenVisionChatLanguageModel.chat(userMessage));
        return response.content().text();
    }

    /**
     * 使用视觉模型识别电路图并转换为电路画布设计
     * @param imageUrl MinIO生成的图片地址
     * @return CircuitDesign 对象
     */
    public CircuitDesign recognizeCircuitDesignFromImage(String imageUrl) {
        String prompt = promptService.getCircuitImageDesignPrompt();
        UserMessage userMessage = UserMessage.from(
            TextContent.from(prompt),
            ImageContent.from(imageUrl)
        );
        Response<AiMessage> response = toResponse(qwenVisionChatLanguageModel.chat(userMessage));
        String raw = response.content().text();
        try {
            return circuitImageNetlistParser.parse(raw);
        } catch (Exception e) {
            log.error("识别电路图失败，模型输出: {}", raw, e);
            throw new RuntimeException("解析电路网表失败", e);
        }
    }

    public List<String> getSolveWays(String question) throws JsonProcessingException {
        String prompt = promptService.getSolveWaysPrompt(question);
        String result = deepseekV3ChatLanguageModel.chat(prompt);
        TypeReference<List<String>> typeReference = new TypeReference<>() {};
        return objectMapper.readValue(result, typeReference);
    }

    public List<String> getPlannerSplit(LinkedList<ChatMessage> history, AtomicLong tokens) throws JsonProcessingException {
        String prompt = promptService.getPlannerSplitPrompt();
        history.addLast(new UserMessage(prompt));
        Response<AiMessage> response = toResponse(deepseekV3ChatLanguageModel.chat(history));
        tokens.addAndGet(response.tokenUsage().totalTokenCount());
        TypeReference<List<String>> typeReference = new TypeReference<>() {};
        return objectMapper.readValue(response.content().text(), typeReference);
    }

    private Response<AiMessage> toResponse(ChatResponse response) {
        return Response.from(
            response.aiMessage(),
            response.tokenUsage(),
            response.finishReason()
        );
    }

}

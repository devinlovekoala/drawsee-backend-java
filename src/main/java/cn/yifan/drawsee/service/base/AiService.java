package cn.yifan.drawsee.service.base;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import dev.langchain4j.data.message.*;
import dev.langchain4j.model.chat.ChatLanguageModel;
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
    private ChatLanguageModel doubaoChatLanguageModel;
    @Autowired
    private ChatLanguageModel deepseekV3ChatLanguageModel;
    @Autowired
    private ChatLanguageModel doubaoVisionChatLanguageModel;
    @Autowired
    private ObjectMapper objectMapper;

    public String getConvTitle(String question) {
        String prompt = promptService.getConvTitlePrompt(question);
        return doubaoChatLanguageModel.chat(prompt);
    }

    public List<String> getRelatedKnowledgePoints(List<String> knowledgePoints, String question, AtomicLong tokens) {
        String prompt = promptService.getRelatedKnowledgePointsPrompt(knowledgePoints, question);
        Response<AiMessage> response = doubaoChatLanguageModel.generate(UserMessage.from(prompt));
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
     * 使用doubaoVision识别图片中的文本
     * @param imageUrl 图片的URL地址
     * @return 识别出的文本内容
     */
    public String recognizeTextFromImage(String imageUrl) {
        String imageTextPrompt = promptService.getImageTextPrompt();
        UserMessage userMessage = UserMessage.from(
            TextContent.from(imageTextPrompt),
            ImageContent.from(imageUrl)
        );
        // 使用doubaoVision模型进行识别
        Response<AiMessage> response = doubaoVisionChatLanguageModel.generate(userMessage);
        return response.content().text();
    }

    public List<String> getSolveWays(String question) throws JsonProcessingException {
        String prompt = promptService.getSolveWaysPrompt(question);
        String result = doubaoChatLanguageModel.chat(prompt);
        TypeReference<List<String>> typeReference = new TypeReference<>() {};
        return objectMapper.readValue(result, typeReference);
    }

    public List<String> getPlannerSplit(LinkedList<ChatMessage> history, AtomicLong tokens) throws JsonProcessingException {
        String prompt = promptService.getPlannerSplitPrompt();
        history.addLast(new UserMessage(prompt));
        Response<AiMessage> response = doubaoChatLanguageModel.generate(history);
        tokens.addAndGet(response.tokenUsage().totalTokenCount());
        TypeReference<List<String>> typeReference = new TypeReference<>() {};
        return objectMapper.readValue(response.content().text(), typeReference);
    }

}

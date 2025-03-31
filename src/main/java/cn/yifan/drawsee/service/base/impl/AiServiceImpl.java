package cn.yifan.drawsee.service.base.impl;

import cn.yifan.drawsee.service.base.AiService;
import cn.yifan.drawsee.service.base.PromptService;
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
 * AI服务实现类
 */
@Service
@Slf4j
public class AiServiceImpl implements AiService {

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

    @Override
    public String getConvTitle(String question) {
        String prompt = promptService.getConvTitlePrompt(question);
        return doubaoChatLanguageModel.chat(prompt);
    }

    @Override
    public List<String> getRelatedKnowledgePoints(List<String> knowledgePoints, String question, AtomicLong tokens) {
        String prompt = promptService.getRelatedKnowledgePointsPrompt(knowledgePoints, question);
        Response<AiMessage> response = doubaoChatLanguageModel.generate(UserMessage.from(prompt));
        tokens.addAndGet(response.tokenUsage().totalTokenCount());
        TypeReference<List<String>> typeReference = new TypeReference<>() {};
        try {
            return objectMapper.readValue(response.content().text(), typeReference);
        } catch (JsonProcessingException e) {
            log.error("解析知识点列表失败，AI输出为：{}", response.content().text(), e);
            return null;
        }
    }

    @Override
    public String recognizeTextFromImage(String imageUrl) {
        String imageTextPrompt = promptService.getImageTextPrompt();
        UserMessage userMessage = UserMessage.from(
            TextContent.from(imageTextPrompt),
            ImageContent.from(imageUrl)
        );
        Response<AiMessage> response = doubaoVisionChatLanguageModel.generate(userMessage);
        return response.content().text();
    }

    @Override
    public List<String> getSolveWays(String question) throws JsonProcessingException {
        String prompt = promptService.getSolveWaysPrompt(question);
        String result = doubaoChatLanguageModel.chat(prompt);
        TypeReference<List<String>> typeReference = new TypeReference<>() {};
        return objectMapper.readValue(result, typeReference);
    }

    @Override
    public List<String> getPlannerSplit(LinkedList<ChatMessage> history, AtomicLong tokens) throws JsonProcessingException {
        String prompt = promptService.getPlannerSplitPrompt();
        history.addLast(new UserMessage(prompt));
        Response<AiMessage> response = doubaoChatLanguageModel.generate(history);
        tokens.addAndGet(response.tokenUsage().totalTokenCount());
        TypeReference<List<String>> typeReference = new TypeReference<>() {};
        return objectMapper.readValue(response.content().text(), typeReference);
    }
} 
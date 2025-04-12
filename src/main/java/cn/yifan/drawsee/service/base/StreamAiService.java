package cn.yifan.drawsee.service.base;

import cn.yifan.drawsee.constant.AiModel;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.StreamingResponseHandler;
import dev.langchain4j.model.chat.StreamingChatLanguageModel;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.LinkedList;
import java.util.List;

/**
 * @FileName StreamAiService
 * @Description 
 * @Author yifan
 * @date 2025-03-09 23:38
 **/

@Service
@Slf4j
public class StreamAiService {

    @Autowired
    private PromptService promptService;
    @Autowired
    private StreamingChatLanguageModel doubaoStreamingChatLanguageModel;
    @Autowired
    private StreamingChatLanguageModel deepseekV3StreamingChatLanguageModel;

    public void generalChat(List<ChatMessage> history, String question, String model, StreamingResponseHandler<AiMessage> handler) {
        LinkedList<ChatMessage> messages = new LinkedList<>(history);
        String systemPrompt = promptService.getGeneralChatPrompt();
        messages.addFirst(new SystemMessage(systemPrompt));
        messages.addLast(new UserMessage(question));

        if (model.equals(AiModel.DEEPSEEKV3)) {
            log.info("使用DeepSeekV3模型");
            deepseekV3StreamingChatLanguageModel.generate(messages, handler);
        } else {
            log.info("使用豆包模型");
            doubaoStreamingChatLanguageModel.generate(messages, handler);
        }
    }

    public void knowledgeDetailChat(List<ChatMessage> history, String knowledgePoint, String model, StreamingResponseHandler<AiMessage> handler) {
        LinkedList<ChatMessage> messages = new LinkedList<>(history);
        String prompt = promptService.getKnowledgeDetailChatPrompt(knowledgePoint);
        messages.add(new UserMessage(prompt));
        
        if (model.equals(AiModel.DEEPSEEKV3)) {
            log.info("使用DeepSeekV3模型");
            deepseekV3StreamingChatLanguageModel.generate(messages, handler);
        } else {
            log.info("使用豆包模型");
            doubaoStreamingChatLanguageModel.generate(messages, handler);
        }
    }

    public void animationChat(List<ChatMessage> history, String question, String model, StreamingResponseHandler<AiMessage> handler) {
        LinkedList<ChatMessage> messages = new LinkedList<>(history);
        String prompt = promptService.getAnimationChatPrompt(question);
        messages.addLast(new UserMessage(prompt));
        
        if (model.equals(AiModel.DEEPSEEKV3)) {
            log.info("使用DeepSeekV3模型");
            deepseekV3StreamingChatLanguageModel.generate(messages, handler);
        } else {
            log.info("使用豆包模型");
            doubaoStreamingChatLanguageModel.generate(messages, handler);
        }
    }

    public void solverFirstChat(List<ChatMessage> history, String question, String method, String model, StreamingResponseHandler<AiMessage> handler) {
        LinkedList<ChatMessage> messages = new LinkedList<>(history);
        String prompt = promptService.getSolverFirstChatPrompt(question, method);
        messages.addLast(new UserMessage(prompt));
        
        if (model.equals(AiModel.DEEPSEEKV3)) {
            log.info("使用DeepSeekV3模型");
            deepseekV3StreamingChatLanguageModel.generate(messages, handler);
        } else {
            log.info("使用豆包模型");
            doubaoStreamingChatLanguageModel.generate(messages, handler);
        }
    }

    public void solverContinueChat(List<ChatMessage> history, String model, StreamingResponseHandler<AiMessage> handler) {
        LinkedList<ChatMessage> messages = new LinkedList<>(history);
        String prompt = promptService.getSolverContinueChatPrompt();
        messages.addLast(new UserMessage(prompt));
        
        if (model.equals(AiModel.DEEPSEEKV3)) {
            log.info("使用DeepSeekV3模型");
            deepseekV3StreamingChatLanguageModel.generate(messages, handler);
        } else {
            log.info("使用豆包模型");
            doubaoStreamingChatLanguageModel.generate(messages, handler);
        }
    }

    public void solverSummaryChat(List<ChatMessage> history, String model, StreamingResponseHandler<AiMessage> handler) {
        LinkedList<ChatMessage> messages = new LinkedList<>(history);
        String prompt = promptService.getSolverSummaryChatPrompt();
        messages.addLast(new UserMessage(prompt));
        
        if (model.equals(AiModel.DEEPSEEKV3)) {
            log.info("使用DeepSeekV3模型");
            deepseekV3StreamingChatLanguageModel.generate(messages, handler);
        } else {
            log.info("使用豆包模型");
            doubaoStreamingChatLanguageModel.generate(messages, handler);
        }
    }

    public void plannerFirstChat(List<ChatMessage> history, String model, StreamingResponseHandler<AiMessage> handler) {
        LinkedList<ChatMessage> messages = new LinkedList<>(history);
        String prompt = promptService.getPlannerFirstPrompt();
        messages.addLast(new UserMessage(prompt));
        
        if (model.equals(AiModel.DEEPSEEKV3)) {
            log.info("使用DeepSeekV3模型");
            deepseekV3StreamingChatLanguageModel.generate(messages, handler);
        } else {
            log.info("使用豆包模型");
            doubaoStreamingChatLanguageModel.generate(messages, handler);
        }
    }

    public void htmlMakerChat(List<ChatMessage> history, String question, String model, StreamingResponseHandler<AiMessage> handler) {
        LinkedList<ChatMessage> messages = new LinkedList<>(history);
        String prompt = promptService.getHtmlMakerChatPrompt(question);
        messages.addLast(new UserMessage(prompt));
        
        if (model.equals(AiModel.DEEPSEEKV3)) {
            log.info("使用DeepSeekV3模型");
            deepseekV3StreamingChatLanguageModel.generate(messages, handler);
        } else {
            log.info("使用豆包模型");
            doubaoStreamingChatLanguageModel.generate(messages, handler);
        }
    }

    public void circuitAnalysisChat(LinkedList<ChatMessage> history, String prompt, String model, StreamingResponseHandler<AiMessage> handler) {
        LinkedList<ChatMessage> messages = new LinkedList<>(history);
        messages.add(new UserMessage(prompt));
        
        if (model.equals(AiModel.DEEPSEEKV3)) {
            log.info("使用DeepSeekV3模型");
            deepseekV3StreamingChatLanguageModel.generate(messages, handler);
        } else {
            log.info("使用豆包模型");
            doubaoStreamingChatLanguageModel.generate(messages, handler);
        }
    }
}

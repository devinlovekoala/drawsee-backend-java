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
import dev.langchain4j.data.message.ImageContent;

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
    @Autowired
    private StreamingChatLanguageModel doubaoVisionStreamingChatLanguageModel;

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

    /**
     * 生成回答角度
     * @param history 历史消息
     * @param question 用户问题
     * @param model 使用的AI模型
     * @param handler 流式处理器
     */
    public void answerPointChat(List<ChatMessage> history, String question, String model, StreamingResponseHandler<AiMessage> handler) {
        LinkedList<ChatMessage> messages = new LinkedList<>(history);
        String prompt = promptService.getAnswerPointPrompt(question);
        messages.add(new UserMessage(prompt));
        
        if (model.equals(AiModel.DEEPSEEKV3)) {
            log.info("使用DeepSeekV3模型生成回答角度");
            deepseekV3StreamingChatLanguageModel.generate(messages, handler);
        } else {
            log.info("使用豆包模型生成回答角度");
            doubaoStreamingChatLanguageModel.generate(messages, handler);
        }
    }

    /**
     * 根据指定角度生成详细回答
     * 该方法由GeneralDetailWorkFlow调用，其中角度信息从父节点(ANSWER_POINT)中获取
     * 原始问题通过findOriginalQuestion方法向上回溯QUERY节点获取
     * 
     * @param history 历史消息
     * @param question 原始问题
     * @param angle 回答角度（从父节点获取）
     * @param model 使用的AI模型
     * @param handler 流式处理器
     */
    public void answerDetailChat(List<ChatMessage> history, String question, String angle, String model, StreamingResponseHandler<AiMessage> handler) {
        LinkedList<ChatMessage> messages = new LinkedList<>(history);
        String prompt = promptService.getAnswerDetailPrompt(question, angle);
        messages.add(new UserMessage(prompt));
        
        if (model.equals(AiModel.DEEPSEEKV3)) {
            log.info("使用DeepSeekV3模型生成详细回答，角度: {}", angle);
            deepseekV3StreamingChatLanguageModel.generate(messages, handler);
        } else {
            log.info("使用豆包模型生成详细回答，角度: {}", angle);
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

    public void visionChat(List<ChatMessage> history, List<String> imageUrls, String instruction, String model, StreamingResponseHandler<AiMessage> handler) {
		LinkedList<ChatMessage> messages = new LinkedList<>(history);
		String systemPrompt = promptService.getDocumentAnalysisVisionPrompt();
		messages.addFirst(new SystemMessage(systemPrompt));

		int maxImages = Math.min(8, imageUrls.size());
		List<ImageContent> imageContents = new LinkedList<>();
		for (int j = 0; j < maxImages; j++) {
			imageContents.add(ImageContent.from(imageUrls.get(j)));
		}
		messages.addLast(UserMessage.from(instruction, imageContents.toArray(new ImageContent[0])));

		if (AiModel.DOUBAOVISION.equals(model)) {
			log.info("使用豆包Vision模型，图片数: {}", maxImages);
			doubaoVisionStreamingChatLanguageModel.generate(messages, handler);
		} else {
			log.info("当前模型不支持多模态，退化为文本提示，模型: {}", model);
			deepseekV3StreamingChatLanguageModel.generate(messages, handler);
		}
	}
}

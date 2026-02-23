package cn.yifan.drawsee.service.base;

import cn.yifan.drawsee.constant.AiModel;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.StreamingResponseHandler;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler;
import dev.langchain4j.model.output.Response;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.LinkedList;
import java.util.List;
import dev.langchain4j.data.message.ImageContent;
import dev.langchain4j.service.AiServices;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;

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
    private StreamingChatModel qwenStreamingChatLanguageModel;
    @Autowired
    private StreamingChatModel deepseekV3StreamingChatLanguageModel;
    @Autowired
    private StreamingChatModel qwenVisionStreamingChatLanguageModel;

    public void generalChat(List<ChatMessage> history, String question, String model, StreamingResponseHandler<AiMessage> handler) {
        LinkedList<ChatMessage> messages = new LinkedList<>(history);
        String systemPrompt = promptService.getGeneralChatPrompt();
        messages.addFirst(new SystemMessage(systemPrompt));
        messages.addLast(new UserMessage(question));

        resolveTextModel(model).chat(messages, buildStreamingHandler(handler));
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
        
        resolveTextModel(model).chat(messages, buildStreamingHandler(handler));
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
        
        resolveTextModel(model).chat(messages, buildStreamingHandler(handler));
    }

    public void knowledgeDetailChat(List<ChatMessage> history, String knowledgePoint, String model, StreamingResponseHandler<AiMessage> handler) {
        LinkedList<ChatMessage> messages = new LinkedList<>(history);
        String prompt = promptService.getKnowledgeDetailChatPrompt(knowledgePoint);
        messages.add(new UserMessage(prompt));
        
        resolveTextModel(model).chat(messages, buildStreamingHandler(handler));
    }

    public void animationChat(List<ChatMessage> history, String question, String model, StreamingResponseHandler<AiMessage> handler) {
        LinkedList<ChatMessage> messages = new LinkedList<>(history);
        String prompt = promptService.getAnimationChatPrompt(question);
        messages.addLast(new UserMessage(prompt));
        
        resolveTextModel(model).chat(messages, buildStreamingHandler(handler));
    }

    public void solverFirstChat(List<ChatMessage> history, String question, String method, String model, StreamingResponseHandler<AiMessage> handler) {
        LinkedList<ChatMessage> messages = new LinkedList<>(history);
        String prompt = promptService.getSolverFirstChatPrompt(question, method);
        messages.addLast(new UserMessage(prompt));
        
        resolveTextModel(model).chat(messages, buildStreamingHandler(handler));
    }

    public void solverContinueChat(List<ChatMessage> history, String model, StreamingResponseHandler<AiMessage> handler) {
        LinkedList<ChatMessage> messages = new LinkedList<>(history);
        String prompt = promptService.getSolverContinueChatPrompt();
        messages.addLast(new UserMessage(prompt));
        
        resolveTextModel(model).chat(messages, buildStreamingHandler(handler));
    }

    public void solverSummaryChat(List<ChatMessage> history, String model, StreamingResponseHandler<AiMessage> handler) {
        LinkedList<ChatMessage> messages = new LinkedList<>(history);
        String prompt = promptService.getSolverSummaryChatPrompt();
        messages.addLast(new UserMessage(prompt));
        
        resolveTextModel(model).chat(messages, buildStreamingHandler(handler));
    }

    public void plannerFirstChat(List<ChatMessage> history, String model, StreamingResponseHandler<AiMessage> handler) {
        LinkedList<ChatMessage> messages = new LinkedList<>(history);
        String prompt = promptService.getPlannerFirstPrompt();
        messages.addLast(new UserMessage(prompt));
        
        resolveTextModel(model).chat(messages, buildStreamingHandler(handler));
    }

    public void htmlMakerChat(List<ChatMessage> history, String question, String model, StreamingResponseHandler<AiMessage> handler) {
        LinkedList<ChatMessage> messages = new LinkedList<>(history);
        String prompt = promptService.getHtmlMakerChatPrompt(question);
        messages.addLast(new UserMessage(prompt));
        
        resolveTextModel(model).chat(messages, buildStreamingHandler(handler));
    }

    public void circuitAnalysisChat(LinkedList<ChatMessage> history, String prompt, String model, StreamingResponseHandler<AiMessage> handler) {
        LinkedList<ChatMessage> messages = new LinkedList<>(history);
        messages.add(new UserMessage(prompt));

        resolveTextModel(model).chat(messages, buildStreamingHandler(handler));
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

        if (!AiModel.QWENVISION.equals(model)) {
            throw new IllegalArgumentException("未配置可用的视觉模型: " + model);
        }
        log.info("使用千问Vision模型，图片数: {}", maxImages);
        qwenVisionStreamingChatLanguageModel.chat(messages, buildStreamingHandler(handler));
	}

	/**
	 * Tool-based chat with Agentic RAG integration
	 * 使用LangChain4j AiServices构建Assistant，支持Tool自动调用
	 *
	 * @param systemPrompt 系统提示词（定义角色和Tool调用规则）
	 * @param userQuery 用户查询（包含完整上下文）
	 * @param tools 要注册的Tool实例
	 * @param model 使用的AI模型
	 * @param handler 流式处理器
	 */
	public <T> void toolBasedChat(
		String systemPrompt,
		String userQuery,
		Object[] tools,
		String model,
		Class<T> assistantInterface,
		StreamingResponseHandler<AiMessage> handler
	) {
		// 选择ChatModel
		StreamingChatModel chatModel;
		chatModel = resolveTextModel(model);

		// 使用AiServices构建Assistant
		T assistant = AiServices.builder(assistantInterface)
			.streamingChatModel(chatModel)
			.tools(tools)
			.chatMemory(MessageWindowChatMemory.withMaxMessages(10))
			.build();

		// 组合完整查询
		String fullQuery = systemPrompt + "\n\n" + userQuery;

		// 通过反射调用chat方法（因为assistantInterface可能有不同的方法签名）
		try {
			java.lang.reflect.Method chatMethod = assistantInterface.getMethod("chat", String.class);
			dev.langchain4j.service.TokenStream tokenStream =
				(dev.langchain4j.service.TokenStream) chatMethod.invoke(assistant, fullQuery);

			tokenStream
				.onPartialResponse(handler::onNext)
				.onCompleteResponse(response -> {
                    Response<AiMessage> wrapped = Response.from(
                        response.aiMessage(),
                        response.tokenUsage(),
                        response.finishReason()
                    );
                    handler.onComplete(wrapped);
                })
				.onError(handler::onError)
				.start();
		} catch (Exception e) {
			log.error("Tool-based chat调用失败: {}", e.getMessage(), e);
			handler.onError(e);
		}
	}

    private StreamingChatModel resolveTextModel(String model) {
        if (AiModel.QWEN.equals(model)) {
            log.info("使用千问模型");
            return qwenStreamingChatLanguageModel;
        }
        if (AiModel.DEEPSEEKV3.equals(model)) {
            log.info("使用DeepSeekV3模型");
            return deepseekV3StreamingChatLanguageModel;
        }
        throw new IllegalArgumentException("未配置可用的文本模型: " + model);
    }

    private StreamingChatResponseHandler buildStreamingHandler(StreamingResponseHandler<AiMessage> handler) {
        return new StreamingChatResponseHandler() {
            @Override
            public void onPartialResponse(String partialResponse) {
                handler.onNext(partialResponse);
            }

            @Override
            public void onCompleteResponse(ChatResponse response) {
                Response<AiMessage> wrapped = Response.from(
                    response.aiMessage(),
                    response.tokenUsage(),
                    response.finishReason()
                );
                handler.onComplete(wrapped);
            }

            @Override
            public void onError(Throwable error) {
                handler.onError(error);
            }
        };
    }

}

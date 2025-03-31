package cn.yifan.drawsee.service.base;

import java.util.List;
import java.util.LinkedList;
import dev.langchain4j.data.message.ChatMessage;
import java.util.concurrent.atomic.AtomicLong;
import com.fasterxml.jackson.core.JsonProcessingException;

/**
 * AI服务接口
 */
public interface AiService {
    
    /**
     * 获取会话标题
     */
    String getConvTitle(String question);

    /**
     * 获取相关知识点
     */
    List<String> getRelatedKnowledgePoints(List<String> knowledgePoints, String question, AtomicLong tokens);

    /**
     * 识别图片中的文本
     */
    String recognizeTextFromImage(String imageUrl);

    /**
     * 获取解题方法
     */
    List<String> getSolveWays(String question) throws JsonProcessingException;

    /**
     * 获取规划拆分
     */
    List<String> getPlannerSplit(LinkedList<ChatMessage> history, AtomicLong tokens) throws JsonProcessingException;
}

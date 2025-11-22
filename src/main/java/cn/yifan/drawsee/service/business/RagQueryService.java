package cn.yifan.drawsee.service.business;

import cn.yifan.drawsee.config.WeaviateConfig;
import cn.yifan.drawsee.pojo.vo.rag.RagChatResponseVO;
import cn.yifan.drawsee.service.base.AiService;
import cn.yifan.drawsee.service.base.StreamAiService;
import io.weaviate.client.WeaviateClient;
import io.weaviate.client.base.Result;
import io.weaviate.client.v1.graphql.model.GraphQLResponse;
import io.weaviate.client.v1.graphql.query.argument.NearVectorArgument;
import io.weaviate.client.v1.graphql.query.fields.Field;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * RAG 检索服务
 *
 * 负责组织向量检索、重排序与大模型调用
 *
 * @author devin
 * @date 2025-10-10
 */
@Service
@Slf4j
public class RagQueryService {

    private final WeaviateClient weaviateClient;
    private final WeaviateConfig weaviateConfig;
    private final EmbeddingService embeddingService;
    private final AiService aiService;

    @Autowired
    public RagQueryService(WeaviateClient weaviateClient,
                           WeaviateConfig weaviateConfig,
                           EmbeddingService embeddingService,
                           AiService aiService) {
        this.weaviateClient = weaviateClient;
        this.weaviateConfig = weaviateConfig;
        this.embeddingService = embeddingService;
        this.aiService = aiService;
    }

    /**
     * 根据查询问题返回答案
     *
     * @param knowledgeBaseIds 参与检索的知识库集合
     * @param query            用户问题
     * @param history          对话历史
     * @return RAG响应
     */
    public RagChatResponseVO query(List<String> knowledgeBaseIds, String query, List<String> history) {
        try {
            // 1. 生成查询向量
            double[] queryVector = embeddingService.generateEmbedding(query);
            
            // 2. 检索相关文档块
            List<Map<String, Object>> retrievedChunks = new ArrayList<>();
            for (String knowledgeBaseId : knowledgeBaseIds) {
                String className = buildClassName(knowledgeBaseId);
                List<Map<String, Object>> chunks = searchSimilarChunks(className, queryVector, 5);
                retrievedChunks.addAll(chunks);
            }
            
            if (retrievedChunks.isEmpty()) {
                return createEmptyResponse("未找到相关知识内容");
            }
            
            // 3. 构建增强提示词
            String enhancedPrompt = buildEnhancedPrompt(query, retrievedChunks, history);
            
            // 4. 调用LLM生成回答
            String answer = aiService.getConvTitle(enhancedPrompt);  // 暂时使用这个方法，实际应该有专门的RAG回答方法
            
            // 5. 构建响应
            RagChatResponseVO response = new RagChatResponseVO();
            response.setAnswer(answer);
            response.setDone(true);
            response.setChunks(retrievedChunks);
            
            return response;
            
        } catch (Exception e) {
            log.error("RAG查询失败: {}", e.getMessage(), e);
            return createEmptyResponse("知识检索服务异常: " + e.getMessage());
        }
    }

    /**
     * 搜索相似文档块
     */
    private List<Map<String, Object>> searchSimilarChunks(String className, double[] queryVector, int limit) {
        try {
            NearVectorArgument nearVector = NearVectorArgument.builder()
                    .vector(convertToFloatArray(queryVector))
                    .build();

            Field content = Field.builder()
                    .name("content")
                    .build();
            
            Field documentId = Field.builder()
                    .name("documentId")
                    .build();
            
            Field knowledgeBaseId = Field.builder()
                    .name("knowledgeBaseId")
                    .build();
            
            Field chunkIndex = Field.builder()
                    .name("chunkIndex")
                    .build();

            Result<GraphQLResponse> result = weaviateClient.graphQL().get()
                    .withClassName(className)
                    .withFields(content, documentId, knowledgeBaseId, chunkIndex)
                    .withNearVector(nearVector)
                    .withLimit(limit)
                    .run();

            if (result.hasErrors()) {
                log.warn("Weaviate搜索警告: {}", result.getError().getMessages());
                return new ArrayList<>();
            }

            GraphQLResponse response = result.getResult();
            List<Map<String, Object>> chunks = new ArrayList<>();
            
            // 解析响应
            @SuppressWarnings("unchecked")
            Map<String, Object> data = (Map<String, Object>) response.getData();
            if (data != null && data.containsKey("Get")) {
                @SuppressWarnings("unchecked")
                Map<String, Object> get = (Map<String, Object>) data.get("Get");
                if (get.containsKey(className)) {
                    @SuppressWarnings("unchecked")
                    List<Map<String, Object>> results = (List<Map<String, Object>>) get.get(className);
                    chunks.addAll(results);
                }
            }
            
            return chunks;
            
        } catch (Exception e) {
            log.error("检索文档块失败: {}", e.getMessage(), e);
            return new ArrayList<>();
        }
    }

    /**
     * 构建增强提示词
     */
    private String buildEnhancedPrompt(String query, List<Map<String, Object>> chunks, List<String> history) {
        StringBuilder prompt = new StringBuilder();
        
        // 添加历史对话上下文
        if (history != null && !history.isEmpty()) {
            prompt.append("对话历史:\n");
            for (String historyItem : history) {
                prompt.append(historyItem).append("\n");
            }
            prompt.append("\n");
        }
        
        // 添加知识库内容
        prompt.append("相关知识内容:\n");
        for (Map<String, Object> chunk : chunks) {
            String content = (String) chunk.get("content");
            if (content != null) {
                prompt.append("- ").append(content).append("\n");
            }
        }
        
        prompt.append("\n基于以上知识内容，请回答用户问题: ").append(query);
        prompt.append("\n\n请确保回答准确、有条理，如果知识内容中没有相关信息，请如实说明。");
        
        return prompt.toString();
    }

    /**
     * 构建类名
     */
    private String buildClassName(String knowledgeBaseId) {
        String prefix = weaviateConfig.getClassPrefix();
        String sanitized = knowledgeBaseId.replaceAll("[^A-Za-z0-9]", "_");
        if (sanitized.length() > 48) {
            sanitized = sanitized.substring(0, 48);
        }
        String className = prefix + "_" + sanitized;
        if (Character.isDigit(className.charAt(0))) {
            className = "K" + className;
        }
        return className;
    }
    
    /**
     * 转换为Float数组
     */
    private Float[] convertToFloatArray(double[] doubleArray) {
        Float[] floatArray = new Float[doubleArray.length];
        for (int i = 0; i < doubleArray.length; i++) {
            floatArray[i] = (float) doubleArray[i];
        }
        return floatArray;
    }
    
    /**
     * 创建空响应
     */
    private RagChatResponseVO createEmptyResponse(String message) {
        RagChatResponseVO response = new RagChatResponseVO();
        response.setAnswer(message);
        response.setDone(true);
        response.setChunks(new ArrayList<>());
        return response;
    }
}

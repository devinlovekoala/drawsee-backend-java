package cn.yifan.drawsee.service.business;

import cn.yifan.drawsee.pojo.vo.rag.RagChatResponseVO;
import cn.yifan.drawsee.service.base.PythonRagService;
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
 * 作为MCP工具调用Python RAG微服务进行知识检索
 * 调用失败时返回null，由调用方回退到纯LLM生成（无RAG增强）
 *
 * @author devin
 * @date 2025-10-10
 */
@Service
@Slf4j
public class RagQueryService {

    private final PythonRagService pythonRagService;

    @Autowired
    public RagQueryService(PythonRagService pythonRagService) {
        this.pythonRagService = pythonRagService;
    }

    /**
     * RAG混合检索（支持多知识库、电路结构化数据）
     *
     * @param knowledgeBaseIds 参与检索的知识库集合
     * @param query            用户问题
     * @param history          对话历史（暂未使用）
     * @param userId           用户ID
     * @param classId          班级ID
     * @return RAG响应，失败时返回null（调用方应回退到纯LLM生成）
     */
    public RagChatResponseVO query(List<String> knowledgeBaseIds, String query, List<String> history, Long userId, String classId) {
        // 检查Python RAG服务是否可用
        if (!pythonRagService.isServiceAvailable()) {
            log.warn("Python RAG服务不可用，返回null由调用方回退到纯LLM生成");
            return null;
        }

        try {
            log.info("使用Python RAG MCP服务进行混合检索: 用户{}, 问题: {}, 知识库: {}", userId, query, knowledgeBaseIds);

            // 调用Python RAG混合检索（向量+结构化数据）
            Map<String, Object> pythonResponse = pythonRagService.ragQuery(
                query,
                knowledgeBaseIds,
                classId,
                userId,
                5  // Top-K
            );

            if (pythonResponse == null) {
                log.warn("Python RAG服务返回null，回退到纯LLM生成");
                return null;
            }

            // 转换Python服务响应为RagChatResponseVO
            return convertPythonResponseToVO(pythonResponse);

        } catch (Exception e) {
            log.warn("Python RAG服务调用异常，返回null: {}", e.getMessage());
            // 像MCP工具失败一样，返回null让调用方回退到纯LLM
            return null;
        }
    }

    /**
     * 转换Python服务响应为VO对象
     *
     * Python响应格式:
     * {
     *   "success": true,
     *   "message": "检索成功，返回3条结果",
     *   "results": [
     *     {
     *       "circuit_id": "...",
     *       "score": 0.95,
     *       "caption": "这是一个共射极放大电路...",
     *       "bom": [...],
     *       "topology": {...},
     *       "page_number": 3,
     *       "image_url": "...",
     *       "document_id": "..."
     *     }
     *   ],
     *   "total": 3
     * }
     */
    private RagChatResponseVO convertPythonResponseToVO(Map<String, Object> pythonResponse) {
        RagChatResponseVO response = new RagChatResponseVO();

        // 提取检索结果
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> results = (List<Map<String, Object>>) pythonResponse.get("results");

        if (results != null && !results.isEmpty()) {
            // 将电路检索结果转换为知识块格式
            List<Map<String, Object>> chunks = new ArrayList<>();
            StringBuilder combinedContext = new StringBuilder();

            for (Map<String, Object> result : results) {
                // 构造知识块
                Map<String, Object> chunk = new HashMap<>();

                // 基本信息
                chunk.put("circuit_id", result.get("circuit_id"));
                chunk.put("score", result.get("score"));
                chunk.put("page_number", result.get("page_number"));
                chunk.put("image_url", result.get("image_url"));
                chunk.put("document_id", result.get("document_id"));

                // 电路结构化数据
                chunk.put("caption", result.get("caption"));
                chunk.put("bom", result.get("bom"));
                chunk.put("topology", result.get("topology"));

                chunks.add(chunk);

                // 组装上下文（用于LLM生成）
                String caption = (String) result.get("caption");
                Object pageNum = result.get("page_number");

                combinedContext.append(String.format(
                    "【电路图 %d】(页码: %s, 相似度: %.2f)\n%s\n\n",
                    chunks.size(),
                    pageNum,
                    ((Number) result.get("score")).doubleValue(),
                    caption
                ));
            }

            response.setChunks(chunks);

            // 设置组合的上下文（供LLM参考）
            response.setAnswer(combinedContext.toString());

            log.info("RAG检索成功: 返回{}个电路图相关结果", chunks.size());
        } else {
            response.setChunks(new ArrayList<>());
            response.setAnswer("");
            log.info("RAG检索无结果");
        }

        response.setDone(true);
        return response;
    }

}

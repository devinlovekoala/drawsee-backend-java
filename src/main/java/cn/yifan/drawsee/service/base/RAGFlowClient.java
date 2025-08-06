package cn.yifan.drawsee.service.base;

import cn.yifan.drawsee.config.RagFlowConfig;
import cn.yifan.drawsee.exception.ApiError;
import cn.yifan.drawsee.exception.ApiException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

import java.util.Map;
import java.util.HashMap;
import java.util.List;

/**
 * @FileName RAGFlowClient
 * @Description RAGFlow API客户端服务，用于与RAGFlow进行交互
 * @Author devin
 * @date 2025-09-06 10:00
 */
@Service
@Slf4j
public class RAGFlowClient {

    @Autowired
    private RagFlowConfig ragFlowConfig;
    
    private final RestTemplate restTemplate;
    
    @Autowired
    public RAGFlowClient(@Qualifier("ragFlowRestTemplate") RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }
    
    /**
     * 创建知识库
     * @param name 知识库名称
     * @param description 知识库描述
     * @return 知识库ID
     */
    public String createKnowledgeBase(String name, String description) {
        String url = ragFlowConfig.getApiEndpoint() + "/api/knowledge";
        
        Map<String, Object> body = new HashMap<>();
        body.put("knowledge_name", name);
        body.put("knowledge_description", description);
        
        try {
            ResponseEntity<Map> response = restTemplate.postForEntity(url, body, Map.class);
            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                Map<String, Object> data = (Map<String, Object>) response.getBody().get("data");
                return (String) data.get("knowledge_id");
            } else {
                log.error("创建RAGFlow知识库失败: {}", response);
                throw new ApiException(ApiError.SYSTEM_ERROR, "文件不能为空");
            }
        } catch (Exception e) {
            log.error("创建RAGFlow知识库异常", e);
            throw new ApiException(ApiError.SYSTEM_ERROR, "文件不能为空");
        }
    }
    
    /**
     * 上传文档到知识库
     * @param knowledgeBaseId 知识库ID
     * @param file 文档文件
     * @param options 处理选项
     * @return 上传任务ID
     */
    public String uploadDocument(String knowledgeBaseId, MultipartFile file, Map<String, Object> options) {
        String url = ragFlowConfig.getApiEndpoint() + "/api/document/upload";
        
        try {
            // 准备文件部分
            ByteArrayResource fileResource = new ByteArrayResource(file.getBytes()) {
                @Override
                public String getFilename() {
                    return file.getOriginalFilename();
                }
            };
            
            // 准备表单数据
            MultiValueMap<String, Object> formData = new LinkedMultiValueMap<>();
            formData.add("file", fileResource);
            formData.add("knowledge_id", knowledgeBaseId);
            
            // 添加其他选项
            if (options != null) {
                for (Map.Entry<String, Object> entry : options.entrySet()) {
                    formData.add(entry.getKey(), entry.getValue());
                }
            }
            
            HttpHeaders headers = new HttpHeaders();
            headers.set("Content-Type", "multipart/form-data");
            
            HttpEntity<MultiValueMap<String, Object>> requestEntity = new HttpEntity<>(formData, headers);
            
            ResponseEntity<Map> response = restTemplate.exchange(
                url,
                HttpMethod.POST,
                requestEntity,
                Map.class
            );
            
            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                Map<String, Object> data = (Map<String, Object>) response.getBody().get("data");
                return (String) data.get("task_id");
            } else {
                log.error("上传文档到RAGFlow失败: {}", response);
                throw new ApiException(ApiError.SYSTEM_ERROR, "文件不能为空");
            }
        } catch (Exception e) {
            log.error("上传文档到RAGFlow异常", e);
            throw new ApiException(ApiError.SYSTEM_ERROR, "文件不能为空");
        }
    }
    
    /**
     * 获取文档处理任务状态
     * @param taskId 任务ID
     * @return 任务状态信息
     */
    public Map<String, Object> getTaskStatus(String taskId) {
        String url = ragFlowConfig.getApiEndpoint() + "/api/task/status?task_id=" + taskId;
        
        try {
            ResponseEntity<Map> response = restTemplate.getForEntity(url, Map.class);
            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                return (Map<String, Object>) response.getBody().get("data");
            } else {
                log.error("获取RAGFlow任务状态失败: {}", response);
                throw new ApiException(ApiError.SYSTEM_ERROR, "文件不能为空");
            }
        } catch (Exception e) {
            log.error("获取RAGFlow任务状态异常", e);
            throw new ApiException(ApiError.SYSTEM_ERROR, "文件不能为空");
        }
    }
    
    /**
     * 从知识库中检索相关内容
     * @param knowledgeBaseId 知识库ID
     * @param query 查询文本
     * @param topK 返回结果数量
     * @return 检索结果
     */
    public List<Map<String, Object>> retrieveFromKnowledgeBase(String knowledgeBaseId, String query, int topK) {
        String url = ragFlowConfig.getApiEndpoint() + "/api/knowledge/retrieve";
        
        Map<String, Object> body = new HashMap<>();
        body.put("knowledge_id", knowledgeBaseId);
        body.put("query", query);
        body.put("top_k", topK);
        
        try {
            ResponseEntity<Map> response = restTemplate.postForEntity(url, body, Map.class);
            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                Map<String, Object> data = (Map<String, Object>) response.getBody().get("data");
                return (List<Map<String, Object>>) data.get("results");
            } else {
                log.error("从RAGFlow检索知识失败: {}", response);
                throw new ApiException(ApiError.SYSTEM_ERROR, "文件不能为空");
            }
        } catch (Exception e) {
            log.error("从RAGFlow检索知识异常", e);
            throw new ApiException(ApiError.SYSTEM_ERROR, "文件不能为空");
        }
    }
    
    /**
     * 查询知识库包含的所有文档
     * @param knowledgeBaseId 知识库ID
     * @return 文档列表
     */
    public List<Map<String, Object>> listDocuments(String knowledgeBaseId) {
        String url = ragFlowConfig.getApiEndpoint() + "/api/document/list?knowledge_id=" + knowledgeBaseId;
        
        try {
            ResponseEntity<Map> response = restTemplate.getForEntity(url, Map.class);
            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                Map<String, Object> data = (Map<String, Object>) response.getBody().get("data");
                return (List<Map<String, Object>>) data.get("documents");
            } else {
                log.error("获取RAGFlow文档列表失败: {}", response);
                throw new ApiException(ApiError.SYSTEM_ERROR, "文件不能为空");
            }
        } catch (Exception e) {
            log.error("获取RAGFlow文档列表异常", e);
            throw new ApiException(ApiError.SYSTEM_ERROR, "文件不能为空");
        }
    }
    
    /**
     * 删除知识库中的文档
     * @param knowledgeBaseId 知识库ID
     * @param documentId 文档ID
     * @return 是否成功
     */
    public boolean deleteDocument(String knowledgeBaseId, String documentId) {
        String url = ragFlowConfig.getApiEndpoint() + "/api/document/delete";
        
        Map<String, Object> body = new HashMap<>();
        body.put("knowledge_id", knowledgeBaseId);
        body.put("document_id", documentId);
        
        try {
            ResponseEntity<Map> response = restTemplate.postForEntity(url, body, Map.class);
            return response.getStatusCode().is2xxSuccessful();
        } catch (Exception e) {
            log.error("删除RAGFlow文档异常", e);
            throw new ApiException(ApiError.SYSTEM_ERROR, "文件不能为空");
        }
    }
    
    /**
     * 与知识库进行对话
     * @param knowledgeBaseId 知识库ID
     * @param query 用户问题
     * @param history 对话历史
     * @return AI回复
     */
    public Map<String, Object> chatWithKnowledge(String knowledgeBaseId, String query, List<Map<String, String>> history) {
        String url = ragFlowConfig.getApiEndpoint() + "/api/knowledge/chat";
        
        Map<String, Object> body = new HashMap<>();
        body.put("knowledge_id", knowledgeBaseId);
        body.put("query", query);
        body.put("history", history);
        
        try {
            ResponseEntity<Map> response = restTemplate.postForEntity(url, body, Map.class);
            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                return (Map<String, Object>) response.getBody().get("data");
            } else {
                log.error("RAGFlow知识库对话失败: {}", response);
                throw new ApiException(ApiError.SYSTEM_ERROR, "文件不能为空");
            }
        } catch (Exception e) {
            log.error("RAGFlow知识库对话异常", e);
            throw new ApiException(ApiError.SYSTEM_ERROR, "文件不能为空");
        }
    }
    
    /**
     * 取消文档处理任务
     * @param taskId 任务ID
     * @return 结果，包含成功或失败信息
     */
    public Map<String, Object> cancelTask(String taskId) {
        try {
            // 构建URL
            String url = ragFlowConfig.getApiEndpoint() + "/api/tasks/" + taskId + "/cancel";
            
            // 发送POST请求，取消任务
            ResponseEntity<Map> response = restTemplate.postForEntity(url, null, Map.class);
            
            // 转换结果
            return response.getBody();
        } catch (Exception e) {
            log.error("取消任务失败", e);
            throw new ApiException(ApiError.SYSTEM_ERROR, "文件不能为空");
        }
    }
    
    /**
     * 更新知识库信息
     * @param knowledgeId 知识库ID
     * @param name 新名称
     * @param description 新描述
     * @return 是否成功
     */
    public boolean updateKnowledgeBase(String knowledgeId, String name, String description) {
        String url = ragFlowConfig.getApiEndpoint() + "/api/knowledge/update";
        
        Map<String, Object> body = new HashMap<>();
        body.put("knowledge_id", knowledgeId);
        
        if (name != null) {
            body.put("knowledge_name", name);
        }
        
        if (description != null) {
            body.put("knowledge_description", description);
        }
        
        try {
            ResponseEntity<Map> response = restTemplate.postForEntity(url, body, Map.class);
            return response.getStatusCode().is2xxSuccessful();
        } catch (Exception e) {
            log.error("更新RAGFlow知识库异常", e);
            throw new ApiException(ApiError.SYSTEM_ERROR, "文件不能为空");
        }
    }
    
    /**
     * 删除知识库
     * @param knowledgeId 知识库ID
     * @return 是否成功
     */
    public boolean deleteKnowledgeBase(String knowledgeId) {
        String url = ragFlowConfig.getApiEndpoint() + "/api/knowledge/delete";
        
        Map<String, Object> body = new HashMap<>();
        body.put("knowledge_id", knowledgeId);
        
        try {
            ResponseEntity<Map> response = restTemplate.postForEntity(url, body, Map.class);
            return response.getStatusCode().is2xxSuccessful();
        } catch (Exception e) {
            log.error("删除RAGFlow知识库异常", e);
            throw new ApiException(ApiError.SYSTEM_ERROR, "文件不能为空");
        }
    }
    
    /**
     * 获取知识库的统计信息
     * @param knowledgeId 知识库ID
     * @return 统计信息
     */
    public Map<String, Object> getKnowledgeStats(String knowledgeId) {
        String url = ragFlowConfig.getApiEndpoint() + "/api/knowledge/stats?knowledge_id=" + knowledgeId;
        
        try {
            ResponseEntity<Map> response = restTemplate.getForEntity(url, Map.class);
            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                return (Map<String, Object>) response.getBody().get("data");
            } else {
                log.error("获取RAGFlow知识库统计信息失败: {}", response);
                throw new ApiException(ApiError.SYSTEM_ERROR, "文件不能为空");
            }
        } catch (Exception e) {
            log.error("获取RAGFlow知识库统计信息异常", e);
            throw new ApiException(ApiError.SYSTEM_ERROR, "文件不能为空");
        }
    }
    
    /**
     * 批量上传文档到知识库
     * @param knowledgeBaseId 知识库ID
     * @param files 文档文件列表
     * @param options 处理选项
     * @return 上传任务ID列表
     */
    public List<String> batchUploadDocuments(String knowledgeBaseId, List<MultipartFile> files, Map<String, Object> options) {
        List<String> taskIds = new java.util.ArrayList<>();
        
        for (MultipartFile file : files) {
            try {
                String taskId = uploadDocument(knowledgeBaseId, file, options);
                taskIds.add(taskId);
            } catch (Exception e) {
                log.error("批量上传文档时单个文件上传失败: {}", file.getOriginalFilename(), e);
                // 继续处理其他文件
            }
        }
        
        return taskIds;
    }
} 
package cn.yifan.drawsee.service.business;

import cn.dev33.satoken.stp.StpUtil;
import cn.yifan.drawsee.config.RagFlowConfig;
import cn.yifan.drawsee.constant.UserRole;
import cn.yifan.drawsee.exception.ApiError;
import cn.yifan.drawsee.exception.ApiException;
import cn.yifan.drawsee.exception.BusinessException;
import cn.yifan.drawsee.mapper.KnowledgeBaseMapper;
import cn.yifan.drawsee.mapper.KnowledgeResourceMapper;
import cn.yifan.drawsee.pojo.dto.rag.RagChatRequestDTO;
import cn.yifan.drawsee.pojo.dto.rag.RagCreateKnowledgeDTO;
import cn.yifan.drawsee.pojo.entity.KnowledgeBase;
import cn.yifan.drawsee.pojo.entity.KnowledgeResource;
import cn.yifan.drawsee.pojo.vo.rag.RagChatResponseVO;
import cn.yifan.drawsee.pojo.vo.rag.RagDocumentVO;
import cn.yifan.drawsee.pojo.vo.rag.RagKnowledgeListResponse;
import cn.yifan.drawsee.pojo.vo.rag.RagKnowledgeVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * RAGFlow服务
 * 提供连接和调用RAGFlow相关API的功能
 * 
 * @author devin
 * @date 2025-05-09
 */
@Service
@Slf4j
public class RagFlowService {
    
    @Autowired
    private RagFlowConfig ragFlowConfig;
    
    @Autowired
    @Qualifier("ragFlowRestTemplateAlt")
    private RestTemplate restTemplate;
    
    @Autowired
    private KnowledgeBaseMapper knowledgeBaseMapper;
    
    @Autowired
    private KnowledgeResourceMapper knowledgeResourceMapper;
    
    @Autowired
    private UserService userService;
    
    /**
     * 获取用户可访问的RAG知识库ID列表
     * 
     * @param classId 班级ID（可选）
     * @param userId 用户ID
     * @return 可访问的RAG知识库ID列表
     */
    public List<String> getAccessibleRagKnowledgeIds(String classId, Long userId) {
        List<String> ragKnowledgeIds = new ArrayList<>();
        
        // 获取用户角色
        String userRole = userService.getUserRole(userId);
        
        // 获取用户可访问的知识库
        List<KnowledgeBase> accessibleBases;
        
        // 管理员可以访问所有启用了RAG的知识库
        if (UserRole.ADMIN.equals(userRole)) {
            accessibleBases = knowledgeBaseMapper.getAll(false);
        } else {
            // 获取用户创建的知识库
            List<KnowledgeBase> createdBases = knowledgeBaseMapper.getByCreatorId(userId, false);
            
            // 获取用户加入的知识库
            List<KnowledgeBase> joinedBases = knowledgeBaseMapper.getByMemberId(userId, false);
            
            // 获取已发布的知识库 - 使用过滤方式获取，因为没有直接的方法
            List<KnowledgeBase> allBases = knowledgeBaseMapper.getAll(false);
            List<KnowledgeBase> publishedBases = allBases.stream()
                .filter(base -> Boolean.TRUE.equals(base.getIsPublished()))
                .collect(Collectors.toList());
            
            // 合并去重
            Set<String> baseIds = new HashSet<>();
            accessibleBases = new ArrayList<>();
            
            // 添加用户创建的知识库
            for (KnowledgeBase base : createdBases) {
                if (!baseIds.contains(base.getId())) {
                    baseIds.add(base.getId());
                    accessibleBases.add(base);
                }
            }
            
            // 添加用户加入的知识库
            for (KnowledgeBase base : joinedBases) {
                if (!baseIds.contains(base.getId())) {
                    baseIds.add(base.getId());
                    accessibleBases.add(base);
                }
            }
            
            // 添加已发布的知识库
            for (KnowledgeBase base : publishedBases) {
                if (!baseIds.contains(base.getId())) {
                    baseIds.add(base.getId());
                    accessibleBases.add(base);
                }
            }
        }
        
        // 过滤出启用了RAG的知识库，并且有ragKnowledgeId
        ragKnowledgeIds = accessibleBases.stream()
            .filter(base -> base.getRagEnabled() != null && base.getRagEnabled() && base.getRagKnowledgeId() != null)
            .map(KnowledgeBase::getRagKnowledgeId)
            .collect(Collectors.toList());
        
        log.info("用户{}可访问{}个RAG知识库", userId, ragKnowledgeIds.size());
        return ragKnowledgeIds;
    }
    
    /**
     * 执行RAG知识库问答
     * 
     * @param query 查询问题
     * @param knowledgeIds 知识库ID列表
     * @param taskId 任务ID
     * @param systemPrompt 系统提示
     * @param history 历史消息
     * @return RAG问答响应VO
     */
    public RagChatResponseVO ragChat(String query, List<String> knowledgeIds, String taskId, 
                                     String systemPrompt, List<Map<String, String>> history) {
        if (!ragFlowConfig.isEnabled()) {
            log.warn("RAGFlow功能未启用，无法执行知识库问答");
            return null;
        }
        
        if (knowledgeIds == null || knowledgeIds.isEmpty()) {
            log.warn("未提供知识库ID，无法执行知识库问答");
            return null;
        }
        
        // 构建请求对象
        RagChatRequestDTO chatRequest = new RagChatRequestDTO();
        chatRequest.setQuery(query);
        // 使用第一个知识库ID，因为RagChatRequestDTO只支持单个知识库ID
        chatRequest.setKnowledgeId(knowledgeIds.get(0));
        chatRequest.setSessionId(taskId);
        chatRequest.setSystemPrompt(systemPrompt);
        chatRequest.setHistory(history);
        
        return ragChat(chatRequest);
    }

    /**
     * 执行RAG知识库问答
     * 
     * @param chatRequest RAG问答请求DTO
     * @return RAG问答响应VO
     */
    public RagChatResponseVO ragChat(RagChatRequestDTO chatRequest) {
        if (!ragFlowConfig.isEnabled()) {
            log.warn("RAGFlow功能未启用，无法执行知识库问答");
            return null;
        }
        
        String apiUrl = ragFlowConfig.getApiEndpoint() + "/api/knowledge/chat";
        
        try {
            // 设置请求头
            HttpHeaders headers = getJsonHttpHeaders();
            
            // 创建HTTP请求实体
            HttpEntity<RagChatRequestDTO> httpEntity = new HttpEntity<>(chatRequest, headers);
            
            log.info("发送RAG问答请求: knowledgeId={}, query={}", chatRequest.getKnowledgeId(), chatRequest.getQuery());
            long startTime = System.currentTimeMillis();
            
            // 执行请求
            ResponseEntity<RagChatResponseVO> responseEntity = restTemplate.exchange(
                apiUrl, 
                HttpMethod.POST,
                httpEntity, 
                RagChatResponseVO.class
            );
            
            long endTime = System.currentTimeMillis();
            long duration = endTime - startTime;
            
            // 检查响应
            if (responseEntity.getStatusCode().is2xxSuccessful() && responseEntity.getBody() != null) {
                RagChatResponseVO response = responseEntity.getBody();
                log.info("RAG问答请求成功: 耗时={}ms", duration);
                
                // 如果响应没有设置耗时，则手动设置
                if (response.getCostTime() == null) {
                    response.setCostTime(duration);
                }
                
                return response;
            } else {
                log.error("RAG问答请求返回错误状态码: {}", responseEntity.getStatusCode());
                return null;
            }
        } catch (RestClientException e) {
            log.error("RAG问答请求失败: {}", e.getMessage(), e);
            return null;
        } catch (Exception e) {
            log.error("处理RAG问答请求时发生异常: {}", e.getMessage(), e);
            return null;
        }
    }
    
    /**
     * 创建知识库
     *
     * @param createDto 创建知识库DTO
     * @return 创建的知识库信息
     */
    public RagKnowledgeVO createKnowledge(RagCreateKnowledgeDTO createDto) {
        if (!ragFlowConfig.isEnabled()) {
            throw new BusinessException("RAGFlow服务未启用");
        }

        HttpHeaders headers = getJsonHttpHeaders();
        HttpEntity<RagCreateKnowledgeDTO> requestEntity = new HttpEntity<>(createDto, headers);

        try {
            ResponseEntity<RagKnowledgeVO> responseEntity = restTemplate.exchange(
                    ragFlowConfig.getApiEndpoint() + "/api/knowledges",
                    HttpMethod.POST,
                    requestEntity,
                    RagKnowledgeVO.class
            );

            if (responseEntity.getStatusCode() == HttpStatus.CREATED) {
                return responseEntity.getBody();
            } else {
                log.error("创建知识库失败: {}", responseEntity.getStatusCode());
                throw new BusinessException("创建知识库失败: " + responseEntity.getStatusCode());
            }
        } catch (RestClientException e) {
            log.error("调用RAGFlow API创建知识库时出错", e);
            throw new BusinessException("调用RAGFlow API创建知识库时出错: " + e.getMessage());
        }
    }

    /**
     * 获取知识库列表
     *
     * @param page 页码
     * @param size 每页大小
     * @return 知识库列表响应
     */
    public RagKnowledgeListResponse listKnowledges(int page, int size) {
        if (!ragFlowConfig.isEnabled()) {
            return new RagKnowledgeListResponse(Collections.emptyList(), 0);
        }

        HttpHeaders headers = getJsonHttpHeaders();
        HttpEntity<?> requestEntity = new HttpEntity<>(headers);

        try {
            String url = ragFlowConfig.getApiEndpoint() + "/api/knowledges?page=" + page + "&size=" + size;
            ResponseEntity<RagKnowledgeListResponse> responseEntity = restTemplate.exchange(
                    url,
                    HttpMethod.GET,
                    requestEntity,
                    RagKnowledgeListResponse.class
            );

            if (responseEntity.getStatusCode() == HttpStatus.OK) {
                return responseEntity.getBody();
            } else {
                log.error("获取知识库列表失败: {}", responseEntity.getStatusCode());
                throw new BusinessException("获取知识库列表失败: " + responseEntity.getStatusCode());
            }
        } catch (RestClientException e) {
            log.error("调用RAGFlow API获取知识库列表时出错", e);
            throw new BusinessException("调用RAGFlow API获取知识库列表时出错: " + e.getMessage());
        }
    }

    /**
     * 获取知识库详情
     *
     * @param knowledgeId 知识库ID
     * @return 知识库详情
     */
    public RagKnowledgeVO getKnowledgeDetail(String knowledgeId) {
        if (!ragFlowConfig.isEnabled()) {
            throw new BusinessException("RAGFlow服务未启用");
        }

        HttpHeaders headers = getJsonHttpHeaders();
        HttpEntity<?> requestEntity = new HttpEntity<>(headers);

        try {
            ResponseEntity<RagKnowledgeVO> responseEntity = restTemplate.exchange(
                    ragFlowConfig.getApiEndpoint() + "/api/knowledges/" + knowledgeId,
                    HttpMethod.GET,
                    requestEntity,
                    RagKnowledgeVO.class
            );

            if (responseEntity.getStatusCode() == HttpStatus.OK) {
                return responseEntity.getBody();
            } else {
                log.error("获取知识库详情失败: {}", responseEntity.getStatusCode());
                throw new BusinessException("获取知识库详情失败: " + responseEntity.getStatusCode());
            }
        } catch (RestClientException e) {
            log.error("调用RAGFlow API获取知识库详情时出错", e);
            throw new BusinessException("调用RAGFlow API获取知识库详情时出错: " + e.getMessage());
        }
    }

    /**
     * 删除知识库
     *
     * @param knowledgeId 知识库ID
     * @return 是否删除成功
     */
    public boolean deleteKnowledge(String knowledgeId) {
        if (!ragFlowConfig.isEnabled()) {
            throw new BusinessException("RAGFlow服务未启用");
        }

        HttpHeaders headers = getJsonHttpHeaders();
        HttpEntity<?> requestEntity = new HttpEntity<>(headers);

        try {
            ResponseEntity<Void> responseEntity = restTemplate.exchange(
                    ragFlowConfig.getApiEndpoint() + "/api/knowledges/" + knowledgeId,
                    HttpMethod.DELETE,
                    requestEntity,
                    Void.class
            );

            return responseEntity.getStatusCode() == HttpStatus.NO_CONTENT;
        } catch (RestClientException e) {
            log.error("调用RAGFlow API删除知识库时出错", e);
            throw new BusinessException("调用RAGFlow API删除知识库时出错: " + e.getMessage());
        }
    }

    /**
     * 上传文档到知识库
     *
     * @param knowledgeId 知识库ID
     * @param file 文档文件
     * @return 上传的文档信息
     */
    public RagDocumentVO uploadDocument(String knowledgeId, MultipartFile file) {
        if (!ragFlowConfig.isEnabled()) {
            throw new BusinessException("RAGFlow服务未启用");
        }

        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.MULTIPART_FORM_DATA);

            MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
            ByteArrayResource resource = new ByteArrayResource(file.getBytes()) {
                @Override
                public String getFilename() {
                    return file.getOriginalFilename();
                }
            };
            body.add("file", resource);

            HttpEntity<MultiValueMap<String, Object>> requestEntity = new HttpEntity<>(body, headers);

            ResponseEntity<RagDocumentVO> responseEntity = restTemplate.exchange(
                    ragFlowConfig.getApiEndpoint() + "/api/knowledges/" + knowledgeId + "/documents",
                    HttpMethod.POST,
                    requestEntity,
                    RagDocumentVO.class
            );

            if (responseEntity.getStatusCode() == HttpStatus.CREATED) {
                return responseEntity.getBody();
            } else {
                log.error("上传文档失败: {}", responseEntity.getStatusCode());
                throw new BusinessException("上传文档失败: " + responseEntity.getStatusCode());
            }
        } catch (IOException e) {
            log.error("读取上传文件内容时出错", e);
            throw new BusinessException("读取上传文件内容时出错: " + e.getMessage());
        } catch (RestClientException e) {
            log.error("调用RAGFlow API上传文档时出错", e);
            throw new BusinessException("调用RAGFlow API上传文档时出错: " + e.getMessage());
        }
    }

    /**
     * 获取知识库中的文档列表
     *
     * @param knowledgeId 知识库ID
     * @param page 页码
     * @param size 每页大小
     * @return 文档列表
     */
    public List<RagDocumentVO> listDocuments(String knowledgeId, int page, int size) {
        if (!ragFlowConfig.isEnabled()) {
            return Collections.emptyList();
        }

        HttpHeaders headers = getJsonHttpHeaders();
        HttpEntity<?> requestEntity = new HttpEntity<>(headers);

        try {
            String url = ragFlowConfig.getApiEndpoint() + "/api/knowledges/" + knowledgeId + 
                        "/documents?page=" + page + "&size=" + size;
            
            ResponseEntity<RagDocumentVO[]> responseEntity = restTemplate.exchange(
                    url,
                    HttpMethod.GET,
                    requestEntity,
                    RagDocumentVO[].class
            );

            if (responseEntity.getStatusCode() == HttpStatus.OK && responseEntity.getBody() != null) {
                return List.of(responseEntity.getBody());
            } else {
                log.error("获取文档列表失败: {}", responseEntity.getStatusCode());
                throw new BusinessException("获取文档列表失败: " + responseEntity.getStatusCode());
            }
        } catch (RestClientException e) {
            log.error("调用RAGFlow API获取文档列表时出错", e);
            throw new BusinessException("调用RAGFlow API获取文档列表时出错: " + e.getMessage());
        }
    }

    /**
     * 删除文档
     *
     * @param documentId 文档ID
     * @return 是否删除成功
     */
    public boolean deleteDocument(String documentId) {
        if (!ragFlowConfig.isEnabled()) {
            throw new BusinessException("RAGFlow服务未启用");
        }

        HttpHeaders headers = getJsonHttpHeaders();
        HttpEntity<?> requestEntity = new HttpEntity<>(headers);

        try {
            ResponseEntity<Void> responseEntity = restTemplate.exchange(
                    ragFlowConfig.getApiEndpoint() + "/api/documents/" + documentId,
                    HttpMethod.DELETE,
                    requestEntity,
                    Void.class
            );

            return responseEntity.getStatusCode() == HttpStatus.NO_CONTENT;
        } catch (RestClientException e) {
            log.error("调用RAGFlow API删除文档时出错", e);
            throw new BusinessException("调用RAGFlow API删除文档时出错: " + e.getMessage());
        }
    }

    /**
     * 与RAG知识库聊天
     *
     * @param chatRequest 聊天请求
     * @return 聊天响应
     */
    public Map<String, Object> chat(RagChatRequestDTO chatRequest) {
        if (!ragFlowConfig.isEnabled()) {
            throw new BusinessException("RAGFlow服务未启用");
        }

        HttpHeaders headers = getJsonHttpHeaders();
        HttpEntity<RagChatRequestDTO> requestEntity = new HttpEntity<>(chatRequest, headers);

        try {
            ResponseEntity<Map> responseEntity = restTemplate.exchange(
                    ragFlowConfig.getApiEndpoint() + "/api/chat",
                    HttpMethod.POST,
                    requestEntity,
                    Map.class
            );

            if (responseEntity.getStatusCode().is2xxSuccessful() && responseEntity.getBody() != null) {
                return responseEntity.getBody();
            } else {
                log.error("RAG对话请求失败: {}", responseEntity.getStatusCode());
                throw new BusinessException("RAG对话请求失败: " + responseEntity.getStatusCode());
            }
        } catch (RestClientException e) {
            log.error("调用RAGFlow API进行对话时出错", e);
            throw new BusinessException("调用RAGFlow API进行对话时出错: " + e.getMessage());
        }
    }
    
    /**
     * 检查RAGFlow服务是否可用
     * 
     * @return 服务是否可用
     */
    public boolean isServiceAvailable() {
        if (!ragFlowConfig.isEnabled()) {
            return false;
        }
        
        try {
            ResponseEntity<String> responseEntity = restTemplate.getForEntity(
                ragFlowConfig.getApiEndpoint() + "/api/health",
                String.class
            );
            return responseEntity.getStatusCode().is2xxSuccessful();
        } catch (RestClientException e) {
            log.warn("RAGFlow服务健康检查失败: {}", e.getMessage());
            return false;
        }
    }
    
    /**
     * 等待RAGFlow服务可用
     * 在服务启动时可以调用此方法等待RAGFlow服务就绪
     * 
     * @param timeoutSeconds 超时时间（秒）
     * @return 是否在超时前服务变为可用
     */
    public boolean waitForServiceAvailable(int timeoutSeconds) {
        if (!ragFlowConfig.isEnabled()) {
            log.info("RAGFlow功能未启用，跳过等待服务可用");
            return false;
        }
        
        log.info("等待RAGFlow服务可用，超时时间: {}秒", timeoutSeconds);
        long startTime = System.currentTimeMillis();
        long timeoutMillis = TimeUnit.SECONDS.toMillis(timeoutSeconds);
        
        // 重试直到服务可用或超时
        while (System.currentTimeMillis() - startTime < timeoutMillis) {
            if (isServiceAvailable()) {
                long duration = System.currentTimeMillis() - startTime;
                log.info("RAGFlow服务已可用，耗时: {}ms", duration);
                return true;
            }
            
            try {
                // 等待1秒后重试
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                log.warn("等待RAGFlow服务可用时被中断");
                return false;
            }
        }
        
        log.warn("等待RAGFlow服务可用超时（{}秒）", timeoutSeconds);
        return false;
    }
    
    /**
     * 获取设置了JSON内容类型的HTTP请求头
     * 
     * @return HTTP请求头
     */
    private HttpHeaders getJsonHttpHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setAccept(Arrays.asList(MediaType.APPLICATION_JSON));
        
        // 添加认证信息（如果需要）
        // headers.set("Authorization", "Bearer " + ragFlowConfig.getApiKey());
        
        return headers;
    }
    
    /**
     * 创建知识库
     * 
     * @param dto 创建知识库参数
     * @return 创建的知识库信息
     */
    public RagKnowledgeVO createKnowledgeBase(RagCreateKnowledgeDTO dto) {
        if (!ragFlowConfig.isEnabled()) {
            log.warn("RAGFlow服务未启用，无法创建知识库");
            return null;
        }

        try {
            String url = ragFlowConfig.getApiEndpoint() + "/api/v1/datasets";
            
            // 构建请求头和请求体
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            
            HttpEntity<RagCreateKnowledgeDTO> requestEntity = new HttpEntity<>(dto, headers);
            
            // 发送请求
            ResponseEntity<RagKnowledgeVO> response = restTemplate.exchange(
                url,
                HttpMethod.POST,
                requestEntity,
                RagKnowledgeVO.class
            );
            
            // 检查响应状态
            if (response.getStatusCode().is2xxSuccessful()) {
                log.info("成功创建RAGFlow知识库: {}", response.getBody().getName());
                return response.getBody();
            } else {
                log.error("创建RAGFlow知识库失败, 状态码: {}", response.getStatusCode());
                return null;
            }
        } catch (Exception e) {
            log.error("创建RAGFlow知识库时发生异常", e);
            return null;
        }
    }
    
    /**
     * 上传文档到知识库
     * 
     * @param datasetId 知识库ID
     * @param file 文件
     * @param fileName 文件名
     * @return 上传结果
     */
    public RagDocumentVO uploadDocument(String datasetId, MultipartFile file, String fileName) {
        if (!ragFlowConfig.isEnabled()) {
            log.warn("RAGFlow服务未启用，无法上传文档");
            return null;
        }

        if (file == null || file.isEmpty()) {
            log.error("上传文档失败：文件为空");
            return null;
        }

        try {
            String url = ragFlowConfig.getApiEndpoint() + "/api/v1/datasets/" + datasetId + "/files";
            
            // 构建请求头和请求体
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.MULTIPART_FORM_DATA);
            
            // 准备文件和元数据
            Map<String, Object> metadataMap = new HashMap<>();
            metadataMap.put("filename", fileName);
            
            // 构建请求体
            HttpEntity<byte[]> requestEntity = new HttpEntity<>(file.getBytes(), headers);
            
            // 发送请求
            ResponseEntity<RagDocumentVO> response = restTemplate.exchange(
                url,
                HttpMethod.POST,
                requestEntity,
                RagDocumentVO.class
            );
            
            // 检查响应状态
            if (response.getStatusCode().is2xxSuccessful()) {
                log.info("成功上传文档到RAGFlow知识库: {}", fileName);
                return response.getBody();
            } else {
                log.error("上传文档到RAGFlow知识库失败, 状态码: {}", response.getStatusCode());
                return null;
            }
        } catch (IOException e) {
            log.error("处理文件时出错", e);
            return null;
        } catch (Exception e) {
            log.error("上传文档到RAGFlow知识库时发生异常", e);
            return null;
        }
    }
    
    /**
     * 获取知识库列表
     * 
     * @return 知识库列表
     */
    public List<RagKnowledgeVO> getKnowledgeBases() {
        if (!ragFlowConfig.isEnabled()) {
            log.warn("RAGFlow服务未启用，无法获取知识库列表");
            return Collections.emptyList();
        }

        try {
            String url = ragFlowConfig.getApiEndpoint() + "/api/v1/datasets";
            
            // 构建请求头
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            
            HttpEntity<String> requestEntity = new HttpEntity<>(headers);
            
            // 发送请求
            ResponseEntity<List<RagKnowledgeVO>> response = restTemplate.exchange(
                url,
                HttpMethod.GET,
                requestEntity,
                new ParameterizedTypeReference<List<RagKnowledgeVO>>() {}
            );
            
            // 检查响应状态
            if (response.getStatusCode().is2xxSuccessful()) {
                log.info("成功获取RAGFlow知识库列表");
                return response.getBody();
            } else {
                log.error("获取RAGFlow知识库列表失败, 状态码: {}", response.getStatusCode());
                return Collections.emptyList();
            }
        } catch (Exception e) {
            log.error("获取RAGFlow知识库列表时发生异常", e);
            return Collections.emptyList();
        }
    }

    /**
     * 获取文档列表
     * 简化版本，不分页
     *
     * @param knowledgeId 知识库ID
     * @return 文档列表
     */
    public List<RagDocumentVO> getDocumentList(String knowledgeId) {
        if (!ragFlowConfig.isEnabled()) {
            return Collections.emptyList();
        }

        return listDocuments(knowledgeId, 1, 100);
    }
    
    /**
     * 删除知识库
     * 兼容旧方法调用
     *
     * @param knowledgeId 知识库ID
     * @return 是否删除成功
     */
    public boolean deleteKnowledgeBase(String knowledgeId) {
        return deleteKnowledge(knowledgeId);
    }

    /**
     * 验证用户是否有权限操作知识库
     */
    private void validateUserPermission(KnowledgeBase knowledgeBase) {
        Long userId = StpUtil.getLoginIdAsLong();
        String userRole = userService.getUserRole();
        
        // 管理员有权限操作所有知识库
        if (UserRole.ADMIN.equals(userRole)) {
            return;
        }
        
        // 创建者有权限操作
        if (knowledgeBase.getCreatorId().equals(userId)) {
            return;
        }
        
        // 成员有权限操作
        if (knowledgeBase.getMembers() != null && knowledgeBase.getMembers().contains(userId)) {
            return;
        }
        
        throw new ApiException(ApiError.PERMISSION_DENIED, "文件不能为空");
    }
    
    /**
     * 上传资源到RAG知识库
     */
    public boolean uploadResourceToRag(String knowledgeBaseId, String resourceId, MultipartFile file, Map<String, Object> metadata) {
        // 验证知识库是否存在
        KnowledgeBase knowledgeBase = knowledgeBaseMapper.getById(knowledgeBaseId);
        if (knowledgeBase == null || knowledgeBase.getIsDeleted()) {
            throw new ApiException(ApiError.KNOWLEDGE_BASE_NOT_EXISTED, "文件不能为空");
        }
        
        // 验证用户是否有权限
        validateUserPermission(knowledgeBase);
        
        // 验证知识库是否启用RAG
        if (!knowledgeBase.getRagEnabled() || knowledgeBase.getRagKnowledgeId() == null) {
            throw new ApiException(ApiError.RAG_NOT_ENABLED, "文件不能为空");
        }
        
        // 准备资源文件和元数据
        if (file == null || file.isEmpty()) {
            log.error("上传资源失败：文件为空");
            return false;
        }
        
        try {
            // 调用RAG Flow上传资源
            RagDocumentVO document = uploadDocument(knowledgeBase.getRagKnowledgeId(), file);
            
            if (document != null) {
                // 更新知识库RAG文档数量
                knowledgeBase.setRagDocumentCount(knowledgeBase.getRagDocumentCount() + 1);
                knowledgeBase.setUpdatedAt(new Date());
                knowledgeBaseMapper.update(knowledgeBase);
                return true;
            }
            return false;
        } catch (Exception e) {
            log.error("上传资源到RAG知识库失败", e);
            return false;
        }
    }

    /**
     * 通过关键词检索相关文档
     * 
     * @param query 查询关键词
     * @param knowledgeIds 知识库ID列表
     * @param topK 返回结果数量
     * @return 相关文档列表
     */
    public List<RagDocumentVO> retrieveDocuments(String query, List<String> knowledgeIds, int topK) {
        if (!ragFlowConfig.isEnabled() || knowledgeIds.isEmpty()) {
            log.info("RAGFlow未启用或知识库ID列表为空，无法检索文档");
            return new ArrayList<>();
        }
        
        List<RagDocumentVO> results = new ArrayList<>();
        
        try {
            // 对每个知识库进行检索
            for (String knowledgeId : knowledgeIds) {
                String apiUrl = ragFlowConfig.getApiEndpoint() + "/api/knowledge/retrieve";
                
                Map<String, Object> requestBody = new HashMap<>();
                requestBody.put("knowledge_id", knowledgeId);
                requestBody.put("query", query);
                requestBody.put("top_k", Math.min(topK, 5)); // 最多检索5个文档
                
                HttpHeaders headers = getJsonHttpHeaders();
                HttpEntity<Map<String, Object>> httpEntity = new HttpEntity<>(requestBody, headers);
                
                ResponseEntity<Map> responseEntity = restTemplate.exchange(
                    apiUrl, 
                    HttpMethod.POST,
                    httpEntity, 
                    Map.class
                );
                
                if (responseEntity.getStatusCode().is2xxSuccessful() && responseEntity.getBody() != null) {
                    Map<String, Object> responseBody = responseEntity.getBody();
                    Map<String, Object> data = (Map<String, Object>) responseBody.get("data");
                    
                    if (data != null && data.containsKey("results")) {
                        List<Map<String, Object>> retrievedDocs = (List<Map<String, Object>>) data.get("results");
                        
                        for (Map<String, Object> doc : retrievedDocs) {
                            RagDocumentVO documentVO = new RagDocumentVO();
                            documentVO.setId((String) doc.get("id"));
                            documentVO.setKnowledgeId(knowledgeId);
                            documentVO.setFileName((String) doc.get("file_name"));
                            documentVO.setMimeType((String) doc.get("content_type"));
                            documentVO.setScore(((Number) doc.get("score")).doubleValue());
                            documentVO.setMetadata((Map<String, Object>) doc.get("metadata"));
                            
                            results.add(documentVO);
                        }
                    }
                }
            }
            
            // 按相关性分数排序
            results.sort(Comparator.comparing(RagDocumentVO::getScore).reversed());
            
            // 取前topK个结果
            if (results.size() > topK) {
                results = results.subList(0, topK);
            }
            
            log.info("成功检索到{}个相关文档", results.size());
            return results;
        } catch (Exception e) {
            log.error("检索相关文档时发生异常: {}", e.getMessage(), e);
            return new ArrayList<>();
        }
    }

    /**
     * 根据关键词从知识库搜索B站视频资源
     * 
     * @param keywords 关键词
     * @param knowledgeBaseIds 知识库ID列表
     * @param limit 限制返回数量
     * @return 视频资源列表
     */
    public List<KnowledgeResource> searchBilibiliResources(String keywords, List<String> knowledgeBaseIds, int limit) {
        if (knowledgeBaseIds == null || knowledgeBaseIds.isEmpty() || keywords == null) {
            return new ArrayList<>();
        }
        
        List<KnowledgeResource> allResources = new ArrayList<>();
        
        // 从每个知识库中搜集视频资源
        for (String knowledgeBaseId : knowledgeBaseIds) {
            try {
                // 获取该知识库的视频资源
                List<KnowledgeResource> resources = knowledgeResourceMapper.getByKnowledgeBaseIdAndType(
                    knowledgeBaseId, "VIDEO", false);
                
                if (resources != null && !resources.isEmpty()) {
                    allResources.addAll(resources);
                }
            } catch (Exception e) {
                log.error("获取知识库视频资源出错: knowledgeBaseId={}, error={}", knowledgeBaseId, e.getMessage(), e);
            }
        }
        
        // 如果没有资源，直接返回空列表
        if (allResources.isEmpty()) {
            return new ArrayList<>();
        }
        
        // 关键词匹配筛选
        List<KnowledgeResource> matchedResources = allResources.stream()
            .filter(resource -> matchesKeywords(resource, keywords))
            .limit(limit)
            .collect(Collectors.toList());
        
        log.info("根据关键词'{}'匹配到{}个视频资源", keywords, matchedResources.size());
        return matchedResources;
    }
    
    /**
     * 判断资源是否匹配关键词
     */
    private boolean matchesKeywords(KnowledgeResource resource, String keywords) {
        if (resource == null || keywords == null) {
            return false;
        }
        
        // 将关键词分割为多个词语进行匹配
        String[] keywordArray = keywords.toLowerCase().split("\\s+");
        
        // 检查标题
        String title = resource.getTitle() != null ? resource.getTitle().toLowerCase() : "";
        
        // 检查描述
        String description = resource.getDescription() != null ? resource.getDescription().toLowerCase() : "";
        
        // 对每个关键词检查是否匹配
        for (String keyword : keywordArray) {
            if (keyword.length() < 2) continue; // 忽略太短的关键词
            
            if (title.contains(keyword) || description.contains(keyword)) {
                return true;
            }
        }
        
        return false;
    }
} 
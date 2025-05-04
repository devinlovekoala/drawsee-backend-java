package cn.yifan.drawsee.service.business;

import cn.dev33.satoken.stp.StpUtil;
import cn.yifan.drawsee.exception.ApiError;
import cn.yifan.drawsee.exception.ApiException;
import cn.yifan.drawsee.pojo.mongo.Knowledge;
import cn.yifan.drawsee.pojo.mongo.KnowledgeBase;
import cn.yifan.drawsee.repository.KnowledgeBaseRepository;
import cn.yifan.drawsee.repository.KnowledgeRepository;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * @FileName AbstractKnowledgeBaseService
 * @Description 知识库服务抽象基类，提供公共功能
 * @Author devin
 * @date 2025-04-10 08:30
 **/
public abstract class AbstractKnowledgeBaseService {

    @Autowired
    protected KnowledgeBaseRepository knowledgeBaseRepository;
    
    @Autowired
    protected KnowledgeRepository knowledgeRepository;

    /**
     * 验证知识库是否存在
     * @param knowledgeBaseId 知识库ID
     * @return 知识库对象
     */
    protected KnowledgeBase validateKnowledgeBase(String knowledgeBaseId) {
        KnowledgeBase knowledgeBase = knowledgeBaseRepository.findById(knowledgeBaseId).orElse(null);
        if (knowledgeBase == null || knowledgeBase.getIsDeleted()) {
            throw new ApiException(ApiError.KNOWLEDGE_BASE_NOT_EXISTED);
        }
        return knowledgeBase;
    }
    
    /**
     * 验证用户是否有权限访问该知识库
     * @param knowledgeBase 知识库对象
     */
    protected void validateUserAccess(KnowledgeBase knowledgeBase) {
        Long userId = StpUtil.getLoginIdAsLong();
        // 如果是公开的知识库或用户是知识库的成员，允许访问
        if (knowledgeBase.getIsPublished() || knowledgeBase.getMembers().contains(userId)) {
            return;
        }
        throw new ApiException(ApiError.PERMISSION_DENIED);
    }
    
    /**
     * 验证用户是否有编辑权限（仅创建者有权编辑）
     * @param knowledgeBase 知识库对象
     */
    protected void validateUserEditPermission(KnowledgeBase knowledgeBase) {
        Long userId = StpUtil.getLoginIdAsLong();
        if (!knowledgeBase.getCreatorId().equals(userId)) {
            throw new ApiException(ApiError.PERMISSION_DENIED);
        }
    }
    
    /**
     * 验证知识点是否存在
     * @param knowledgeId 知识点ID
     * @return 知识点对象
     */
    protected Knowledge validateKnowledge(String knowledgeId) {
        Knowledge knowledge = knowledgeRepository.findById(knowledgeId).orElse(null);
        if (knowledge == null) {
            throw new ApiException(ApiError.KNOWLEDGE_NOT_EXISTED);
        }
        return knowledge;
    }
    
    /**
     * 验证知识点是否属于知识库
     * @param knowledgeBase 知识库对象
     * @param knowledgeId 知识点ID
     */
    protected void validateKnowledgeBelongsToBase(KnowledgeBase knowledgeBase, String knowledgeId) {
        if (knowledgeBase.getKnowledgeIds() == null || !knowledgeBase.getKnowledgeIds().contains(knowledgeId)) {
            throw new ApiException(ApiError.KNOWLEDGE_NOT_IN_BASE);
        }
    }
} 
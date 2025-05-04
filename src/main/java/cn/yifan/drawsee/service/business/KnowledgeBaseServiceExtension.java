package cn.yifan.drawsee.service.business;

import cn.dev33.satoken.stp.StpUtil;
import cn.yifan.drawsee.exception.ApiError;
import cn.yifan.drawsee.exception.ApiException;
import cn.yifan.drawsee.pojo.dto.AddKnowledgeDTO;
import cn.yifan.drawsee.pojo.dto.UpdateKnowledgeDTO;
import cn.yifan.drawsee.pojo.mongo.Knowledge;
import cn.yifan.drawsee.pojo.mongo.KnowledgeBase;
import cn.yifan.drawsee.pojo.mongo.KnowledgePosition;
import cn.yifan.drawsee.repository.KnowledgeBaseRepository;
import cn.yifan.drawsee.repository.KnowledgeRepository;
import cn.yifan.drawsee.repository.KnowledgePositionRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

/**
 * @FileName KnowledgeBaseServiceExtension
 * @Description 知识库服务扩展类 - 处理知识点相关功能
 * @Author devin
 * @date 2025-03-30 09:15
 **/

@Service
public class KnowledgeBaseServiceExtension {

    @Autowired
    private KnowledgeBaseRepository knowledgeBaseRepository;
    
    @Autowired
    private KnowledgeRepository knowledgeRepository;

    @Autowired
    private KnowledgePositionRepository knowledgePositionRepository;

    /**
     * 获取知识库中的知识点列表
     * @param knowledgeBaseId 知识库ID
     * @return 知识点列表
     */
    public List<Knowledge> getKnowledgeBaseKnowledgePoints(String knowledgeBaseId) {
        // 验证知识库是否存在
        KnowledgeBase knowledgeBase = validateKnowledgeBase(knowledgeBaseId);
        
        // 验证用户是否有权限访问该知识库
        validateUserAccess(knowledgeBase);
        
        // 获取知识库中的知识点列表
        List<String> knowledgeIds = knowledgeBase.getKnowledgeIds();
        if (knowledgeIds == null || knowledgeIds.isEmpty()) {
            return new ArrayList<>();
        }
        
        return knowledgeRepository.findAllByIdIn(knowledgeIds);
    }
    
    /**
     * 在知识库中添加知识点
     * @param knowledgeBaseId 知识库ID
     * @param addKnowledgeDTO 添加知识点DTO
     * @return 知识点ID
     */
    public String addKnowledgeBaseKnowledgePoint(String knowledgeBaseId, AddKnowledgeDTO addKnowledgeDTO) {
        // 验证知识库是否存在
        KnowledgeBase knowledgeBase = validateKnowledgeBase(knowledgeBaseId);
        
        // 验证用户是否有编辑权限
        validateUserEditPermission(knowledgeBase);
        
        // 创建知识点
        Knowledge knowledge = new Knowledge();
        knowledge.setName(addKnowledgeDTO.getName());
        knowledge.setSubject(addKnowledgeDTO.getSubject());
        knowledge.setAliases(addKnowledgeDTO.getAliases());
        knowledge.setLevel(addKnowledgeDTO.getLevel());
        knowledge.setParentId(addKnowledgeDTO.getParentId());
        knowledge.setChildrenIds(new ArrayList<>());
        knowledge.setResources(new ArrayList<>());
        knowledge.setKnowledgeBaseId(knowledgeBaseId);
        
        // 保存知识点
        Knowledge savedKnowledge = knowledgeRepository.save(knowledge);
        
        // 如果有父节点，更新父节点的子节点列表
        if (addKnowledgeDTO.getParentId() != null && !addKnowledgeDTO.getParentId().isEmpty()) {
            Knowledge parent = knowledgeRepository.findById(addKnowledgeDTO.getParentId()).orElse(null);
            if (parent != null) {
                if (parent.getChildrenIds() == null) {
                    parent.setChildrenIds(new ArrayList<>());
                }
                parent.getChildrenIds().add(savedKnowledge.getId());
                knowledgeRepository.save(parent);
            }
        }
        
        // 更新知识库的知识点列表
        if (knowledgeBase.getKnowledgeIds() == null) {
            knowledgeBase.setKnowledgeIds(new ArrayList<>());
        }
        knowledgeBase.getKnowledgeIds().add(savedKnowledge.getId());
        knowledgeBase.setUpdatedAt(new Date());
        knowledgeBaseRepository.save(knowledgeBase);
        
        return savedKnowledge.getId();
    }
    
    /**
     * 更新知识库中的知识点
     * @param knowledgeBaseId 知识库ID
     * @param knowledgeId 知识点ID
     * @param updateKnowledgeDTO 更新知识点DTO
     */
    public void updateKnowledgeBaseKnowledgePoint(String knowledgeBaseId, String knowledgeId, UpdateKnowledgeDTO updateKnowledgeDTO) {
        // 验证知识库是否存在
        KnowledgeBase knowledgeBase = validateKnowledgeBase(knowledgeBaseId);
        
        // 验证用户是否有编辑权限
        validateUserEditPermission(knowledgeBase);
        
        // 验证知识点是否属于该知识库
        validateKnowledgeBelongsToBase(knowledgeBase, knowledgeId);
        
        // 验证知识点是否存在
        Knowledge knowledge = validateKnowledge(knowledgeId);
        
        // 更新知识点属性
        knowledge.setName(updateKnowledgeDTO.getName());
        knowledge.setSubject(updateKnowledgeDTO.getSubject());
        knowledge.setAliases(updateKnowledgeDTO.getAliases());
        knowledge.setLevel(updateKnowledgeDTO.getLevel());
        
        // 更新父节点关系
        String oldParentId = knowledge.getParentId();
        String newParentId = updateKnowledgeDTO.getParentId();
        
        // 如果父节点发生变化
        if ((oldParentId == null && newParentId != null) || 
            (oldParentId != null && !oldParentId.equals(newParentId))) {
            
            // 如果有旧父节点，从其子节点列表中移除
            if (oldParentId != null && !oldParentId.isEmpty()) {
                Knowledge oldParent = knowledgeRepository.findById(oldParentId).orElse(null);
                if (oldParent != null && oldParent.getChildrenIds() != null) {
                    oldParent.getChildrenIds().remove(knowledgeId);
                    knowledgeRepository.save(oldParent);
                }
            }
            
            // 如果有新父节点，添加到其子节点列表中
            if (newParentId != null && !newParentId.isEmpty()) {
                Knowledge newParent = knowledgeRepository.findById(newParentId).orElse(null);
                if (newParent != null) {
                    if (newParent.getChildrenIds() == null) {
                        newParent.setChildrenIds(new ArrayList<>());
                    }
                    newParent.getChildrenIds().add(knowledgeId);
                    knowledgeRepository.save(newParent);
                }
            }
            
            // 更新知识点的父节点ID
            knowledge.setParentId(newParentId);
        }
        
        // 保存更新后的知识点
        knowledgeRepository.save(knowledge);
    }
    
    /**
     * 删除知识库中的知识点
     * @param knowledgeBaseId 知识库ID
     * @param knowledgeId 知识点ID
     */
    public void deleteKnowledgeBaseKnowledgePoint(String knowledgeBaseId, String knowledgeId) {
        // 验证知识库是否存在
        KnowledgeBase knowledgeBase = validateKnowledgeBase(knowledgeBaseId);
        
        // 验证用户是否有编辑权限
        validateUserEditPermission(knowledgeBase);
        
        // 验证知识点是否属于该知识库
        validateKnowledgeBelongsToBase(knowledgeBase, knowledgeId);
        
        // 验证知识点是否存在
        Knowledge knowledge = validateKnowledge(knowledgeId);
        
        // 从知识库的知识点列表中移除
        knowledgeBase.getKnowledgeIds().remove(knowledgeId);
        knowledgeBaseRepository.save(knowledgeBase);
        
        // 如果有父节点，从父节点的子节点列表中移除
        String parentId = knowledge.getParentId();
        if (parentId != null && !parentId.isEmpty()) {
            Knowledge parent = knowledgeRepository.findById(parentId).orElse(null);
            if (parent != null && parent.getChildrenIds() != null) {
                parent.getChildrenIds().remove(knowledgeId);
                knowledgeRepository.save(parent);
            }
        }
        
        // 如果有子节点，更新子节点的父节点引用
        List<String> childrenIds = knowledge.getChildrenIds();
        if (childrenIds != null && !childrenIds.isEmpty()) {
            for (String childId : childrenIds) {
                Knowledge child = knowledgeRepository.findById(childId).orElse(null);
                if (child != null) {
                    child.setParentId(null);
                    knowledgeRepository.save(child);
                }
            }
        }
        
        // 删除知识点
        knowledgeRepository.delete(knowledge);
    }
    
    // 辅助方法
    
    /**
     * 验证知识库是否存在
     * @param knowledgeBaseId 知识库ID
     * @return 知识库对象
     */
    private KnowledgeBase validateKnowledgeBase(String knowledgeBaseId) {
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
    private void validateUserAccess(KnowledgeBase knowledgeBase) {
        Long userId = StpUtil.getLoginIdAsLong();
        // 如果是公开的知识库或用户是知识库的成员，允许访问
        if (knowledgeBase.getIsPublished() || knowledgeBase.getMembers().contains(userId)) {
            return;
        }
        throw new ApiException(ApiError.PERMISSION_DENIED);
    }
    
    /**
     * 验证用户是否有编辑权限（仅限知识库创建者）
     * @param knowledgeBase 知识库对象
     */
    private void validateUserEditPermission(KnowledgeBase knowledgeBase) {
        Long userId = StpUtil.getLoginIdAsLong();
        if (!knowledgeBase.getCreatorId().equals(userId)) {
            throw new ApiException(ApiError.PERMISSION_DENIED);
        }
    }
    
    /**
     * 验证知识点是否属于该知识库
     * @param knowledgeBase 知识库对象
     * @param knowledgeId 知识点ID
     */
    private void validateKnowledgeBelongsToBase(KnowledgeBase knowledgeBase, String knowledgeId) {
        if (knowledgeBase.getKnowledgeIds() == null || !knowledgeBase.getKnowledgeIds().contains(knowledgeId)) {
            throw new ApiException(ApiError.KNOWLEDGE_NOT_IN_BASE);
        }
    }
    
    /**
     * 验证知识点是否存在
     * @param knowledgeId 知识点ID
     * @return 知识点对象
     */
    private Knowledge validateKnowledge(String knowledgeId) {
        Knowledge knowledge = knowledgeRepository.findById(knowledgeId).orElse(null);
        if (knowledge == null) {
            throw new ApiException(ApiError.KNOWLEDGE_NOT_EXISTED);
        }
        return knowledge;
    }
}
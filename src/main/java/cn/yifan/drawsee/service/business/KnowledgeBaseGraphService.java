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
import cn.yifan.drawsee.repository.KnowledgePositionRepository;
import cn.yifan.drawsee.repository.KnowledgeRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * @FileName KnowledgeBaseGraphService
 * @Description 知识库图谱服务类 - 处理知识点图谱相关功能
 * @Author devin
 * @date 2025-03-30 12:15
 **/

@Service
public class KnowledgeBaseGraphService extends AbstractKnowledgeBaseService {

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
        
        // 验证知识点是否存在于该知识库中
        Knowledge knowledge = validateKnowledgeInKnowledgeBase(knowledgeBase, knowledgeId);
        
        // 更新知识点
        if (updateKnowledgeDTO.getName() != null) {
            knowledge.setName(updateKnowledgeDTO.getName());
        }
        if (updateKnowledgeDTO.getSubject() != null) {
            knowledge.setSubject(updateKnowledgeDTO.getSubject());
        }
        if (updateKnowledgeDTO.getAliases() != null) {
            knowledge.setAliases(updateKnowledgeDTO.getAliases());
        }
        if (updateKnowledgeDTO.getLevel() != null) {
            knowledge.setLevel(updateKnowledgeDTO.getLevel());
        }
        
        // 如果更改了父节点，需要更新父子关系
        if (updateKnowledgeDTO.getParentId() != null 
                && !Objects.equals(updateKnowledgeDTO.getParentId(), knowledge.getParentId())) {
            
            // 如果有旧的父节点，从旧父节点的子节点列表中移除
            if (knowledge.getParentId() != null && !knowledge.getParentId().isEmpty()) {
                Knowledge oldParent = knowledgeRepository.findById(knowledge.getParentId()).orElse(null);
                if (oldParent != null && oldParent.getChildrenIds() != null) {
                    oldParent.getChildrenIds().remove(knowledgeId);
                    knowledgeRepository.save(oldParent);
                }
            }
            
            // 设置新的父节点
            knowledge.setParentId(updateKnowledgeDTO.getParentId());
            
            // 如果有新的父节点，添加到新父节点的子节点列表中
            if (!updateKnowledgeDTO.getParentId().isEmpty()) {
                Knowledge newParent = knowledgeRepository.findById(updateKnowledgeDTO.getParentId()).orElse(null);
                if (newParent != null) {
                    if (newParent.getChildrenIds() == null) {
                        newParent.setChildrenIds(new ArrayList<>());
                    }
                    if (!newParent.getChildrenIds().contains(knowledgeId)) {
                        newParent.getChildrenIds().add(knowledgeId);
                        knowledgeRepository.save(newParent);
                    }
                }
            }
        }
        
        // 保存知识点
        knowledgeRepository.save(knowledge);
        
        // 更新知识库的更新时间
        knowledgeBase.setUpdatedAt(new Date());
        knowledgeBaseRepository.save(knowledgeBase);
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
        
        // 验证知识点是否存在于该知识库中
        Knowledge knowledge = validateKnowledgeInKnowledgeBase(knowledgeBase, knowledgeId);
        
        // 如果有父节点，从父节点的子节点列表中移除
        if (knowledge.getParentId() != null && !knowledge.getParentId().isEmpty()) {
            Knowledge parent = knowledgeRepository.findById(knowledge.getParentId()).orElse(null);
            if (parent != null && parent.getChildrenIds() != null) {
                parent.getChildrenIds().remove(knowledgeId);
                knowledgeRepository.save(parent);
            }
        }
        
        // 递归删除所有子节点
        if (knowledge.getChildrenIds() != null && !knowledge.getChildrenIds().isEmpty()) {
            for (String childId : new ArrayList<>(knowledge.getChildrenIds())) {
                deleteKnowledgeBaseKnowledgePoint(knowledgeBaseId, childId);
            }
        }
        
        // 删除知识点位置信息
        knowledgePositionRepository.deleteByKnowledgeId(knowledgeId);
        
        // 删除知识点
        knowledgeRepository.deleteById(knowledgeId);
        
        // 更新知识库的知识点列表
        knowledgeBase.getKnowledgeIds().remove(knowledgeId);
        knowledgeBase.setUpdatedAt(new Date());
        knowledgeBaseRepository.save(knowledgeBase);
    }
    
    /**
     * 获取知识库中的知识图谱数据
     * @param knowledgeBaseId 知识库ID
     * @return 知识图谱数据（节点和连接）
     */
    @SuppressWarnings("unchecked")
    public Map<String, Object> getKnowledgeBaseGraph(String knowledgeBaseId) {
        // 验证知识库是否存在
        KnowledgeBase knowledgeBase = validateKnowledgeBase(knowledgeBaseId);
        
        // 验证用户是否有权限访问该知识库
        validateUserAccess(knowledgeBase);
        
        // 获取知识库中的知识点列表
        List<Knowledge> knowledgePoints = getKnowledgeBaseKnowledgePoints(knowledgeBaseId);
        
        // 获取知识点位置列表
        List<KnowledgePosition> positions = knowledgePositionRepository.findAllByKnowledgeBaseId(knowledgeBaseId);
        Map<String, KnowledgePosition> positionMap = new HashMap<>();
        for (KnowledgePosition position : positions) {
            positionMap.put(position.getKnowledgeId(), position);
        }
        
        // 准备返回数据
        Map<String, Object> result = new HashMap<>();
        
        // 准备节点数据
        List<Map<String, Object>> nodes = new ArrayList<>();
        for (Knowledge knowledge : knowledgePoints) {
            Map<String, Object> node = new HashMap<>();
            node.put("id", knowledge.getId());
            node.put("type", "knowledge");
            
            Map<String, Object> data = new HashMap<>();
            data.put("label", knowledge.getName());
            data.put("level", knowledge.getLevel());
            data.put("subject", knowledge.getSubject());
            data.put("hasResources", knowledge.getResources() != null && !knowledge.getResources().isEmpty());
            
            // 获取节点位置信息（如果存在）
            KnowledgePosition position = positionMap.get(knowledge.getId());
            if (position != null) {
                data.put("x", position.getX());
                data.put("y", position.getY());
            }
            
            node.put("data", data);
            nodes.add(node);
        }
        
        // 准备边数据
        List<Map<String, Object>> edges = new ArrayList<>();
        
        // 添加父子关系边
        for (Knowledge knowledge : knowledgePoints) {
            if (knowledge.getParentId() != null && !knowledge.getParentId().isEmpty()) {
                Map<String, Object> edge = new HashMap<>();
                edge.put("id", "e-" + knowledge.getParentId() + "-" + knowledge.getId());
                edge.put("source", knowledge.getParentId());
                edge.put("target", knowledge.getId());
                
                Map<String, Object> data = new HashMap<>();
                data.put("type", "parent-child");
                
                edge.put("data", data);
                edges.add(edge);
            }
        }
        
        result.put("nodes", nodes);
        result.put("edges", edges);
        
        return result;
    }
    
    /**
     * 批量更新知识图谱节点位置
     * @param knowledgeBaseId 知识库ID
     * @param nodePositions 节点位置信息
     */
    @SuppressWarnings("unchecked")
    public void updateKnowledgeBaseNodePositions(String knowledgeBaseId, Map<String, Object> nodePositions) {
        // 验证知识库是否存在
        KnowledgeBase knowledgeBase = validateKnowledgeBase(knowledgeBaseId);
        
        // 验证用户是否有编辑权限
        validateUserEditPermission(knowledgeBase);
        
        // 获取节点位置数据
        List<Map<String, Object>> positions = (List<Map<String, Object>>) nodePositions.get("positions");
        if (positions == null || positions.isEmpty()) {
            return;
        }
        
        // 获取当前已存在的位置信息
        List<KnowledgePosition> existingPositions = knowledgePositionRepository.findAllByKnowledgeBaseId(knowledgeBaseId);
        Map<String, KnowledgePosition> existingPositionMap = new HashMap<>();
        for (KnowledgePosition position : existingPositions) {
            existingPositionMap.put(position.getKnowledgeId(), position);
        }
        
        // 准备新的或更新的节点位置
        Date now = new Date();
        List<KnowledgePosition> positionsToSave = new ArrayList<>();
        for (Map<String, Object> position : positions) {
            String knowledgeId = (String) position.get("id");
            Double x = ((Number) position.get("x")).doubleValue();
            Double y = ((Number) position.get("y")).doubleValue();
            
            // 查找是否已存在位置信息
            KnowledgePosition knowledgePosition = existingPositionMap.get(knowledgeId);
            if (knowledgePosition == null) {
                // 创建新的位置信息
                knowledgePosition = new KnowledgePosition();
                knowledgePosition.setKnowledgeId(knowledgeId);
                knowledgePosition.setKnowledgeBaseId(knowledgeBaseId);
                knowledgePosition.setCreatedAt(now);
            }
            
            // 更新位置信息
            knowledgePosition.setX(x);
            knowledgePosition.setY(y);
            knowledgePosition.setUpdatedAt(now);
            
            positionsToSave.add(knowledgePosition);
        }
        
        // 保存位置信息到MongoDB
        knowledgePositionRepository.saveAll(positionsToSave);
        
        // 更新知识库的更新时间
        knowledgeBase.setUpdatedAt(now);
        knowledgeBaseRepository.save(knowledgeBase);
    }
    
    /**
     * 获取知识库中所有知识点的位置信息
     * @param knowledgeBaseId 知识库ID
     * @return 位置信息列表
     */
    public List<KnowledgePosition> getKnowledgeBaseNodePositions(String knowledgeBaseId) {
        // 验证知识库是否存在
        KnowledgeBase knowledgeBase = validateKnowledgeBase(knowledgeBaseId);
        
        // 验证用户是否有权限访问该知识库
        validateUserAccess(knowledgeBase);
        
        // 获取位置信息
        return knowledgePositionRepository.findAllByKnowledgeBaseId(knowledgeBaseId);
    }
    
    /**
     * 获取特定知识点的位置信息
     * @param knowledgeBaseId 知识库ID
     * @param knowledgeId 知识点ID
     * @return 位置信息
     */
    public KnowledgePosition getKnowledgeNodePosition(String knowledgeBaseId, String knowledgeId) {
        // 验证知识库是否存在
        KnowledgeBase knowledgeBase = validateKnowledgeBase(knowledgeBaseId);
        
        // 验证用户是否有权限访问该知识库
        validateUserAccess(knowledgeBase);
        
        // 验证知识点是否存在于该知识库中
        validateKnowledgeInKnowledgeBase(knowledgeBase, knowledgeId);
        
        // 获取位置信息
        return knowledgePositionRepository.findByKnowledgeBaseIdAndKnowledgeId(knowledgeBaseId, knowledgeId);
    }
}
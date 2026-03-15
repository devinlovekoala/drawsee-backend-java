package cn.yifan.drawsee.service.business;

import cn.yifan.drawsee.mapper.KnowledgeBaseMapper;
import cn.yifan.drawsee.pojo.entity.KnowledgeBase;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * 班级知识库服务 负责管理班级与知识库的关联关系，以及RAG检索时的知识库过滤
 *
 * @author yifan
 * @date 2025-10-10
 */
@Service
@Slf4j
public class ClassKnowledgeService {

  @Autowired private KnowledgeBaseMapper knowledgeBaseMapper;

  /**
   * 根据班级ID获取可用的知识库列表
   *
   * @param classId 班级ID
   * @return 知识库ID列表
   */
  public List<String> getKnowledgeBaseIdsByClassId(Long classId) {
    if (classId == null) {
      log.warn("班级ID为空，返回空列表");
      return new ArrayList<>();
    }

    try {
      // 查询属于该班级且启用RAG的知识库
      List<KnowledgeBase> knowledgeBases = knowledgeBaseMapper.listByClassId(classId);

      return knowledgeBases.stream()
          .filter(kb -> kb.getRagEnabled() != null && kb.getRagEnabled())
          .filter(kb -> kb.getRagDocumentCount() != null && kb.getRagDocumentCount() > 0)
          .filter(kb -> kb.getIsDeleted() == null || !kb.getIsDeleted())
          .map(KnowledgeBase::getId)
          .collect(Collectors.toList());

    } catch (Exception e) {
      log.error("获取班级知识库失败, classId={}", classId, e);
      return new ArrayList<>();
    }
  }

  /**
   * 根据班级ID和用户ID获取可用的知识库列表 包括班级知识库和用户可访问的个人知识库
   *
   * @param classId 班级ID
   * @param userId 用户ID
   * @return 知识库ID列表
   */
  public List<String> getAccessibleKnowledgeBaseIds(Long classId, Long userId) {
    List<String> knowledgeBaseIds = new ArrayList<>();

    // 1. 获取班级相关的知识库
    if (classId != null) {
      List<String> classKnowledgeBases = getKnowledgeBaseIdsByClassId(classId);
      knowledgeBaseIds.addAll(classKnowledgeBases);
      log.info("班级 {} 的知识库数量: {}", classId, classKnowledgeBases.size());
    }

    // 2. 获取用户个人的知识库（可选扩展）
    if (userId != null) {
      try {
        List<KnowledgeBase> userKnowledgeBases = knowledgeBaseMapper.listByCreatorId(userId);
        List<String> userKnowledgeBaseIds =
            userKnowledgeBases.stream()
                .filter(kb -> kb.getRagEnabled() != null && kb.getRagEnabled())
                .filter(kb -> kb.getRagDocumentCount() != null && kb.getRagDocumentCount() > 0)
                .filter(kb -> kb.getIsDeleted() == null || !kb.getIsDeleted())
                .filter(
                    kb -> kb.getClassIds() == null || kb.getClassIds().isEmpty()) // 排除已分配给班级的知识库
                .map(KnowledgeBase::getId)
                .collect(Collectors.toList());
        knowledgeBaseIds.addAll(userKnowledgeBaseIds);
        log.info("用户 {} 的个人知识库数量: {}", userId, userKnowledgeBaseIds.size());
      } catch (Exception e) {
        log.warn("获取用户个人知识库失败, userId={}", userId, e);
      }
    }

    // 去重
    knowledgeBaseIds = knowledgeBaseIds.stream().distinct().collect(Collectors.toList());

    log.info("用户 {} 在班级 {} 中可访问的知识库总数: {}", userId, classId, knowledgeBaseIds.size());
    return knowledgeBaseIds;
  }

  /**
   * 将知识库分配给班级
   *
   * @param knowledgeBaseId 知识库ID
   * @param classId 班级ID
   */
  public void assignKnowledgeBaseToClass(String knowledgeBaseId, Long classId) {
    try {
      KnowledgeBase knowledgeBase = knowledgeBaseMapper.getById(knowledgeBaseId);
      if (knowledgeBase == null) {
        log.warn("知识库不存在: {}", knowledgeBaseId);
        return;
      }

      List<Long> classIds =
          knowledgeBase.getClassIds() != null
              ? new ArrayList<>(knowledgeBase.getClassIds())
              : new ArrayList<>();

      if (!classIds.contains(classId)) {
        classIds.add(classId);
        knowledgeBase.setClassIds(classIds);
        knowledgeBaseMapper.update(knowledgeBase);
        log.info("知识库 {} 已分配给班级 {}", knowledgeBaseId, classId);
      } else {
        log.info("知识库 {} 已存在班级关联 {}, 跳过更新", knowledgeBaseId, classId);
      }
    } catch (Exception e) {
      log.error("分配知识库到班级失败, knowledgeBaseId={}, classId={}", knowledgeBaseId, classId, e);
    }
  }

  /**
   * 取消知识库与班级的关联
   *
   * @param knowledgeBaseId 知识库ID
   */
  public void unassignKnowledgeBaseFromClass(String knowledgeBaseId) {
    try {
      KnowledgeBase knowledgeBase = knowledgeBaseMapper.getById(knowledgeBaseId);
      if (knowledgeBase == null) {
        log.warn("知识库不存在: {}", knowledgeBaseId);
        return;
      }

      List<Long> previousClassIds = knowledgeBase.getClassIds();
      knowledgeBase.setClassIds(new ArrayList<>());
      knowledgeBaseMapper.update(knowledgeBase);

      log.info("知识库 {} 已移除所有班级关联: {}", knowledgeBaseId, previousClassIds);
    } catch (Exception e) {
      log.error("取消知识库班级关联失败, knowledgeBaseId={}", knowledgeBaseId, e);
    }
  }
}

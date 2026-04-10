package cn.yifan.drawsee.service.business;

import cn.dev33.satoken.stp.StpUtil;
import cn.yifan.drawsee.constant.UserRole;
import cn.yifan.drawsee.exception.ApiError;
import cn.yifan.drawsee.exception.ApiException;
import cn.yifan.drawsee.mapper.KnowledgeBaseMapper;
import cn.yifan.drawsee.pojo.entity.KnowledgeBase;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * @FileName AbstractKnowledgeBaseService @Description 知识库服务抽象基类，提供公共功能 @Author yifan
 *
 * @date 2025-04-10 08:30
 */
public abstract class AbstractKnowledgeBaseService {

  protected final Logger logger = LoggerFactory.getLogger(getClass());

  @Autowired protected KnowledgeBaseMapper knowledgeBaseMapper;

  @Autowired protected UserRoleService userRoleService;

  /**
   * 验证知识库是否存在
   *
   * @param id 知识库ID
   * @return 知识库对象
   */
  protected KnowledgeBase validateKnowledgeBase(String id) {
    KnowledgeBase knowledgeBase = knowledgeBaseMapper.getById(id);
    if (knowledgeBase == null || knowledgeBase.getIsDeleted()) {
      throw new ApiException(ApiError.KNOWLEDGE_BASE_NOT_EXISTED, "文件不能为空");
    }
    return knowledgeBase;
  }

  /**
   * 验证用户是否有权限访问知识库
   *
   * @param knowledgeBase 知识库对象
   */
  protected void validateUserAccess(KnowledgeBase knowledgeBase) {
    Long userId = StpUtil.getLoginIdAsLong();
    String userRole = userRoleService.getCurrentUserRole();

    // 管理员可以访问所有知识库
    if (UserRole.ADMIN.equals(userRole)) {
      return;
    }

    // 创建者可以访问
    if (knowledgeBase.getCreatorId().equals(userId)) {
      return;
    }

    // 成员可以访问
    if (knowledgeBase.getMembers() != null && knowledgeBase.getMembers().contains(userId)) {
      return;
    }

    // 如果知识库已发布，任何人都可以访问
    if (Boolean.TRUE.equals(knowledgeBase.getIsPublished())) {
      return;
    }

    throw new ApiException(ApiError.PERMISSION_DENIED, "文件不能为空");
  }

  /**
   * 验证用户是否有权限编辑知识库
   *
   * @param knowledgeBase 知识库对象
   */
  protected void validateUserEditPermission(KnowledgeBase knowledgeBase) {
    Long userId = StpUtil.getLoginIdAsLong();
    String userRole = userRoleService.getCurrentUserRole();

    // 管理员可以编辑所有知识库
    if (UserRole.ADMIN.equals(userRole)) {
      return;
    }

    // 创建者可以编辑
    if (knowledgeBase.getCreatorId().equals(userId)) {
      return;
    }

    throw new ApiException(ApiError.PERMISSION_DENIED, "文件不能为空");
  }
}

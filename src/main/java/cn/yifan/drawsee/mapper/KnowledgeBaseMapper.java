package cn.yifan.drawsee.mapper;

import cn.yifan.drawsee.pojo.entity.KnowledgeBase;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 知识库Mapper接口
 * 
 * @author yifan
 * @date 2025-03-28 19:30
 */
@Mapper
public interface KnowledgeBaseMapper {

    /**
     * 插入知识库记录
     * 
     * @param knowledgeBase 知识库对象
     * @return 影响行数
     */
    int insert(KnowledgeBase knowledgeBase);
    
    /**
     * 根据ID查询
     * 
     * @param id 知识库ID
     * @return 知识库对象
     */
    KnowledgeBase getById(String id);
    
    /**
     * 根据ID删除（物理删除）
     * 
     * @param id 知识库ID
     * @return 影响行数
     */
    int deleteById(String id);
    
    /**
     * 更新知识库
     * 
     * @param knowledgeBase 知识库对象
     * @return 影响行数
     */
    int update(KnowledgeBase knowledgeBase);
    
    /**
     * 根据名称查询
     * 
     * @param name 知识库名称
     * @return 知识库对象
     */
    KnowledgeBase getByName(String name);
    
    /**
     * 根据创建者ID查询列表
     * 
     * @param creatorId 创建者ID
     * @param isDeleted 是否删除
     * @return 知识库列表
     */
    List<KnowledgeBase> getByCreatorId(@Param("creatorId") Long creatorId, @Param("isDeleted") Boolean isDeleted);
    
    /**
     * 根据邀请码查询
     * 
     * @param invitationCode 邀请码
     * @return 知识库对象
     */
    KnowledgeBase getByInvitationCode(String invitationCode);
    
    /**
     * 根据RAG知识库ID查询
     * 
     * @param ragKnowledgeId RAG知识库ID
     * @return 知识库对象
     */
    KnowledgeBase getByRagKnowledgeId(String ragKnowledgeId);
    
    /**
     * 查询所有知识库
     * 
     * @param isDeleted 是否删除
     * @return 知识库列表
     */
    List<KnowledgeBase> getAll(@Param("isDeleted") Boolean isDeleted);
    
    /**
     * 查询用户作为成员的知识库
     * 
     * @param userId 用户ID
     * @param isDeleted 是否删除
     * @return 知识库列表
     */
    List<KnowledgeBase> getByMemberId(@Param("userId") Long userId, @Param("isDeleted") Boolean isDeleted);
    
    /**
     * 根据创建者ID和RAG启用状态查询
     * 
     * @param creatorId 创建者ID
     * @param ragEnabled RAG启用状态
     * @param isDeleted 是否删除
     * @return 知识库列表
     */
    List<KnowledgeBase> getByCreatorIdAndRagEnabled(
        @Param("creatorId") Long creatorId, 
        @Param("ragEnabled") Boolean ragEnabled,
        @Param("isDeleted") Boolean isDeleted
    );

    /**
     * 根据创建者ID和RAG启用状态查询知识库
     * @param creatorId 创建者ID
     * @param ragEnabled 是否启用RAG
     * @param isDeleted 是否已删除
     * @return 知识库列表
     */
    List<KnowledgeBase> getByCreatorIdAndRagEnabled(@Param("creatorId") Long creatorId, 
                                                   @Param("ragEnabled") boolean ragEnabled,
                                                   @Param("isDeleted") boolean isDeleted);

    /**
     * 根据成员ID和RAG启用状态查询知识库
     * @param memberId 成员ID
     * @param ragEnabled 是否启用RAG
     * @param isDeleted 是否已删除
     * @return 知识库列表
     */
    List<KnowledgeBase> getByMemberIdAndRagEnabled(@Param("memberId") Long memberId, 
                                                  @Param("ragEnabled") boolean ragEnabled,
                                                  @Param("isDeleted") boolean isDeleted);
    
    /**
     * 查询所有已发布的知识库
     * @return 知识库列表
     */
    List<KnowledgeBase> getByIsPublishedTrue();
    
    /**
     * 根据班级ID查询知识库列表
     * 
     * @param classId 班级ID
     * @return 知识库列表
     */
    List<KnowledgeBase> listByClassId(@Param("classId") Long classId);
    
    /**
     * 根据创建者ID查询知识库列表
     * 
     * @param creatorId 创建者ID
     * @return 知识库列表
     */
    List<KnowledgeBase> listByCreatorId(@Param("creatorId") Long creatorId);
} 

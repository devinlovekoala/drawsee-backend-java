package cn.yifan.drawsee.mapper;

import cn.yifan.drawsee.pojo.entity.Knowledge;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 知识点Mapper接口
 *
 * @author devin
 * @date 2025-04-15 15:35
 */
@Mapper
public interface KnowledgeMapper {

  /**
   * 插入知识点记录
   *
   * @param knowledge 知识点对象
   * @return 影响行数
   */
  int insert(Knowledge knowledge);

  /**
   * 根据ID查询
   *
   * @param id 知识点ID
   * @return 知识点对象
   */
  Knowledge getById(String id);

  /**
   * 根据ID删除（物理删除）
   *
   * @param id 知识点ID
   * @return 影响行数
   */
  int deleteById(String id);

  /**
   * 更新知识点
   *
   * @param knowledge 知识点对象
   * @return 影响行数
   */
  int update(Knowledge knowledge);

  /**
   * 根据名称查询
   *
   * @param name 知识点名称
   * @return 知识点对象
   */
  Knowledge getByName(String name);

  /**
   * 根据学科查询所有知识点
   *
   * @param subject 学科
   * @param isDeleted 是否删除
   * @return 知识点列表
   */
  List<Knowledge> getBySubject(
      @Param("subject") String subject, @Param("isDeleted") Boolean isDeleted);

  /**
   * 根据学科和名称查询
   *
   * @param subject 学科
   * @param name 知识点名称
   * @param isDeleted 是否删除
   * @return 知识点对象
   */
  Knowledge getBySubjectAndName(
      @Param("subject") String subject,
      @Param("name") String name,
      @Param("isDeleted") Boolean isDeleted);

  /**
   * 根据知识库ID查询知识点列表
   *
   * @param knowledgeBaseId 知识库ID
   * @param isDeleted 是否删除
   * @return 知识点列表
   */
  List<Knowledge> getByKnowledgeBaseId(
      @Param("knowledgeBaseId") String knowledgeBaseId, @Param("isDeleted") Boolean isDeleted);

  /**
   * 根据创建者ID查询知识点列表
   *
   * @param creatorId 创建者ID
   * @param isDeleted 是否删除
   * @return 知识点列表
   */
  List<Knowledge> getByCreatorId(
      @Param("creatorId") Long creatorId, @Param("isDeleted") Boolean isDeleted);

  /**
   * 查询所有知识点
   *
   * @param isDeleted 是否删除
   * @return 知识点列表
   */
  List<Knowledge> getAll(@Param("isDeleted") Boolean isDeleted);

  /**
   * 根据父知识点ID查询子知识点列表
   *
   * @param parentId 父知识点ID
   * @param isDeleted 是否删除
   * @return 知识点列表
   */
  List<Knowledge> getByParentId(
      @Param("parentId") String parentId, @Param("isDeleted") Boolean isDeleted);

  /**
   * 逻辑删除知识点
   *
   * @param id 知识点ID
   * @return 影响行数
   */
  int logicDelete(@Param("id") String id);
}

package cn.yifan.drawsee.mapper;

import java.util.List;
import java.util.Map;
import org.apache.ibatis.annotations.*;

/**
 * @FileName CircuitDesignMapper @Description 电路设计数据库映射接口 @Author yifan
 *
 * @date 2025-07-18 16:20
 */
@Mapper
public interface CircuitDesignMapper {

  /**
   * 保存电路设计
   *
   * @param params 参数Map
   * @return 影响的行数
   */
  @Insert(
      "INSERT INTO circuit_design (user_id, title, description, data, created_at, updated_at) "
          + "VALUES (#{userId}, #{title}, #{description}, #{data}, NOW(), NOW())")
  @Options(useGeneratedKeys = true, keyProperty = "id")
  int saveCircuitDesign(Map<String, Object> params);

  /**
   * 更新电路设计
   *
   * @param params 参数Map
   * @return 影响的行数
   */
  @Update(
      "UPDATE circuit_design SET title = #{title}, description = #{description}, "
          + "data = #{data}, updated_at = NOW() WHERE id = #{id} AND user_id = #{userId} AND is_deleted = 0")
  int updateCircuitDesign(Map<String, Object> params);

  /**
   * 根据ID获取电路设计
   *
   * @param id 电路设计ID
   * @return 电路设计数据
   */
  @Select(
      "SELECT id, user_id, title, description, data, created_at, updated_at "
          + "FROM circuit_design WHERE id = #{id} AND is_deleted = 0")
  Map<String, Object> getCircuitDesignById(@Param("id") Long id);

  /**
   * 获取用户的所有电路设计
   *
   * @param userId 用户ID
   * @return 电路设计列表
   */
  @Select(
      "SELECT id, title, description, created_at, updated_at "
          + "FROM circuit_design WHERE user_id = #{userId} AND is_deleted = 0 ORDER BY updated_at DESC")
  List<Map<String, Object>> getCircuitDesignsByUserId(@Param("userId") Long userId);

  /**
   * 逻辑删除电路设计
   *
   * @param id 电路设计ID
   * @param userId 用户ID
   * @return 影响的行数
   */
  @Update(
      "UPDATE circuit_design SET is_deleted = 1, updated_at = NOW() "
          + "WHERE id = #{id} AND user_id = #{userId} AND is_deleted = 0")
  int deleteCircuitDesign(@Param("id") Long id, @Param("userId") Long userId);
}

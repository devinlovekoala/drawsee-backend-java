package cn.yifan.drawsee.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * @FileName UserRoleMapper
 * @Description 用户角色Mapper接口
 * @Author yifan
 * @date 2025-06-12 10:15
 **/

@Mapper
public interface UserRoleMapper {

    /**
     * 根据用户ID查询用户角色
     * @param userId 用户ID
     * @return 角色标识符(admin、teacher、user等)
     */
    String getRoleByUserId(@Param("userId") Long userId);
    
    /**
     * 判断用户是否为管理员
     * @param userId 用户ID
     * @return 是否为管理员
     */
    Boolean isAdmin(@Param("userId") Long userId);
    
    /**
     * 判断用户是否为教师
     * @param userId 用户ID
     * @return 是否为教师
     */
    Boolean isTeacher(@Param("userId") Long userId);
    
} 
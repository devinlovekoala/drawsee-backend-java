package cn.yifan.drawsee.service.business;

import cn.dev33.satoken.stp.StpUtil;
import cn.yifan.drawsee.constant.UserRole;
import cn.yifan.drawsee.mapper.UserRoleMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * @FileName UserRoleService
 * @Description 用户角色服务类
 * @Author yifan
 * @date 2025-03-30 14:25
 **/

@Service
public class UserRoleService {

    @Autowired
    private UserRoleMapper userRoleMapper;

    /**
     * 获取当前登录用户角色
     * @return 角色标识符
     */
    public String getCurrentUserRole() {
        Long userId = StpUtil.getLoginIdAsLong();
        
        // 从session中获取角色，如果已缓存
        String roleFromSession = (String) StpUtil.getSessionByLoginId(userId).get("role");
        if (roleFromSession != null) {
            return roleFromSession;
        }
        
        // 从数据库查询用户角色
        String role = userRoleMapper.getRoleByUserId(userId);
        
        // 将角色缓存到session
        StpUtil.getSessionByLoginId(userId).set("role", role);
        
        return role != null ? role : UserRole.USER; // 默认为普通用户
    }
    
    /**
     * 判断当前登录用户是否为管理员
     * @return 是否为管理员
     */
    public boolean isCurrentUserAdmin() {
        Long userId = StpUtil.getLoginIdAsLong();
        return userRoleMapper.isAdmin(userId);
    }
    
    /**
     * 判断当前登录用户是否为教师
     * @return 是否为教师
     */
    public boolean isCurrentUserTeacher() {
        Long userId = StpUtil.getLoginIdAsLong();
        return userRoleMapper.isTeacher(userId);
    }
    
    /**
     * 判断指定用户是否为管理员
     * @param userId 用户ID
     * @return 是否为管理员
     */
    public boolean isUserAdmin(Long userId) {
        return userRoleMapper.isAdmin(userId);
    }
    
    /**
     * 判断指定用户是否为教师
     * @param userId 用户ID
     * @return 是否为教师
     */
    public boolean isUserTeacher(Long userId) {
        return userRoleMapper.isTeacher(userId);
    }
    
    /**
     * 获取指定用户的角色
     * @param userId 用户ID
     * @return 角色标识符
     */
    public String getUserRole(Long userId) {
        String role = userRoleMapper.getRoleByUserId(userId);
        return role != null ? role : UserRole.USER;
    }
} 
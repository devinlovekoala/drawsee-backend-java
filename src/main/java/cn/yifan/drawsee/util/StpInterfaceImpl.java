package cn.yifan.drawsee.util;

import cn.dev33.satoken.stp.StpInterface;
import cn.yifan.drawsee.constant.UserRole;
import cn.yifan.drawsee.mapper.AdminMapper;
import cn.yifan.drawsee.mapper.TeacherMapper;
import cn.yifan.drawsee.mapper.UserMapper;
import cn.yifan.drawsee.pojo.entity.Admin;
import cn.yifan.drawsee.pojo.entity.Teacher;
import cn.yifan.drawsee.pojo.entity.User;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * @FileName StpInterfaceImpl
 * @Description
 * @Author yifan
 * @date 2025-03-25 12:39
 **/

@Component
public class StpInterfaceImpl implements StpInterface {

    @Autowired
    private UserMapper userMapper;
    
    @Autowired
    private AdminMapper adminMapper;
    
    @Autowired
    private TeacherMapper teacherMapper;

    @Override
    public List<String> getPermissionList(Object loginId, String loginType) {
        return List.of();
    }

    @Override
    public List<String> getRoleList(Object loginId, String loginType) {
        Long userId = Long.valueOf((String) loginId);
        
        // 确保用户存在
        User user = userMapper.getById(userId);
        if (user == null) {
            return List.of();
        }
        
        List<String> roles = new ArrayList<>();
        roles.add(UserRole.USER); // 所有用户都有USER角色
        
        // 检查是否是管理员
        Admin admin = adminMapper.getByUserId(userId);
        if (admin != null) {
            roles.add(UserRole.ADMIN);
        }
        
        // 检查是否是教师
        Teacher teacher = teacherMapper.getByUserId(userId);
        if (teacher != null) {
            roles.add(UserRole.TEACHER);
        }
        
        return roles;
    }
}

package cn.yifan.drawsee.util;

import cn.dev33.satoken.stp.StpInterface;
import cn.yifan.drawsee.constant.UserRole;
import cn.yifan.drawsee.mapper.UserMapper;
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

    @Override
    public List<String> getPermissionList(Object loginId, String loginType) {
        return List.of();
    }

    @Override
    public List<String> getRoleList(Object loginId, String loginType) {
        Long userId = Long.valueOf((String) loginId);
        User user = userMapper.getById(userId);
        List<String> admins = new ArrayList<>();
        admins.add("test1");
        if (user != null && admins.contains(user.getUsername())) {
            return List.of(UserRole.ADMIN);
        }
        return List.of(UserRole.USER);
    }

}

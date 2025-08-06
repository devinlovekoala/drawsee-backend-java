package cn.yifan.drawsee.service.business;

import cn.dev33.satoken.stp.SaTokenInfo;
import cn.dev33.satoken.stp.StpUtil;
import cn.yifan.drawsee.constant.AiTaskLimit;
import cn.yifan.drawsee.constant.UserRole;
import cn.yifan.drawsee.exception.ApiError;
import cn.yifan.drawsee.exception.ApiException;
import cn.yifan.drawsee.mapper.UserMapper;
import cn.yifan.drawsee.pojo.dto.UserLoginDTO;
import cn.yifan.drawsee.pojo.dto.UserSignUpDTO;
import cn.yifan.drawsee.pojo.entity.User;
import cn.yifan.drawsee.pojo.vo.LoginVO;
import cn.yifan.drawsee.util.PasswordUtil;
import cn.yifan.drawsee.util.RedisUtils;
import org.redisson.api.RAtomicLong;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * @FileName UserService
 * @Description
 * @Author yifan
 * @date 2025-01-28 16:08
 **/

@Service
public class UserService {

    @Autowired
    private UserMapper userMapper;
    
    @Autowired
    private RedissonClient redissonClient;
    
    @Autowired
    private UserRoleService userRoleService;

    private LoginVO getLoginVO(User user) {
        SaTokenInfo tokenInfo = StpUtil.getTokenInfo();
        RAtomicLong counter = RedisUtils.getUseAiCounter(redissonClient, user.getId());
        
        // 获取用户角色
        String userRole = userRoleService.getUserRole(user.getId());
        
        // 根据用户角色决定任务限制
        Integer aiTaskLimit;
        if (UserRole.ADMIN.equals(userRole) || UserRole.TEACHER.equals(userRole)) {
            aiTaskLimit = AiTaskLimit.DAY_LIMIT; // 管理员和教师使用较大限额
        } else {
            aiTaskLimit = AiTaskLimit.NORMAL_USER_DAY_LIMIT; // 普通用户限制为10次
        }
        
        return new LoginVO(
            tokenInfo.tokenValue,
            user.getUsername(),
            counter.get(),
            aiTaskLimit
        );
    }

    public LoginVO login(UserLoginDTO userLoginDTO) {
        User user = userMapper.getByUsername(userLoginDTO.getUsername());
        if (user == null) {
            throw new ApiException(ApiError.USER_NOT_EXIST, "文件不能为空");
        }
        // 使用PasswordUtil验证密码
        if (!PasswordUtil.matches(userLoginDTO.getPassword(), user.getPassword())) {
            throw new ApiException(ApiError.PASSWORD_ERROR, "文件不能为空");
        }
        
        // 登录用户
        StpUtil.login(user.getId());
        
        // 在登录时同时设置用户角色
        String role = userRoleService.getUserRole(user.getId());
        if (role != null) {
            StpUtil.getTokenSession().set("role", role);
            StpUtil.getRoleList().add(role);
        }
        
        return getLoginVO(user);
    }

    /**
     * 用户注册
     * 注意：已移除邀请码验证需求，用户可以直接注册
     */
    @Transactional
    public LoginVO signup(UserSignUpDTO userSignUpDTO) {
        User user = userMapper.getByUsername(userSignUpDTO.getUsername());
        if (user != null) {
            throw new ApiException(ApiError.USER_HAD_EXISTED, "文件不能为空");
        }
        
        // 创建用户对象，使用PasswordUtil加密密码
        user = new User(
            userSignUpDTO.getUsername(),
            PasswordUtil.encode(userSignUpDTO.getPassword())
        );
        userMapper.insert(user);
        StpUtil.login(user.getId());
        return getLoginVO(user);
    }

    public LoginVO checkLogin() {
        Long userId = StpUtil.getLoginIdAsLong();
        User user = userMapper.getById(userId);
        if (user == null) {
            throw new ApiException(ApiError.USER_NOT_EXIST, "文件不能为空");
        }
        return getLoginVO(user);
    }
    
    /**
     * 通过用户ID获取用户
     * @param userId 用户ID
     * @return 用户对象，如果不存在则返回null
     */
    public User getUserById(Long userId) {
        return userMapper.getById(userId);
    }

    /**
     * 获取用户角色
     * @param userId 用户ID
     * @return 用户角色
     */
    public String getUserRole(Long userId) {
        return userRoleService.getUserRole(userId);
    }
    
    /**
     * 获取当前用户角色
     * @return 用户角色
     */
    public String getUserRole() {
        Long userId = StpUtil.getLoginIdAsLong();
        return getUserRole(userId);
    }
}

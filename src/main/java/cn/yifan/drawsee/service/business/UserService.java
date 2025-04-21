package cn.yifan.drawsee.service.business;

import cn.dev33.satoken.stp.SaTokenInfo;
import cn.dev33.satoken.stp.StpUtil;
import cn.yifan.drawsee.constant.AiTaskLimit;
import cn.yifan.drawsee.constant.UserRole;
import cn.yifan.drawsee.exception.ApiError;
import cn.yifan.drawsee.exception.ApiException;
import cn.yifan.drawsee.mapper.InvitationCodeMapper;
import cn.yifan.drawsee.mapper.UserMapper;
import cn.yifan.drawsee.pojo.dto.UserLoginDTO;
import cn.yifan.drawsee.pojo.dto.UserSignUpDTO;
import cn.yifan.drawsee.pojo.entity.InvitationCode;
import cn.yifan.drawsee.pojo.entity.User;
import cn.yifan.drawsee.pojo.vo.LoginVO;
import java.sql.Timestamp;

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
    private InvitationCodeMapper invitationCodeMapper;
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
            throw new ApiException(ApiError.USER_NOT_EXIST);
        }
        if (!user.getPassword().equals(userLoginDTO.getPassword())) {
            throw new ApiException(ApiError.PASSWORD_ERROR);
        }
        StpUtil.login(user.getId());
        return getLoginVO(user);
    }

    @Transactional
    public LoginVO signup(UserSignUpDTO userSignUpDTO) {
        InvitationCode invitationCode = invitationCodeMapper.getByCode(userSignUpDTO.getInvitationCode());
        if (invitationCode == null) {
            throw new ApiException(ApiError.INVITATION_CODE_NOT_EXISTED);
        }
        if (!invitationCode.getIsActive()) {
            throw new ApiException(ApiError.INVITATION_CODE_ALREADY_USED);
        }
        User user = userMapper.getByUsername(userSignUpDTO.getUsername());
        if (user != null) {
            throw new ApiException(ApiError.USER_HAD_EXISTED);
        }
        user = new User(
            userSignUpDTO.getUsername(),
            userSignUpDTO.getPassword()
        );
        userMapper.insert(user);
        invitationCode.setUsedBy(user.getId());
        invitationCode.setUsedAt(new Timestamp(System.currentTimeMillis()));
        invitationCode.setIsActive(false);
        invitationCodeMapper.update(invitationCode);
        StpUtil.login(user.getId());
        return getLoginVO(user);
    }

    public LoginVO checkLogin() {
        Long userId = StpUtil.getLoginIdAsLong();
        User user = userMapper.getById(userId);
        if (user == null) {
            throw new ApiException(ApiError.USER_NOT_EXIST);
        }
        return getLoginVO(user);
    }

}

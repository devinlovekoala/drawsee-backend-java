package cn.yifan.drawsee.service.business;

import cn.dev33.satoken.stp.SaTokenInfo;
import cn.dev33.satoken.stp.StpUtil;
import cn.yifan.drawsee.exception.ApiError;
import cn.yifan.drawsee.exception.ApiException;
import cn.yifan.drawsee.mapper.AdminMapper;
import cn.yifan.drawsee.mapper.InvitationCodeMapper;
import cn.yifan.drawsee.mapper.UserMapper;
import cn.yifan.drawsee.pojo.dto.SendInvitationCodeDTO;
import cn.yifan.drawsee.pojo.dto.UserLoginDTO;
import cn.yifan.drawsee.pojo.dto.AdminRegisterDTO;
import cn.yifan.drawsee.pojo.dto.CreateInvitationCodeDTO;
import cn.yifan.drawsee.pojo.entity.Admin;
import cn.yifan.drawsee.pojo.entity.InvitationCode;
import cn.yifan.drawsee.pojo.entity.User;
import cn.yifan.drawsee.pojo.vo.AdminLoginVO;
import cn.yifan.drawsee.service.base.MailService;
import jakarta.mail.MessagingException;
import lombok.extern.slf4j.Slf4j;

import org.checkerframework.checker.units.qual.A;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.sql.Timestamp;
import java.util.List;
import java.util.Random;

/**
 * @FileName AdminService
 * @Description
 * @Author yifan
 * @date 2025-03-25 08:55
 **/

@Service
@Slf4j
public class AdminService {

    @Autowired
    private InvitationCodeMapper invitationCodeMapper;
    @Autowired
    private MailService mailService;
    @Autowired
    private UserMapper userMapper;
    @Autowired
    private AdminMapper adminMapper;

    /* 管理员注册 */

    public void register(AdminRegisterDTO adminRegisterDTO) {
        User user = userMapper.getById(adminRegisterDTO.getUserId());
        if (user == null) {
            throw new ApiException(ApiError.USER_NOT_EXIST);
        }
        Admin admin = adminMapper.getByUserId(user.getId());
        if (admin != null) {
            throw new ApiException(ApiError.ADMIN_HAD_EXISTED);
        }
        admin = new Admin(adminRegisterDTO.getUserId());
        adminMapper.insert(admin);
    }

    /* 管理员登录 */

    public AdminLoginVO login(UserLoginDTO userLoginDTO) {
        User user = userMapper.getByUsername(userLoginDTO.getUsername());
        if (user == null) {
            throw new ApiException(ApiError.USER_NOT_EXIST);
        }
        if (!user.getPassword().equals(userLoginDTO.getPassword())) {
            throw new ApiException(ApiError.PASSWORD_ERROR);
        }
        Admin admin = adminMapper.getByUserId(user.getId());
        if (admin == null) {
            throw new ApiException(ApiError.NOT_ADMIN);
        }
        StpUtil.login(admin.getId());
        return new AdminLoginVO(StpUtil.getTokenInfo().tokenValue);
    }

    public void checkLogin() {
        Long userId = StpUtil.getLoginIdAsLong();
        User user = userMapper.getById(userId);
        if (user == null) {
            throw new ApiException(ApiError.USER_NOT_EXIST);
        }
        Admin admin = adminMapper.getByUserId(user.getId());
        if (admin == null) {
            throw new ApiException(ApiError.NOT_ADMIN);
        }
    }

    /* 邀请码管理 */

    /* 分页获取邀请码 */
    public List<InvitationCode> getInvitationCodesByPage(int page, int size) {
        int offset = (page - 1) * size;
        return invitationCodeMapper.getByPage(offset, size);
    }

    // 创建随机邀请码的方法
    public String generateCode() {
        String characters = "ACDEFGHJKLMNPQRSTUVWXYZ234679"; // 去除了容易混淆的字符
        Random random = new SecureRandom();
        StringBuilder sb = new StringBuilder(8);

        for (int i = 0; i < 8; i++) {
            sb.append(characters.charAt(random.nextInt(characters.length())));
        }

        String code = sb.toString();
        // 检查是否已存在（极小概率冲突时可重试）
        if (invitationCodeMapper.getByCode(code) != null) {
            return generateCode();
        }
        return code;
    }

    /* 创建邀请码 */
    public void createInvitationCode(CreateInvitationCodeDTO createInvitationCodeDTO) {
        Integer count = createInvitationCodeDTO.getCount();
        // 创建count个邀请码
        for (int i = 0; i < count; i++) {
            String code = generateCode();
            InvitationCode invitationCode = new InvitationCode(code);
            invitationCodeMapper.insert(invitationCode);
        }
    }

    /* 发送邀请码 */
    @Transactional
    public void sendInvitationCode(Long id, SendInvitationCodeDTO sendInvitationCodeDTO) {
        InvitationCode invitationCode = invitationCodeMapper.getById(id);
        if (invitationCode == null) {
            throw new ApiException(ApiError.INVITATION_CODE_NOT_EXISTED);
        }
        if (!invitationCode.getIsActive()) {
            throw new ApiException(ApiError.INVITATION_CODE_ALREADY_USED);
        }
        // 发送邮件
        try {
            mailService.sendInvitationMail(
                sendInvitationCodeDTO.getEmail(), 
                sendInvitationCodeDTO.getUserName(), 
                invitationCode.getCode()
            );
            invitationCode.setSentEmail(sendInvitationCodeDTO.getEmail());
            invitationCode.setSentUserName(sendInvitationCodeDTO.getUserName());
            invitationCode.setLastSentAt(new Timestamp(System.currentTimeMillis()));
            invitationCodeMapper.update(invitationCode);
        } catch (MessagingException e) {
            throw new ApiException(ApiError.INVITATION_CODE_SEND_FAILED);
        }
    }

}

package cn.yifan.drawsee.service.business;

import cn.yifan.drawsee.exception.ApiError;
import cn.yifan.drawsee.exception.ApiException;
import cn.yifan.drawsee.mapper.AdminMapper;
import cn.yifan.drawsee.mapper.InvitationCodeMapper;
import cn.yifan.drawsee.mapper.UserMapper;
import cn.yifan.drawsee.pojo.dto.AdminRegisterDTO;
import cn.yifan.drawsee.pojo.dto.CreateInvitationCodeDTO;
import cn.yifan.drawsee.pojo.dto.SendInvitationCodeDTO;
import cn.yifan.drawsee.pojo.entity.Admin;
import cn.yifan.drawsee.pojo.entity.InvitationCode;
import cn.yifan.drawsee.pojo.entity.User;
import cn.yifan.drawsee.service.base.MailService;
import jakarta.mail.MessagingException;
import java.security.SecureRandom;
import java.sql.Timestamp;
import java.util.List;
import java.util.Random;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * @FileName AdminService @Description 管理员服务类，处理管理员特有的功能 @Author yifan
 *
 * @date 2025-03-25 08:55
 * @update 2025-08-16 10:25 移除专用登录逻辑，使用通用用户登录
 */
@Service
@Slf4j
public class AdminService {

  @Autowired private InvitationCodeMapper invitationCodeMapper;

  @Autowired private MailService mailService;

  @Autowired private UserMapper userMapper;

  @Autowired private AdminMapper adminMapper;

  /* 管理员注册 */

  /**
   * 将普通用户注册为管理员
   *
   * @param adminRegisterDTO 管理员注册DTO
   */
  public void register(AdminRegisterDTO adminRegisterDTO) {
    User user = userMapper.getById(adminRegisterDTO.getUserId());
    if (user == null) {
      throw new ApiException(ApiError.USER_NOT_EXIST, "文件不能为空");
    }
    Admin admin = adminMapper.getByUserId(user.getId());
    if (admin != null) {
      throw new ApiException(ApiError.ADMIN_HAD_EXISTED, "文件不能为空");
    }
    admin = new Admin(adminRegisterDTO.getUserId());
    adminMapper.insert(admin);
  }

  /* 邀请码管理 */

  /**
   * 分页获取邀请码列表
   *
   * @param page 页码
   * @param size 每页大小
   * @return 邀请码列表
   */
  public List<InvitationCode> getInvitationCodesByPage(int page, int size) {
    int offset = (page - 1) * size;
    return invitationCodeMapper.getByPage(offset, size);
  }

  /**
   * 生成随机邀请码
   *
   * @return 8位邀请码
   */
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

  /**
   * 创建邀请码
   *
   * @param createInvitationCodeDTO 创建邀请码DTO
   */
  public void createInvitationCode(CreateInvitationCodeDTO createInvitationCodeDTO) {
    Integer count = createInvitationCodeDTO.getCount();
    // 创建count个邀请码
    for (int i = 0; i < count; i++) {
      String code = generateCode();
      InvitationCode invitationCode = new InvitationCode(code);
      invitationCodeMapper.insert(invitationCode);
    }
  }

  /**
   * 发送邀请码
   *
   * @param id 邀请码ID
   * @param sendInvitationCodeDTO 发送邀请码DTO
   */
  @Transactional
  public void sendInvitationCode(Long id, SendInvitationCodeDTO sendInvitationCodeDTO) {
    InvitationCode invitationCode = invitationCodeMapper.getById(id);
    if (invitationCode == null) {
      throw new ApiException(ApiError.INVITATION_CODE_NOT_EXISTED, "文件不能为空");
    }
    if (!invitationCode.getIsActive()) {
      throw new ApiException(ApiError.INVITATION_CODE_ALREADY_USED, "文件不能为空");
    }
    // 发送邮件
    try {
      mailService.sendInvitationMail(
          sendInvitationCodeDTO.getEmail(),
          sendInvitationCodeDTO.getUserName(),
          invitationCode.getCode());
      invitationCode.setSentEmail(sendInvitationCodeDTO.getEmail());
      invitationCode.setSentUserName(sendInvitationCodeDTO.getUserName());
      invitationCode.setLastSentAt(new Timestamp(System.currentTimeMillis()));
      invitationCodeMapper.update(invitationCode);
    } catch (MessagingException e) {
      throw new ApiException(ApiError.INVITATION_CODE_SEND_FAILED, "文件不能为空");
    }
  }
}

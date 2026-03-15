package cn.yifan.drawsee.controller.teacher;

import cn.dev33.satoken.annotation.SaCheckLogin;
import cn.dev33.satoken.annotation.SaCheckRole;
import cn.yifan.drawsee.constant.UserRole;
import cn.yifan.drawsee.pojo.dto.CreateTeacherInvitationCodeDTO;
import cn.yifan.drawsee.pojo.entity.TeacherInvitationCode;
import cn.yifan.drawsee.service.business.TeacherInvitationCodeService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

/**
 * @FileName TeacherInvitationCodeController @Description 教师邀请码控制器 @Author yifan
 *
 * @date 2025-06-11 14:45
 */
@RestController
@RequestMapping("/admin/teacher/invitation_codes")
@SaCheckRole(UserRole.ADMIN)
public class TeacherInvitationCodeController {

  @Autowired private TeacherInvitationCodeService teacherInvitationCodeService;

  /**
   * 分页获取教师邀请码
   *
   * @param page 页码
   * @param size 每页大小
   * @return 教师邀请码列表
   */
  @GetMapping
  public List<TeacherInvitationCode> getTeacherInvitationCodesByPage(
      @RequestParam(defaultValue = "1") int page, @RequestParam(defaultValue = "10") int size) {
    return teacherInvitationCodeService.getTeacherInvitationCodesByPage(page, size);
  }

  /**
   * 创建教师邀请码
   *
   * @param createTeacherInvitationCodeDTO 创建教师邀请码DTO
   */
  @PostMapping
  public void createTeacherInvitationCode(
      @RequestBody @Valid CreateTeacherInvitationCodeDTO createTeacherInvitationCodeDTO) {
    teacherInvitationCodeService.createTeacherInvitationCode(createTeacherInvitationCodeDTO);
  }
}

/** 教师邀请码控制器 - 用户可用的API */
@RestController
@RequestMapping("/teacher/invitation_codes")
@SaCheckLogin
class TeacherInvitationCodeUserController {

  @Autowired private TeacherInvitationCodeService teacherInvitationCodeService;

  /**
   * 使用教师邀请码
   *
   * @param code 邀请码
   * @return 是否使用成功
   */
  @PostMapping("/use")
  public boolean useTeacherInvitationCode(@RequestParam String code) {
    return teacherInvitationCodeService.useTeacherInvitationCode(code);
  }
}

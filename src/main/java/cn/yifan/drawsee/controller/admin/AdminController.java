package cn.yifan.drawsee.controller.admin;

import cn.dev33.satoken.annotation.SaCheckLogin;
import cn.dev33.satoken.annotation.SaCheckRole;
import cn.yifan.drawsee.constant.UserRole;
import cn.yifan.drawsee.pojo.dto.AdminRegisterDTO;
import cn.yifan.drawsee.pojo.dto.CreateInvitationCodeDTO;
import cn.yifan.drawsee.pojo.dto.SendInvitationCodeDTO;
import cn.yifan.drawsee.pojo.entity.InvitationCode;
import cn.yifan.drawsee.pojo.vo.StatisticsVO;
import cn.yifan.drawsee.service.business.AdminService;
import cn.yifan.drawsee.service.business.StatisticsService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

/**
 * @FileName AdminController @Description 管理员控制器，只包含管理员特有的功能 @Author yifan
 *
 * @date 2025-03-25 08:54
 * @update 2025-08-16 10:20 移除专用登录逻辑，使用通用用户登录
 */
@RestController
@RequestMapping("/admin")
@SaCheckRole({UserRole.ADMIN})
@SaCheckLogin
public class AdminController {

  @Autowired private AdminService adminService;

  @Autowired private StatisticsService statisticsService;

  @PostMapping("/register")
  public void register(@RequestBody @Valid AdminRegisterDTO adminRegisterDTO) {
    adminService.register(adminRegisterDTO);
  }

  /* 邀请码管理 */

  // 分页获取邀请码
  @GetMapping("/invitation_codes")
  public List<InvitationCode> getInvitationCodesByPage(
      @RequestParam(defaultValue = "1") int page, @RequestParam(defaultValue = "10") int size) {
    return adminService.getInvitationCodesByPage(page, size);
  }

  // 创建邀请码
  @PostMapping("invitation_codes")
  public void createInvitationCode(@RequestBody CreateInvitationCodeDTO createInvitationCodeDTO) {
    adminService.createInvitationCode(createInvitationCodeDTO);
  }

  // 发送邀请码
  @PostMapping("invitation_codes/{id}")
  public void sendInvitationCode(
      @PathVariable("id") Long id, @RequestBody SendInvitationCodeDTO sendInvitationCodeDTO) {
    adminService.sendInvitationCode(id, sendInvitationCodeDTO);
  }

  /* 统计数据 */

  // 获取统计数据
  @GetMapping("/statistics")
  public StatisticsVO getStatistics() {
    return statisticsService.getStatistics();
  }
}

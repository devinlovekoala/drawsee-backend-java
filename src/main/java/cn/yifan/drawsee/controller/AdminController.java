package cn.yifan.drawsee.controller;

import cn.dev33.satoken.annotation.SaCheckLogin;
import cn.dev33.satoken.annotation.SaCheckRole;
import cn.dev33.satoken.annotation.SaIgnore;
import cn.yifan.drawsee.constant.UserRole;
import cn.yifan.drawsee.pojo.dto.SendInvitationCodeDTO;
import cn.yifan.drawsee.pojo.dto.AdminRegisterDTO;
import cn.yifan.drawsee.pojo.dto.CreateInvitationCodeDTO;
import cn.yifan.drawsee.pojo.dto.UserLoginDTO;
import cn.yifan.drawsee.pojo.entity.InvitationCode;
import cn.yifan.drawsee.pojo.vo.AdminLoginVO;
import cn.yifan.drawsee.pojo.vo.LoginVO;
import cn.yifan.drawsee.pojo.vo.StatisticsVO;
import cn.yifan.drawsee.service.business.AdminService;
import cn.yifan.drawsee.service.business.StatisticsService;
import cn.yifan.drawsee.service.business.UserService;
import jakarta.validation.Valid;
import org.apache.catalina.filters.ExpiresFilter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import org.thymeleaf.spring6.processor.SpringActionTagProcessor;

import java.util.List;

/**
 * @FileName AdminController
 * @Description
 * @Author yifan
 * @date 2025-03-25 08:54
 **/

@RestController
@RequestMapping("/admin")
@SaCheckRole({UserRole.ADMIN})
public class AdminController {

    @Autowired
    private AdminService adminService;
    
    @Autowired
    private StatisticsService statisticsService;

    @PostMapping("/register")
    public void register(@RequestBody @Valid AdminRegisterDTO adminRegisterDTO) {
        adminService.register(adminRegisterDTO);
    }

    /* 管理员登录 */

    @PostMapping("/login")
    @SaIgnore
    public AdminLoginVO login(@RequestBody @Valid UserLoginDTO userLoginDTO) {
        return adminService.login(userLoginDTO);
    }

    @GetMapping("/check_login")
    public void checkLogin() {
        adminService.checkLogin();
    }

    /* 邀请码管理 */

    // 分页获取邀请码
    @GetMapping("/invitation_codes")
    public List<InvitationCode> getInvitationCodesByPage(
        @RequestParam(defaultValue = "1") int page,
        @RequestParam(defaultValue = "10") int size
    ) {
        return adminService.getInvitationCodesByPage(page, size);
    }

    // 创建邀请码
    @PostMapping("invitation_codes")
    public void createInvitationCode(@RequestBody CreateInvitationCodeDTO createInvitationCodeDTO) {
        adminService.createInvitationCode(createInvitationCodeDTO);
    }

    // 发送邀请码
    @PostMapping("invitation_codes/{id}")
    public void sendInvitationCode(@PathVariable("id") Long id, @RequestBody SendInvitationCodeDTO sendInvitationCodeDTO) {
        adminService.sendInvitationCode(id, sendInvitationCodeDTO);
    }
    
    /* 统计数据 */
    
    // 获取统计数据
    @GetMapping("/statistics")
    public StatisticsVO getStatistics() {
        return statisticsService.getStatistics();
    }
}

package cn.yifan.drawsee.controller;

import cn.dev33.satoken.annotation.SaCheckLogin;
import cn.dev33.satoken.stp.StpUtil;
import cn.yifan.drawsee.pojo.dto.UserLoginDTO;
import cn.yifan.drawsee.pojo.dto.UserSignUpDTO;
import cn.yifan.drawsee.pojo.vo.LoginVO;
import cn.yifan.drawsee.service.business.UserService;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

/**
 * @FileName UserController @Description 用户控制器 @Author yifan
 *
 * @date 2025-01-28 16:06
 */
@Slf4j
@RestController
@RequestMapping("/user")
public class UserController {

  private final UserService userService;

  public UserController(UserService userService) {
    this.userService = userService;
  }

  @PostMapping("/login")
  public LoginVO login(@RequestBody @Valid UserLoginDTO userLoginDTO) {
    return userService.login(userLoginDTO);
  }

  @PostMapping("/signup")
  public LoginVO signup(@RequestBody @Valid UserSignUpDTO userSignUpDTO) {
    return userService.signup(userSignUpDTO);
  }

  @GetMapping("/check_login")
  @SaCheckLogin
  public LoginVO checkLogin() {
    return userService.checkLogin();
  }

  @PostMapping("/logout")
  @SaCheckLogin
  public void logout() {
    StpUtil.logout();
  }
}

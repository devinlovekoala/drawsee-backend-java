package cn.yifan.drawsee.config;

import cn.dev33.satoken.interceptor.SaInterceptor;
import cn.dev33.satoken.router.SaRouter;
import cn.dev33.satoken.stp.StpUtil;
import cn.yifan.drawsee.constant.UserRole;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * @FileName SaTokenConfig @Description @Author yifan
 *
 * @date 2025-01-28 15:41
 */
@Configuration
public class SaTokenConfig implements WebMvcConfigurer {

  @Override
  public void addInterceptors(InterceptorRegistry registry) {
    // 注册路由拦截器，自定义验证规则
    registry
        .addInterceptor(
            new SaInterceptor(
                handler -> {

                  // 管理员接口：只允许管理员访问
                  SaRouter.match("/admin/**", r -> StpUtil.checkRole(UserRole.ADMIN));

                  // 教师接口：只允许教师或管理员访问
                  SaRouter.match(
                      "/teacher/**", r -> StpUtil.checkRoleOr(UserRole.TEACHER, UserRole.ADMIN));

                  // 学生接口：允许任何已登录用户访问
                  SaRouter.match("/student/**", r -> StpUtil.checkLogin());
                }))
        .addPathPatterns("/**")
        // 排除登录注册接口
        .excludePathPatterns("/user/login", "/user/signup");
  }
}

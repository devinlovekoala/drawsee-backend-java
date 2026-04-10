package cn.yifan.drawsee.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.lang.NonNull;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * @FileName CorsConfig @Description CORS跨域配置 @Author yifan
 *
 * @date 2025-01-31 22:48
 */
@Configuration
public class CorsConfig implements WebMvcConfigurer {

  @Override
  public void addCorsMappings(@NonNull CorsRegistry registry) {
    registry
        .addMapping("/**")
        .allowedOriginPatterns(
            // 本地开发
            "http://localhost:*",
            "http://127.0.0.1:*",

            // 正式域名
            "http://drawsee.cn",
            "http://*.drawsee.cn")
        .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
        .allowedHeaders("Authorization", "Content-Type", "X-Requested-With")
        .exposedHeaders("Authorization")
        .allowCredentials(true)
        .maxAge(3600);
  }
}

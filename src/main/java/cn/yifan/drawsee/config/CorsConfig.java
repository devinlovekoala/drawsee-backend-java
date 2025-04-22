package cn.yifan.drawsee.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * @FileName CorsConfig
 * @Description CORS跨域配置
 * @Author yifan
 * @date 2025-01-31 22:48
 **/

@Configuration
public class CorsConfig implements WebMvcConfigurer {

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/**")
                .allowedOrigins(
                    "http://localhost:3000",  // React默认端口
                    "http://localhost:5173",  // Vite默认端口
                    "http://localhost:6868",  // 课程服务端口
                    "http://127.0.0.1:3000",
                    "http://127.0.0.1:5173",
                    "http://127.0.0.1:6868",
                    "http://42.193.107.127:6868",
                    "http://42.193.107.127:3000",
                    "http://42.193.107.127:5173",
                    "http://drawsee.cn",
                    "https://drawsee.cn",
                    "http://admin.drawsee.cn",
                    "https://admin.drawsee.cn"
                )
                .allowedHeaders("*", "Authorization", "Content-Type", "X-Requested-With")
                .exposedHeaders("Authorization")
                .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
                .allowCredentials(true)
                .maxAge(3600); // 1小时的预检请求缓存
    }

}

package cn.yifan.drawsee.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.multipart.support.StandardServletMultipartResolver;
import org.springframework.web.servlet.config.annotation.PathMatchConfigurer;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * @FileName WebConfig
 * @Description Web配置类，处理静态资源和请求路径配置
 * @Author yifan
 * @date 2025-04-12 10:30
 **/

@Configuration
public class WebConfig implements WebMvcConfigurer {

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        // 静态资源路径配置
        registry.addResourceHandler("/static/**")
                .addResourceLocations("classpath:/static/");
                
        // Swagger UI资源路径
        registry.addResourceHandler("/swagger-ui/**")
                .addResourceLocations("classpath:/META-INF/resources/webjars/springfox-swagger-ui/")
                .resourceChain(false);
    }
    
    @Override
    public void configurePathMatch(PathMatchConfigurer configurer) {
        // 配置路径匹配，确保控制器能正确处理请求
        configurer.setUseTrailingSlashMatch(false);
    }
    
    /**
     * 配置文件上传解析器
     * @return StandardServletMultipartResolver
     */
    @Bean
    public StandardServletMultipartResolver multipartResolver() {
        return new StandardServletMultipartResolver();
    }
} 
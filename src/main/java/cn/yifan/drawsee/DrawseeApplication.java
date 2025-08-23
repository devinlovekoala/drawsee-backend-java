package cn.yifan.drawsee;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.web.servlet.ServletComponentScan;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.web.servlet.config.annotation.PathMatchConfigurer;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.amqp.rabbit.annotation.EnableRabbit;

@SpringBootApplication
@EnableAsync
@EnableScheduling
@EnableCaching
@EnableRabbit
@ServletComponentScan
public class DrawseeApplication {

    public static void main(String[] args) {
        SpringApplication.run(DrawseeApplication.class, args);
    }
    
    /**
     * 配置全局路径匹配策略
     */
    @Bean
    public WebMvcConfigurer webMvcConfigurer() {
        return new WebMvcConfigurer() {
            @Override
            public void configurePathMatch(PathMatchConfigurer configurer) {
                // 禁用后缀模式匹配
                configurer.setUseSuffixPatternMatch(false);
            }
        };
    }
}

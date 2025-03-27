package cn.yifan.drawsee.config;

import cn.yifan.drawsee.service.base.PromptService;
import cn.yifan.drawsee.util.PromptServiceInvocationHandler;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ResourceLoader;

import java.lang.reflect.Proxy;

/**
 * @FileName PromptConfig
 * @Description
 * @Author yifan
 * @date 2025-03-09 23:18
 **/

@Configuration
public class PromptConfig {

    @Bean
    public PromptService promptServiceProxy(@Qualifier("webApplicationContext") ResourceLoader resourceLoader) {
        return (PromptService) Proxy.newProxyInstance(
            PromptService.class.getClassLoader(),
            new Class[]{PromptService.class},
            new PromptServiceInvocationHandler(resourceLoader)
        );
    }

}

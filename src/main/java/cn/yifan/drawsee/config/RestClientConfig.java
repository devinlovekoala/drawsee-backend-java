package cn.yifan.drawsee.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

/**
 * @FileName RestClientConfig
 * @Description
 * @Author yifan
 * @date 2025-02-27 23:07
 **/

@Configuration
public class RestClientConfig {

    @Bean
    public RestClient restClient() {
        return RestClient.create();
    }

}

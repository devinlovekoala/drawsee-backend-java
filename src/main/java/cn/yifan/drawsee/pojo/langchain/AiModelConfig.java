package cn.yifan.drawsee.pojo.langchain;

import lombok.Data;

import java.util.Map;

/**
 * @FileName ModelConfig
 * @Description
 * @Author yifan
 * @date 2025-03-20 20:33
 **/

@Data
public class AiModelConfig {

    private String baseUrl;

    private String apiKey;

    private String modelName;

    private Double temperature;

    /**
     * 透传到 OpenAI 兼容接口的自定义参数（extra_body）
     */
    private Map<String, Object> customParameters;

}

package cn.yifan.drawsee.pojo.langchain;

import lombok.Data;

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

}

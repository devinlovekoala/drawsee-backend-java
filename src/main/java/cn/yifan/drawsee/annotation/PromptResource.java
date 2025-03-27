package cn.yifan.drawsee.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * @FileName PromptResource
 * @Description
 * @Author yifan
 * @date 2025-03-09 23:02
 **/

@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface PromptResource {
    String fromResource();  // 示例：/prompts/customer-service.txt
}

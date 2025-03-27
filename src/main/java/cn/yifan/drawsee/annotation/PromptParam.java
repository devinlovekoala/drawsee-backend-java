package cn.yifan.drawsee.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * @FileName PromptParam
 * @Description
 * @Author yifan
 * @date 2025-03-09 23:03
 **/

@Target(ElementType.PARAMETER)
@Retention(RetentionPolicy.RUNTIME)
public @interface PromptParam {
    String value();  // 对应模板中的{{变量名}}
}

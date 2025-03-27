package cn.yifan.drawsee.annotation;

import cn.yifan.drawsee.util.ValueSetValidator;
import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.*;

/**
 * @FileName ValueSet
 * @Description
 * @Author yifan
 * @date 2025-02-24 21:06
 **/

@Documented
@Constraint(validatedBy = ValueSetValidator.class)
@Target({ElementType.METHOD, ElementType.FIELD})
@Retention(RetentionPolicy.RUNTIME)
public @interface ValueSet {
    String message() default "Value must be one of the specified values";
    Class<?>[] groups() default {};
    Class<? extends Payload>[] payload() default {};
    String[] values();
}

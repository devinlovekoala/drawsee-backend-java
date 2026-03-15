package cn.yifan.drawsee.util;

import cn.yifan.drawsee.annotation.ValueSet;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import java.util.Arrays;

/**
 * @FileName ValueSetValidator @Description @Author yifan
 *
 * @date 2025-02-24 21:07
 */
public class ValueSetValidator implements ConstraintValidator<ValueSet, String> {

  private String[] allowedValues;

  @Override
  public void initialize(ValueSet constraintAnnotation) {
    this.allowedValues = constraintAnnotation.values();
  }

  @Override
  public boolean isValid(String value, ConstraintValidatorContext context) {
    if (value == null) {
      return false; // 如果允许 null 值，可以在这里返回 true
    }
    return Arrays.asList(allowedValues).contains(value);
  }
}

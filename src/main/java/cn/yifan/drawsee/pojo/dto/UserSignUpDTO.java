package cn.yifan.drawsee.pojo.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * @FileName SignUpDTO @Description @Author yifan
 *
 * @date 2025-01-28 16:19
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class UserSignUpDTO {
  @NotBlank private String username;
  @NotBlank private String password;
}

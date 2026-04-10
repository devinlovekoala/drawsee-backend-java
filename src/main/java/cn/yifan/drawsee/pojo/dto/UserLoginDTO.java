package cn.yifan.drawsee.pojo.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * @FileName LoginDTO @Description @Author yifan
 *
 * @date 2025-01-28 16:18
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class UserLoginDTO {
  @NotBlank private String username;
  @NotBlank private String password;
}

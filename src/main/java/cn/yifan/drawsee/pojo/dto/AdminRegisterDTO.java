package cn.yifan.drawsee.pojo.dto;

import java.io.Serial;
import java.io.Serializable;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * @FileName AdminRegisterDTO @Description @Author yifan
 *
 * @date 2025-03-26 13:09
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class AdminRegisterDTO implements Serializable {

  @Serial private static final long serialVersionUID = 1L;

  private Long userId;
}

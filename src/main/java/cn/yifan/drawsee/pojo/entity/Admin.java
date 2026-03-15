package cn.yifan.drawsee.pojo.entity;

import java.io.Serial;
import java.io.Serializable;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * @FileName Admin @Description @Author devin
 *
 * @date 2025-03-26 12:52
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class Admin implements Serializable {

  @Serial private static final long serialVersionUID = 1L;

  private Long id;

  private Long userId;

  public Admin(Long userId) {
    this.userId = userId;
  }
}

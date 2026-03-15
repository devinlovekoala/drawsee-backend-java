package cn.yifan.drawsee.pojo.entity;

import java.io.Serial;
import java.io.Serializable;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * @FileName Teacher @Description 教师实体类 @Author devin
 *
 * @date 2025-03-28 10:35
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class Teacher implements Serializable {

  @Serial private static final long serialVersionUID = 1L;

  private Long id;

  private Long userId;

  private String title;

  private String organization;

  public Teacher(Long userId) {
    this.userId = userId;
  }
}

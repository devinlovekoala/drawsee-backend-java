package cn.yifan.drawsee.pojo.dto;

import java.io.Serial;
import java.io.Serializable;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * @FileName TeacherRegisterDTO @Description 教师注册DTO @Author yifan
 *
 * @date 2025-06-10 11:35
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class TeacherRegisterDTO implements Serializable {

  @Serial private static final long serialVersionUID = 1L;

  private Long userId;
}

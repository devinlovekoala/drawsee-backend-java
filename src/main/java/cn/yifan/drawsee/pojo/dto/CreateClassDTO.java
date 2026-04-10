package cn.yifan.drawsee.pojo.dto;

import jakarta.validation.constraints.NotBlank;
import java.io.Serial;
import java.io.Serializable;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * @FileName CreateClassDTO @Description 创建班级DTO @Author yifan
 *
 * @date 2025-06-10 11:25
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class CreateClassDTO implements Serializable {

  @Serial private static final long serialVersionUID = 1L;

  @NotBlank(message = "班级名称不能为空")
  private String name;

  @NotBlank(message = "班级描述不能为空")
  private String description;
}

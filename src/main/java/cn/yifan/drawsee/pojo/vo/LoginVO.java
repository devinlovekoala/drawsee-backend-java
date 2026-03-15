package cn.yifan.drawsee.pojo.vo;

import java.io.Serial;
import java.io.Serializable;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * @FileName LoginVO @Description @Author yifan
 *
 * @date 2025-02-26 14:59
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class LoginVO implements Serializable {

  @Serial private static final long serialVersionUID = 1L;

  private String token;

  private String username;

  // 对话次数
  private Long aiTaskCount;

  // 对话次数上限
  private Integer aiTaskLimit;
}

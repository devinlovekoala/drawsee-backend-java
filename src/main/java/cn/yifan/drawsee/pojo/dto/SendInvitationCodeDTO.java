package cn.yifan.drawsee.pojo.dto;

import java.io.Serial;
import java.io.Serializable;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * @FileName ActivateInvitationCodeDTO @Description @Author yifan
 *
 * @date 2025-03-25 14:50
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class SendInvitationCodeDTO implements Serializable {

  @Serial private static final long serialVersionUID = 1L;

  private String email;

  // 问卷中的名称，而非真实用户名
  private String userName;
}

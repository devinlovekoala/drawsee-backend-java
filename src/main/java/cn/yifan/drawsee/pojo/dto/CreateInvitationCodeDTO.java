package cn.yifan.drawsee.pojo.dto;

import java.io.Serial;
import java.io.Serializable;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * @FileName CreateInvitationCodeDTO @Description @Author yifan
 *
 * @date 2025-03-25 09:02
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class CreateInvitationCodeDTO implements Serializable {

  @Serial private static final long serialVersionUID = 1L;

  private Integer count;
}

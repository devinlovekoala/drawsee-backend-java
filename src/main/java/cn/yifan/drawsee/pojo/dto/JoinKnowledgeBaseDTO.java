package cn.yifan.drawsee.pojo.dto;

import jakarta.validation.constraints.NotBlank;
import java.io.Serial;
import java.io.Serializable;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * @FileName JoinKnowledgeBaseDTO @Description 加入知识库的DTO类 @Author yifan
 *
 * @date 2025-03-28 10:49
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class JoinKnowledgeBaseDTO implements Serializable {

  @Serial private static final long serialVersionUID = 1L;

  @NotBlank(message = "邀请码不能为空")
  private String invitationCode;
}

package cn.yifan.drawsee.pojo.vo;

import java.io.Serial;
import java.io.Serializable;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * @FileName ConversationForkVO @Description 分享会话继续(复制)结果VO @Author devin
 *
 * @date 2026-02-25
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class ConversationForkVO implements Serializable {

  @Serial private static final long serialVersionUID = 1L;

  private ConversationVO conversation;
}

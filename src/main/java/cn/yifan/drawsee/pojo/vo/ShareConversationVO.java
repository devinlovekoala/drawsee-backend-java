package cn.yifan.drawsee.pojo.vo;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * @FileName ShareConversationVO @Description 分享会话详情VO @Author devin
 *
 * @date 2026-02-25
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class ShareConversationVO implements Serializable {

  @Serial private static final long serialVersionUID = 1L;

  private ConversationVO conversation;

  private List<NodeVO> nodes;

  private ConversationShareVO share;
}

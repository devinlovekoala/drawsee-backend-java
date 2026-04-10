package cn.yifan.drawsee.pojo.vo;

import java.io.Serial;
import java.io.Serializable;
import java.sql.Timestamp;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * @FileName ConversationVO @Description @Author yifan
 *
 * @date 2025-01-29 20:52
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class ConversationVO implements Serializable {

  @Serial private static final long serialVersionUID = 1L;

  private Long id;

  private String title;

  private Long userId;

  private Timestamp createdAt;

  private Timestamp updatedAt;
}

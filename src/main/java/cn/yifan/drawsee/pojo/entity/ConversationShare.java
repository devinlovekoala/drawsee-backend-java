package cn.yifan.drawsee.pojo.entity;

import java.io.Serial;
import java.io.Serializable;
import java.sql.Timestamp;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * @FileName ConversationShare @Description 会话分享实体 @Author devin
 *
 * @date 2026-02-25
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class ConversationShare implements Serializable {

  @Serial private static final long serialVersionUID = 1L;

  private Long id;

  private Long convId;

  private Long userId;

  private Long classId;

  private String shareToken;

  private Boolean allowContinue;

  private Long viewCount;

  private Timestamp createdAt;

  private Timestamp updatedAt;

  private Boolean isDeleted;
}

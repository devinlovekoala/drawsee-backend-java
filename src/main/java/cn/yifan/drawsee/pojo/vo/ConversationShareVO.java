package cn.yifan.drawsee.pojo.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;
import java.sql.Timestamp;

/**
 * @FileName ConversationShareVO
 * @Description 会话分享VO
 * @Author devin
 * @date 2026-02-25
 */

@Data
@AllArgsConstructor
@NoArgsConstructor
public class ConversationShareVO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private Long id;

    private Long convId;

    private Long userId;

    private Long classId;

    private String shareToken;

    private String sharePath;

    private Boolean allowContinue;

    private Long viewCount;

    private Timestamp createdAt;

    private Timestamp updatedAt;
}

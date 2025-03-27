package cn.yifan.drawsee.pojo.entity;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;
import java.sql.Timestamp;

/**
 * @FileName Conversation
 * @Description
 * @Author yifan
 * @date 2025-01-27 23:42
 **/

@Data
@AllArgsConstructor
@NoArgsConstructor
public class Conversation implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private Long id;

    // 会话标题
    private String title;

    // 会话所属用户
    private Long userId;

    // 会话创建时间
    private Timestamp createdAt;

    // 会话更新时间
    private Timestamp updatedAt;

    // 会话是否删除
    private Boolean isDeleted;

    public Conversation(String title, Long userId) {
        this.title = title;
        this.userId = userId;
    }

}

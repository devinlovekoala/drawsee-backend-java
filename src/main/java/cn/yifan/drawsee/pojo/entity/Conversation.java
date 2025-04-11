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

    /**
     * 会话ID
     */
    private Long id;

    /**
     * 用户ID
     */
    private Long userId;

    /**
     * 会话标题
     */
    private String title;

    /**
     * 学科
     */
    private String subject;

    /**
     * 创建时间
     */
    private Timestamp createdAt;

    /**
     * 更新时间
     */
    private Timestamp updatedAt;

    /**
     * 是否删除
     */
    private Boolean isDeleted;

    public Conversation(String title, Long userId) {
        this.title = title;
        this.userId = userId;
    }

}

package cn.yifan.drawsee.pojo.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;

/**
 * @FileName CreateConversationShareDTO
 * @Description 创建会话分享DTO
 * @Author devin
 * @date 2026-02-25
 */

@Data
@AllArgsConstructor
@NoArgsConstructor
public class CreateConversationShareDTO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 关联班级ID或课程ID（可选）
     */
    private String classId;

    /**
     * 是否允许继续会话（默认允许）
     */
    private Boolean allowContinue;
}

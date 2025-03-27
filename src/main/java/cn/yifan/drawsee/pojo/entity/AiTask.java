package cn.yifan.drawsee.pojo.entity;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;
import java.sql.Timestamp;

/**
 * @FileName ChatTask
 * @Description
 * @Author yifan
 * @date 2025-01-29 17:08
 **/

@Data
@AllArgsConstructor
@NoArgsConstructor
public class AiTask implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private Long id;

    // 任务类型
    private String type;

    // 任务数据
    private String data;

    // 任务结果
    private String result;

    // 任务状态
    private String status;

    // 任务消耗的tokens
    private Long tokens;

    // 任务所属用户
    private Long userId;

    // 任务所属会话
    private Long convId;

    // 任务创建时间
    private Timestamp createdAt;

    // 任务更新时间
    private Timestamp updatedAt;

    // 任务是否删除
    private Boolean isDeleted;

    public AiTask(
        String type, String data,
        String status, Long userId, Long convId
    ) {
        this.type = type;
        this.data = data;
        this.status = status;
        this.userId = userId;
        this.convId = convId;
    }

}
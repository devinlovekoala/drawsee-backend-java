package cn.yifan.drawsee.pojo.vo;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * @FileName CreateTaskVO
 * @Description
 * @Author devin
 * @date 2025-01-29 18:12
 **/

@Data
@AllArgsConstructor
public class CreateAiTaskVO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private Long taskId;

    private ConversationVO conversation;

}
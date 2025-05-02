package cn.yifan.drawsee.pojo.rabbit;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;
import java.util.Map;

/**
 * @FileName TaskMessage
 * @Description
 * @Author yifan
 * @date 2025-01-29 21:43
 **/

@Data
@AllArgsConstructor
@NoArgsConstructor
public class AiTaskMessage implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private String type;

    private String prompt;

    private Map<String, String> promptParams;

    private String model;

    private Long taskId;

    private Long parentId;

    private Long convId;

    private Long userId;

    /**
     * 任务所属班级ID，用于指定知识库
     */
    private String classId;

}

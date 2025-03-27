package cn.yifan.drawsee.pojo.dto;

import cn.yifan.drawsee.annotation.ValueSet;
import cn.yifan.drawsee.constant.AiModel;
import cn.yifan.drawsee.constant.AiTaskType;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;
import java.util.Map;

/**
 * @FileName CreateTaskDTO
 * @Description
 * @Author yifan
 * @date 2025-01-29 18:13
 **/

@Data
@AllArgsConstructor
@NoArgsConstructor
public class CreateAiTaskDTO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @ValueSet(values = {
        AiTaskType.GENERAL,
        AiTaskType.KNOWLEDGE, AiTaskType.KNOWLEDGE_DETAIL,
        AiTaskType.ANIMATION,
        AiTaskType.SOLVER_FIRST, AiTaskType.SOLVER_CONTINUE, AiTaskType.SOLVER_SUMMARY,
        AiTaskType.PLANNER,
        AiTaskType.HTML_MAKER
    })
    private String type;

    private String prompt;

    private Map<String, String> promptParams;

    private String model;

    private Long convId;

    private Long parentId;

}

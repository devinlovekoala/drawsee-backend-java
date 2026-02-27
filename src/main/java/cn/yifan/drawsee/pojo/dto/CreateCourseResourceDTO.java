package cn.yifan.drawsee.pojo.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;
import java.util.Date;

/**
 * 创建课程资源DTO
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class CreateCourseResourceDTO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 资源类型: COURSEWARE / TASK / CIRCUIT_REF
     */
    @NotBlank(message = "资源类型不能为空")
    private String type;

    @NotBlank(message = "标题不能为空")
    private String title;

    private String description;

    /**
     * 任务内容 / 说明
     */
    private String content;

    private String fileUrl;

    private String fileName;

    private Long fileSize;

    private String coverUrl;

    private Date dueAt;
}

package cn.yifan.drawsee.pojo.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;

/**
 * @FileName CreateCourseDTO
 * @Description 创建课程的DTO类
 * @Author devin
 * @date 2025-03-28 10:47
 **/

@Data
@AllArgsConstructor
@NoArgsConstructor
public class CreateCourseDTO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @NotBlank(message = "课程名称不能为空")
    private String name;

    private String description;
} 
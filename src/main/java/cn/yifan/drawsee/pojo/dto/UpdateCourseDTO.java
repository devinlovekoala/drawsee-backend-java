package cn.yifan.drawsee.pojo.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;

/**
 * @FileName UpdateCourseDTO
 * @Description 更新课程的DTO类
 * @Author yifan
 * @date 2025-03-30 16:00
 **/

@Data
@AllArgsConstructor
@NoArgsConstructor
public class UpdateCourseDTO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @NotBlank(message = "课程名称不能为空")
    private String name;

    private String description;
    
    private String subject;
    
    private String code;
}
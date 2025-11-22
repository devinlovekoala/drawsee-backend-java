package cn.yifan.drawsee.pojo.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;

/**
 * @FileName JoinCourseDTO
 * @Description 加入课程的DTO类
 * @Author yifan
 * @date 2025-03-28 10:51
 **/

@Data
@AllArgsConstructor
@NoArgsConstructor
public class JoinCourseDTO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @NotBlank(message = "班级码不能为空")
    private String classCode;
} 
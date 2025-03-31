package cn.yifan.drawsee.pojo.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;

/**
 * @FileName JoinClassDTO
 * @Description 加入班级DTO
 * @Author devin
 * @date 2025-06-10 11:30
 **/

@Data
@AllArgsConstructor
@NoArgsConstructor
public class JoinClassDTO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @NotBlank(message = "班级码不能为空")
    private String classCode;
} 
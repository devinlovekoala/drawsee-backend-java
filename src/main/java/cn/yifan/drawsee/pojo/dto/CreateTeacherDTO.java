package cn.yifan.drawsee.pojo.dto;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;

/**
 * @FileName CreateTeacherDTO
 * @Description 创建教师的DTO类
 * @Author yifan
 * @date 2025-03-28 10:53
 **/

@Data
@AllArgsConstructor
@NoArgsConstructor
public class CreateTeacherDTO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @NotNull(message = "用户ID不能为空")
    private Long userId;
    
    private String title;
    
    private String organization;
} 
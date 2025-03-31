package cn.yifan.drawsee.pojo.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;

/**
 * @FileName TeacherRegisterDTO
 * @Description 教师注册DTO
 * @Author devin
 * @date 2025-06-10 11:35
 **/

@Data
@AllArgsConstructor
@NoArgsConstructor
public class TeacherRegisterDTO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private Long userId;
} 
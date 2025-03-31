package cn.yifan.drawsee.pojo.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;

/**
 * @FileName CreateTeacherInvitationCodeDTO
 * @Description 创建教师邀请码DTO类
 * @Author devin
 * @date 2025-06-11 14:35
 **/

@Data
@AllArgsConstructor
@NoArgsConstructor
public class CreateTeacherInvitationCodeDTO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @NotNull(message = "生成数量不能为空")
    @Min(value = 1, message = "生成数量最小为1")
    @Max(value = 100, message = "生成数量最大为100")
    private Integer count;
    
    private String remark;
} 
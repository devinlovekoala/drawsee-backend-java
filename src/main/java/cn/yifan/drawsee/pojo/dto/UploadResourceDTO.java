package cn.yifan.drawsee.pojo.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;

/**
 * @FileName UploadResourceDTO
 * @Description 上传资源的DTO类
 * @Author devin
 * @date 2025-03-28 10:55
 **/

@Data
@AllArgsConstructor
@NoArgsConstructor
public class UploadResourceDTO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @NotBlank(message = "知识点ID不能为空")
    private String knowledgeId;
    
    @NotBlank(message = "资源类型不能为空")
    private String resourceType;
} 
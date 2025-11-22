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
 * @Author yifan
 * @date 2025-03-28 10:55
 **/

@Data
@AllArgsConstructor
@NoArgsConstructor
public class UploadResourceDTO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @NotBlank(message = "知识库ID不能为空")
    private String knowledgeBaseId;
    
    @NotBlank(message = "资源类型不能为空")
    private String resourceType;
    
    @NotBlank(message = "标题不能为空")
    private String title;
    
    private String description;
    
    /**
     * 文件名
     */
    private String fileName;
    
    /**
     * 构造函数（仅含必要参数）
     * @param knowledgeBaseId 知识库ID
     * @param resourceType 资源类型
     * @param title 标题
     */
    public UploadResourceDTO(String knowledgeBaseId, String resourceType, String title) {
        this.knowledgeBaseId = knowledgeBaseId;
        this.resourceType = resourceType;
        this.title = title;
    }
} 
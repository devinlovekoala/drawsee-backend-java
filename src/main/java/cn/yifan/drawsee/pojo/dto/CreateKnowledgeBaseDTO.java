package cn.yifan.drawsee.pojo.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;

/**
 * @FileName CreateKnowledgeBaseDTO
 * @Description 创建知识库的DTO类
 * @Author devin
 * @date 2025-03-28 10:45
 **/

@Data
@AllArgsConstructor
@NoArgsConstructor
public class CreateKnowledgeBaseDTO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @NotBlank(message = "知识库名称不能为空")
    private String name;

    private String description;

    @NotBlank(message = "学科不能为空")
    private String subject;
} 
package cn.yifan.drawsee.pojo.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 创建知识库数据传输对象
 *
 * @author yifan
 * @date 2025-03-28 17:20
 */
@Data
public class CreateKnowledgeBaseDTO {

  /** 知识库ID，用于更新操作 */
  private String id;

  /** 知识库名称 */
  @NotBlank(message = "知识库名称不能为空")
  private String name;

  /** 知识库描述 */
  private String description;

  /** 知识库主题/学科 */
  private String subject;
}

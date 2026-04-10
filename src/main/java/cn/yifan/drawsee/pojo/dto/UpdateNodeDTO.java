package cn.yifan.drawsee.pojo.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.Map;
import lombok.Data;

@Data
public class UpdateNodeDTO {
  @NotNull(message = "节点ID不能为空")
  private Long id;

  @NotBlank(message = "节点类型不能为空")
  private String type;

  @NotBlank(message = "节点内容不能为空")
  private String content;

  @NotNull(message = "节点位置不能为空")
  private Position position;

  private Map<String, Object> data;

  @Data
  public static class Position {
    @NotNull(message = "X坐标不能为空")
    private Integer x;

    @NotNull(message = "Y坐标不能为空")
    private Integer y;
  }
}

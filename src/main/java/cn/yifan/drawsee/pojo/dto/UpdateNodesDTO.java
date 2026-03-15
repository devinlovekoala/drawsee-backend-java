package cn.yifan.drawsee.pojo.dto;

import cn.yifan.drawsee.pojo.XYPosition;
import jakarta.validation.constraints.NotEmpty;
import java.io.Serial;
import java.io.Serializable;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * @FileName UpdateNodeDTO @Description @Author yifan
 *
 * @date 2025-01-29 21:15
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class UpdateNodesDTO implements Serializable {

  @Data
  @AllArgsConstructor
  @NoArgsConstructor
  public static class NodeToUpdate {
    private Long id;
    private XYPosition position;
    private Long height;
  }

  @Serial private static final long serialVersionUID = 1L;

  @NotEmpty private List<NodeToUpdate> nodes;
}

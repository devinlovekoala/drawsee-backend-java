package cn.yifan.drawsee.pojo;

import java.io.Serial;
import java.io.Serializable;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * @FileName XYPosition @Description @Author yifan
 *
 * @date 2025-03-06 18:34
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class XYPosition implements Serializable {

  @Serial private static final long serialVersionUID = 1L;

  private Integer x;

  private Integer y;

  public static XYPosition origin() {
    return new XYPosition(0, 0);
  }
}

package cn.yifan.drawsee.pojo.rabbit;

import java.io.Serial;
import java.io.Serializable;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class AnimationTaskMessage implements Serializable {

  @Serial private static final long serialVersionUID = 1L;

  private Long aiTaskId;

  private Long nodeId;

  private String code;
}

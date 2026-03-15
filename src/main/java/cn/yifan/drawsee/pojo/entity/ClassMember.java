package cn.yifan.drawsee.pojo.entity;

import java.io.Serial;
import java.io.Serializable;
import java.sql.Timestamp;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * @FileName ClassMember @Description 班级成员实体类 @Author devin
 *
 * @date 2025-06-10 10:15
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class ClassMember implements Serializable {

  @Serial private static final long serialVersionUID = 1L;

  private Long id;

  private Long classId;

  private Long userId;

  private Timestamp joinedAt;

  private Boolean isDeleted;

  public ClassMember(Long classId, Long userId) {
    this.classId = classId;
    this.userId = userId;
    this.isDeleted = false;
  }
}

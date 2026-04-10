package cn.yifan.drawsee.pojo.entity;

import java.io.Serial;
import java.io.Serializable;
import java.sql.Timestamp;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * @FileName TeacherInvitationCode @Description 教师邀请码实体类 @Author devin
 *
 * @date 2025-06-11 14:20
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class TeacherInvitationCode implements Serializable {

  @Serial private static final long serialVersionUID = 1L;

  private Long id;

  private String code;

  private Timestamp createdAt;

  private Long createdBy;

  private Long usedBy;

  private Timestamp usedAt;

  private Boolean isActive;

  private String remark;

  // 关联的课程ID，可选
  private String courseId;

  public TeacherInvitationCode(String code, Long createdBy) {
    this.code = code;
    this.createdBy = createdBy;
    this.isActive = true;
  }
}

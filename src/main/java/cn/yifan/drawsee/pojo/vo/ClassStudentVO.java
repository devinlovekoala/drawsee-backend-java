package cn.yifan.drawsee.pojo.vo;

import java.io.Serial;
import java.io.Serializable;
import java.sql.Timestamp;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * @FileName ClassStudentVO @Description 班级学生信息VO @Author devin
 *
 * @date 2026-02-26
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class ClassStudentVO implements Serializable {

  @Serial private static final long serialVersionUID = 1L;

  private Long userId;

  private String username;

  private Timestamp joinedAt;
}

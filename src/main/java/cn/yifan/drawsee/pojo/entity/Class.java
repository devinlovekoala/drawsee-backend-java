package cn.yifan.drawsee.pojo.entity;

import java.io.Serial;
import java.io.Serializable;
import java.sql.Timestamp;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * @FileName Class @Description 班级实体类 @Author devin
 *
 * @date 2025-06-10 10:10
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class Class implements Serializable {

  @Serial private static final long serialVersionUID = 1L;

  private Long id;

  private String name;

  private String description;

  private String classCode;

  private Long teacherId;

  private Timestamp createdAt;

  private Timestamp updatedAt;

  private Boolean isDeleted;

  public Class(String name, String description, String classCode, Long teacherId) {
    this.name = name;
    this.description = description;
    this.classCode = classCode;
    this.teacherId = teacherId;
    this.isDeleted = false;
  }
}

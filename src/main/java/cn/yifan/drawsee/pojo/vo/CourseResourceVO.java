package cn.yifan.drawsee.pojo.vo;

import java.io.Serial;
import java.io.Serializable;
import java.util.Date;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 课程资源VO */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class CourseResourceVO implements Serializable {

  @Serial private static final long serialVersionUID = 1L;

  private Long id;

  private String courseId;

  private String type;

  private String title;

  private String description;

  private String content;

  private String fileUrl;

  private String fileName;

  private Long fileSize;

  private String coverUrl;

  private Date dueAt;

  private Long createdBy;

  private Date createdAt;

  private Date updatedAt;
}

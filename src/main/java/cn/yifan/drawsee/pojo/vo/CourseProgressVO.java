package cn.yifan.drawsee.pojo.vo;

import java.io.Serial;
import java.io.Serializable;
import java.util.Date;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * @FileName CourseProgressVO @Description 课程学习进度VO类 @Author yifan
 *
 * @date 2025-03-28 11:00
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class CourseProgressVO implements Serializable {

  @Serial private static final long serialVersionUID = 1L;

  /** 已完成知识点数 */
  private Integer completedKnowledgePoints;

  /** 总知识点数 */
  private Integer totalKnowledgePoints;

  /** 最后访问时间 */
  private Date lastAccessTime;

  /** 总学习时长（分钟） */
  private Long totalLearningTime;
}

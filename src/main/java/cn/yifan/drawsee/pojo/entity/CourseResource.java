package cn.yifan.drawsee.pojo.entity;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.util.Date;

/**
 * 课程资源实体
 * 用于课程中心的课件、任务、参考电路图等内容
 */
@Data
public class CourseResource implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private Long id;

    private String courseId;

    /**
     * 资源类型: COURSEWARE / TASK / CIRCUIT_REF
     */
    private String type;

    private String title;

    private String description;

    /**
     * 任务/说明内容
     */
    private String content;

    private String fileUrl;

    private String fileName;

    private Long fileSize;

    private String coverUrl;

    private Date dueAt;

    private Long createdBy;

    private Date createdAt;

    private Date updatedAt;

    private Boolean isDeleted;
}

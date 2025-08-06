package cn.yifan.drawsee.pojo.entity;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * @FileName Course
 * @Description 课程实体类 - MySQL版本
 * @Author devin
 * @date 2025-04-10 15:40
 **/
@Data
@AllArgsConstructor
@NoArgsConstructor
public class Course implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;
    
    /**
     * 主键ID
     */
    private String id;

    /**
     * 课程名称
     */
    private String name;

    /**
     * 课程代码
     */
    private String code;

    /**
     * 班级码
     */
    private String classCode;

    /**
     * 课程描述
     */
    private String description;

    /**
     * 课程科目
     */
    private String subject;

    /**
     * 课程主题列表（JSON存储）
     */
    private List<String> topics = new ArrayList<>();

    /**
     * 创建者ID
     */
    private Long creatorId;

    /**
     * 创建者角色
     */
    private String creatorRole;

    /**
     * 学生ID列表（JSON存储）
     */
    private List<Long> studentIds = new ArrayList<>();

    /**
     * 知识库ID列表（JSON存储）
     */
    private List<String> knowledgeBaseIds = new ArrayList<>();

    /**
     * 创建时间
     */
    private Date createdAt;

    /**
     * 更新时间
     */
    private Date updatedAt;

    /**
     * 是否已删除
     */
    private Boolean isDeleted = false;
} 
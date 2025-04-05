package cn.yifan.drawsee.pojo.mongo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.io.Serial;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * @FileName Course
 * @Description 课程实体类
 * @Author devin
 * @date 2025-03-28 14:45
 **/
@Data
@AllArgsConstructor
@NoArgsConstructor
@Document(collection = "courses")
public class Course implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;
    /**
     * 课程ID
     */
    @Id
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
     * 班级代码
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
     * 课程主题列表
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
     * 学生ID列表
     */
    private List<Long> studentIds = new ArrayList<>();
    
    /**
     * 知识库ID列表
     */
    private List<String> knowledgeBaseIds = new ArrayList<>();
    
    /**
     * 创建时间
     */
    private Long createdAt;
    
    /**
     * 更新时间
     */
    private Long updatedAt;
    
    /**
     * 是否已删除
     */
    private Boolean isDeleted = false;
} 
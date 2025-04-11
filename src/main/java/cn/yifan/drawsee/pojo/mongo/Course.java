package cn.yifan.drawsee.pojo.mongo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

import java.io.Serial;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Date;
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
    @Id
    private String id;

    @Field("name")
    private String name;

    @Field("code")
    private String code;

    @Field("class_code")
    private String classCode;

    @Field("description")
    private String description;

    @Field("subject")
    private String subject;

    @Field("topics")
    private List<String> topics = new ArrayList<>();

    @Field("creator_id")
    private Long creatorId;

    @Field("creator_role")
    private String creatorRole;

    @Field("student_ids")
    private List<Long> studentIds = new ArrayList<>();

    @Field("knowledge_base_ids")
    private List<String> knowledgeBaseIds = new ArrayList<>();

    @Field("created_at")
    private Date createdAt;

    @Field("updated_at")
    private Date updatedAt;

    @Field("is_deleted")
    private Boolean isDeleted = false;
} 
package cn.yifan.drawsee.pojo.mongo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

import java.io.Serial;
import java.io.Serializable;
import java.util.Date;
import java.util.List;

/**
 * @FileName Course
 * @Description 课程实体类
 * @Author devin
 * @date 2025-03-28 10:32
 **/

@Data
@AllArgsConstructor
@NoArgsConstructor
@Document(collection = "course")
public class Course implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Id
    private String id;

    @Field("name")
    private String name;

    @Field("description")
    private String description;

    @Field("class_code")
    private String classCode;

    @Field("creator_id")
    private Long creatorId;
    
    @Field("creator_role")
    private String creatorRole;

    @Field("created_at")
    private Date createdAt;

    @Field("updated_at")
    private Date updatedAt;

    @Field("student_ids")
    private List<Long> studentIds;
    
    @Field("knowledge_base_ids")
    private List<String> knowledgeBaseIds;
    
    @Field("is_deleted")
    private Boolean isDeleted = false;
} 
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
 * @FileName KnowledgeBase
 * @Description 知识库实体类
 * @Author devin
 * @date 2025-03-28 10:30
 **/

@Data
@AllArgsConstructor
@NoArgsConstructor
@Document(collection = "knowledge_base")
public class KnowledgeBase implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Id
    private String id;

    @Field("name")
    private String name;

    @Field("description")
    private String description;

    @Field("subject")
    private String subject;

    @Field("invitation_code")
    private String invitationCode;

    @Field("creator_id")
    private Long creatorId;

    @Field("created_at")
    private Date createdAt;

    @Field("updated_at")
    private Date updatedAt;

    @Field("knowledge_ids")
    private List<String> knowledgeIds;
    
    @Field("members")
    private List<Long> members;
    
    @Field("is_deleted")
    private Boolean isDeleted = false;
    
    @Field("is_published")
    private Boolean isPublished = false;
}
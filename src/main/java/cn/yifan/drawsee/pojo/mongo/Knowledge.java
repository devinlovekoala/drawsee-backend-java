package cn.yifan.drawsee.pojo.mongo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;

/**
 * @FileName KnowledgeNode
 * @Description
 * @Author yifan
 * @date 2025-01-31 11:31
 **/

@Data
@AllArgsConstructor
@NoArgsConstructor
@Document(collection = "knowledge")
public class Knowledge implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Id
    private String id;

    @Field("subject")
    private String subject;

    @Field("name")
    private String name;

    @Field("aliases")
    private List<String> aliases;

    @Field("resources")
    private List<KnowledgeResource> resources;

    @Field("level")
    private Integer level;

    @Field("parentId")
    private String parentId;

    @Field("childrenIds")
    private List<String> childrenIds;
    
    @Field("knowledge_base_id")
    private String knowledgeBaseId;
}

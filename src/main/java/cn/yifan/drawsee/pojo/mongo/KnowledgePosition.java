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

/**
 * @FileName KnowledgePosition
 * @Description 知识点位置信息
 * @Author devin
 * @date 2025-03-30 10:15
 **/

@Data
@AllArgsConstructor
@NoArgsConstructor
@Document(collection = "knowledge_node_position")
public class KnowledgePosition implements Serializable {
    
    @Serial
    private static final long serialVersionUID = 1L;
    
    @Id
    private String id;
    
    @Field("knowledge_id")
    private String knowledgeId;
    
    @Field("knowledge_base_id")
    private String knowledgeBaseId;
    
    @Field("x")
    private Double x;
    
    @Field("y")
    private Double y;
    
    @Field("created_at")
    private Date createdAt;
    
    @Field("updated_at")
    private Date updatedAt;
}
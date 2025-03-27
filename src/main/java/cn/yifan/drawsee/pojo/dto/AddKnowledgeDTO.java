package cn.yifan.drawsee.pojo.dto;

import cn.yifan.drawsee.pojo.mongo.KnowledgeResource;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.mongodb.core.mapping.Field;

import java.util.List;

/**
 * @FileName AddKnowledgeDTO
 * @Description
 * @Author yifan
 * @date 2025-03-06 16:52
 **/

@Data
@AllArgsConstructor
@NoArgsConstructor
public class AddKnowledgeDTO {

    private String name;

    private List<String> aliases;

    private List<KnowledgeResource> resources;

    private String parentId;

}

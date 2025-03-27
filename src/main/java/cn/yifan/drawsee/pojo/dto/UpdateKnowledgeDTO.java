package cn.yifan.drawsee.pojo.dto;

import cn.yifan.drawsee.pojo.mongo.KnowledgeResource;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * @FileName UpdateKnowledgeDTO
 * @Description
 * @Author yifan
 * @date 2025-03-06 16:57
 **/

@Data
@AllArgsConstructor
@NoArgsConstructor
public class UpdateKnowledgeDTO {

    private String name;

    private List<String> aliases;

    private List<KnowledgeResource> resources;

    private String parentId;

}

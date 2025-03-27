package cn.yifan.drawsee.pojo.mongo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * @FileName KnowledgeResource
 * @Description
 * @Author yifan
 * @date 2025-01-31 11:37
 **/

@Data
@AllArgsConstructor
@NoArgsConstructor
public class KnowledgeResource {

    private String type;

    private String value;

}

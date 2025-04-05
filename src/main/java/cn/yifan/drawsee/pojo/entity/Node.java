package cn.yifan.drawsee.pojo.entity;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;
import java.sql.Timestamp;

/**
 * @FileName Node
 * @Description
 * @Author yifan
 * @date 2025-01-27 23:54
 **/

@Data
@AllArgsConstructor
@NoArgsConstructor
public class Node implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private Long id;

    // 节点类型
    private String type;

    // 节点数据
    private String data;

    // 节点位置
    private String position;

    // 节点高度
    private Long height;

    // 节点宽度
    private Long width;

    // 节点父节点ID
    private Long parentId;

    // 节点所属会话ID
    private Long convId;

    // 节点所属用户ID
    private Long userId;

    // 节点创建时间
    private Timestamp createdAt;

    // 节点更新时间
    private Timestamp updatedAt;

    // 节点是否删除
    private Boolean isDeleted;

    public Node(
            String type, String data,
            String position, Long parentId,
            Long userId, Long convId,
            Boolean isDeleted
    ) {
        this.type = type;
        this.data = data;
        this.position = position;
        this.parentId = parentId;
        this.userId = userId;
        this.convId = convId;
        this.isDeleted = isDeleted;
    }

}
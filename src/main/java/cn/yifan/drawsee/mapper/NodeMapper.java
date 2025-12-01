package cn.yifan.drawsee.mapper;

import cn.yifan.drawsee.pojo.entity.Node;
import org.apache.ibatis.annotations.Mapper;
import java.util.List;

/**
 * @FileName NodeMapper
 * @Description
 * @Author yifan
 * @date 2025-01-28 10:02
 **/

@Mapper
public interface NodeMapper {

    Node getById(Long id);

    List<Node> getByConvId(Long convId);

    void insert(Node node);

    void update(Node node);

    void updateDataAndIsDeletedBatch(List<Node> nodes);

    void updatePositionAndHeightBatch(List<Node> nodes);

    /**
     * 将指定类型的节点标记为已删除（软删除），返回受影响的记录数
     * @param type 节点类型
     * @return 受影响行数
     */
    int softDeleteByType(String type);

}

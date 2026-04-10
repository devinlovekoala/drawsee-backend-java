package cn.yifan.drawsee.mapper;

import cn.yifan.drawsee.pojo.entity.Node;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;

/**
 * @FileName NodeMapper @Description @Author yifan
 *
 * @date 2025-01-28 10:02
 */
@Mapper
public interface NodeMapper {

  Node getById(Long id);

  List<Node> getByConvId(Long convId);

  void insert(Node node);

  void update(Node node);

  void updateDataAndIsDeletedBatch(List<Node> nodes);

  void updatePositionAndHeightBatch(List<Node> nodes);
}

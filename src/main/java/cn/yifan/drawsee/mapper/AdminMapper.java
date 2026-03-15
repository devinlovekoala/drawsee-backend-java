package cn.yifan.drawsee.mapper;

import cn.yifan.drawsee.pojo.entity.Admin;
import org.apache.ibatis.annotations.Mapper;

/**
 * @FileName AdminMapper @Description @Author yifan
 *
 * @date 2025-03-26 12:54
 */
@Mapper
public interface AdminMapper {

  Admin getByUserId(Long userId);

  void insert(Admin admin);
}

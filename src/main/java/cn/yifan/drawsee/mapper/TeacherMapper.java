package cn.yifan.drawsee.mapper;

import cn.yifan.drawsee.pojo.entity.Teacher;
import org.apache.ibatis.annotations.Mapper;

/**
 * @FileName TeacherMapper @Description 教师Mapper接口 @Author yifan
 *
 * @date 2025-03-28 10:42
 */
@Mapper
public interface TeacherMapper {

  Teacher getByUserId(Long userId);

  void insert(Teacher teacher);

  void update(Teacher teacher);
}

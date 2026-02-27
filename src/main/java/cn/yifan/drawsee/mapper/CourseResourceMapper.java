package cn.yifan.drawsee.mapper;

import cn.yifan.drawsee.pojo.entity.CourseResource;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface CourseResourceMapper {

    int insert(CourseResource resource);

    int update(CourseResource resource);

    CourseResource getById(@Param("id") Long id);

    List<CourseResource> listByCourseId(@Param("courseId") String courseId);

    List<CourseResource> listByCourseIdAndType(@Param("courseId") String courseId, @Param("type") String type);
}

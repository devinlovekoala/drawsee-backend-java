package cn.yifan.drawsee.mapper;

import cn.yifan.drawsee.pojo.entity.Class;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

/**
 * @FileName ClassMapper
 * @Description 班级Mapper接口
 * @Author yifan
 * @date 2025-06-10 10:30
 **/

@Mapper
public interface ClassMapper {
    
    void insert(Class clazz);
    
    Class getById(Long id);
    
    Class getByClassCode(String classCode);
    
    List<Class> getByTeacherId(Long teacherId);
    
    void update(Class clazz);
    
    List<Class> getByPage(int offset, int limit);
} 
package cn.yifan.drawsee.repository;

import cn.yifan.drawsee.pojo.mongo.Course;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * @FileName CourseRepository
 * @Description 课程Repository接口
 * @Author devin
 * @date 2025-03-28 10:40
 **/

@Repository
public interface CourseRepository extends MongoRepository<Course, String> {

    Course findByName(String name);

    List<Course> findAllByCreatorId(Long creatorId);
    
    Course findByClassCode(String classCode);
    
    List<Course> findByStudentIdsContaining(Long studentId);
} 
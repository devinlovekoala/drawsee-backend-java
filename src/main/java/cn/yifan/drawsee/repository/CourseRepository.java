package cn.yifan.drawsee.repository;

import cn.yifan.drawsee.pojo.mongo.Course;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * @FileName CourseRepository
 * @Description 课程数据访问层
 * @Author devin
 * @date 2025-03-28 14:45
 **/
@Repository
public interface CourseRepository extends MongoRepository<Course, String> {

    /**
     * 根据科目查询课程
     *
     * @param subject  科目
     * @param pageable 分页参数
     * @return 课程分页列表
     */
    @Query("{'subject': ?0, 'isDeleted': false}")
    Page<Course> findBySubject(String subject, Pageable pageable);

    /**
     * 根据学生ID查询已加入的课程
     *
     * @param studentId 学生ID
     * @param pageable  分页参数
     * @return 课程分页列表
     */
    @Query("{'studentIds': ?0, 'isDeleted': false}")
    Page<Course> findByStudentIdsContaining(Long studentId, Pageable pageable);

    /**
     * 根据创建者ID查询创建的课程
     *
     * @param creatorId 创建者ID
     * @param pageable  分页参数
     * @return 课程分页列表
     */
    @Query("{'creatorId': ?0, 'isDeleted': false}")
    Page<Course> findByCreatorId(Long creatorId, Pageable pageable);

    /**
     * 根据课程代码查询课程
     *
     * @param code 课程代码
     * @return 课程信息
     */
    Optional<Course> findByCode(String code);

    /**
     * 根据课程名称查询课程
     *
     * @param name 课程名称
     * @return 课程信息
     */
    @Query("{'name': ?0, 'isDeleted': false}")
    Course findByName(String name);

    /**
     * 根据创建者ID查询所有课程
     *
     * @param creatorId 创建者ID
     * @return 课程列表
     */
    @Query("{'creatorId': ?0, 'isDeleted': false}")
    List<Course> findAllByCreatorId(Long creatorId);
    
    /**
     * 根据班级码查询课程
     *
     * @param classCode 班级码
     * @return 课程信息
     */
    @Query("{'classCode': ?0, 'isDeleted': false}")
    Course findByClassCode(String classCode);
    
    /**
     * 根据学生ID查询已加入的课程列表
     *
     * @param studentId 学生ID
     * @return 课程列表
     */
    @Query("{'studentIds': ?0, 'isDeleted': false}")
    List<Course> findByStudentIdsContaining(Long studentId);
} 
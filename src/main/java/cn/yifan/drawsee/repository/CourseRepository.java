package cn.yifan.drawsee.repository;

import cn.yifan.drawsee.pojo.mongo.Course;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;
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
    
    // 使用Spring Data MongoDB的命名规则，自动生成查询方法
    Page<Course> findBySubjectAndIsDeletedFalse(String subject, Pageable pageable);
    
    Page<Course> findByStudentIdsContainingAndIsDeletedFalse(Long studentId, Pageable pageable);
    
    Page<Course> findByCreatorIdAndIsDeletedFalse(Long creatorId, Pageable pageable);
    
    Optional<Course> findByCodeAndIsDeletedFalse(String code);
    
    Course findByNameAndIsDeletedFalse(String name);
    
    List<Course> findByCreatorIdAndIsDeletedFalse(Long creatorId);
    
    Course findByClassCodeAndIsDeletedFalse(String classCode);
    
    List<Course> findByStudentIdsContainingAndIsDeletedFalse(Long studentId);
    
    Page<Course> findByIsDeletedFalse(Pageable pageable);
    
    // 新增：根据科目和创建者角色列表查询课程
    Page<Course> findBySubjectAndIsDeletedFalseAndCreatorRoleIn(
        String subject, 
        List<String> creatorRoles, 
        Pageable pageable
    );
    
    // 新增：根据创建者角色列表查询课程
    Page<Course> findByIsDeletedFalseAndCreatorRoleIn(
        List<String> creatorRoles, 
        Pageable pageable
    );
    
    // 新增：根据科目和创建者角色或学生ID查询课程
    Page<Course> findBySubjectAndIsDeletedFalseAndCreatorRoleOrStudentIdsContaining(
        String subject,
        String creatorRole,
        Long studentId,
        Pageable pageable
    );
    
    // 新增：根据创建者角色或学生ID查询课程
    Page<Course> findByIsDeletedFalseAndCreatorRoleOrStudentIdsContaining(
        String creatorRole,
        Long studentId,
        Pageable pageable
    );
} 
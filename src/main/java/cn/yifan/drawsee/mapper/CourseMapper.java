package cn.yifan.drawsee.mapper;

import cn.yifan.drawsee.pojo.entity.Course;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * @FileName CourseMapper
 * @Description 课程数据访问层
 * @Author yifan
 * @date 2025-04-10 15:30
 **/
@Mapper
public interface CourseMapper {
    
    /**
     * 插入课程
     * @param course 课程对象
     * @return 影响行数
     */
    int insert(Course course);
    
    /**
     * 更新课程
     * @param course 课程对象
     * @return 影响行数
     */
    int update(Course course);
    
    /**
     * 根据ID获取课程
     * @param id 课程ID
     * @return 课程对象
     */
    Course getById(String id);
    
    /**
     * 根据名称获取课程（未删除的）
     * @param name 课程名称
     * @return 课程对象
     */
    Course getByName(@Param("name") String name);
    
    /**
     * 根据班级码获取课程（未删除的）
     *
     * @param classCode 班级码
     * @return 课程对象
     */
    Course getByClassCode(@Param("classCode") String classCode);
    
    /**
     * 根据课程码获取课程（未删除的）
     * @param code 课程码
     * @return 课程对象
     */
    Course getByCode(@Param("code") String code);
    
    /**
     * 根据创建者ID获取课程列表（未删除的）
     * @param creatorId 创建者ID
     * @param isDeleted 是否删除
     * @return 课程列表
     */
    List<Course> getByCreatorId(@Param("creatorId") Long creatorId, @Param("isDeleted") boolean isDeleted);
    
    /**
     * 根据学生ID获取课程列表（未删除的）
     * @param studentId 学生ID
     * @param isDeleted 是否删除
     * @return 课程列表
     */
    List<Course> getByStudentId(@Param("studentId") Long studentId, @Param("isDeleted") boolean isDeleted);
    
    /**
     * 获取所有课程（未删除的）
     * @param isDeleted 是否删除
     * @return 课程列表
     */
    List<Course> getAll(@Param("isDeleted") boolean isDeleted);
    
    /**
     * 根据科目获取课程列表（未删除的）
     * @param subject 科目
     * @param isDeleted 是否删除
     * @return 课程列表
     */
    List<Course> getBySubject(@Param("subject") String subject, @Param("isDeleted") boolean isDeleted);
    
    /**
     * 根据创建者角色获取课程列表（未删除的）
     * @param creatorRole 创建者角色
     * @param isDeleted 是否删除
     * @return 课程列表
     */
    List<Course> getByCreatorRole(@Param("creatorRole") String creatorRole, @Param("isDeleted") boolean isDeleted);
    
    /**
     * 根据科目和创建者角色获取课程列表（未删除的）
     * @param subject 科目
     * @param creatorRole 创建者角色
     * @param isDeleted 是否删除
     * @return 课程列表
     */
    List<Course> getBySubjectAndCreatorRole(@Param("subject") String subject, 
                                           @Param("creatorRole") String creatorRole, 
                                           @Param("isDeleted") boolean isDeleted);
    
    /**
     * 分页获取课程列表（未删除的）
     * @param offset 偏移量
     * @param limit 限制
     * @param isDeleted 是否删除
     * @return 课程列表
     */
    List<Course> getPage(@Param("offset") int offset, 
                        @Param("limit") int limit, 
                        @Param("isDeleted") boolean isDeleted);
    
    /**
     * 获取课程总数（未删除的）
     * @param isDeleted 是否删除
     * @return 课程总数
     */
    int count(@Param("isDeleted") boolean isDeleted);
    
    /**
     * 根据科目获取课程总数（未删除的）
     * @param subject 科目
     * @param isDeleted 是否删除
     * @return 课程总数
     */
    int countBySubject(@Param("subject") String subject, @Param("isDeleted") boolean isDeleted);
    
    /**
     * 根据创建者角色获取课程总数（未删除的）
     * @param creatorRole 创建者角色
     * @param isDeleted 是否删除
     * @return 课程总数
     */
    int countByCreatorRole(@Param("creatorRole") String creatorRole, @Param("isDeleted") boolean isDeleted);
    
    /**
     * 根据科目和创建者角色获取课程总数（未删除的）
     * @param subject 科目
     * @param creatorRole 创建者角色
     * @param isDeleted 是否删除
     * @return 课程总数
     */
    int countBySubjectAndCreatorRole(@Param("subject") String subject, 
                                    @Param("creatorRole") String creatorRole, 
                                    @Param("isDeleted") boolean isDeleted);
    
    /**
     * 添加学生到课程
     * @param courseId 课程ID
     * @param studentId 学生ID
     * @return 影响行数
     */
    int addStudent(@Param("courseId") String courseId, @Param("studentId") Long studentId);
    
    /**
     * 从课程中移除学生
     * @param courseId 课程ID
     * @param studentId 学生ID
     * @return 影响行数
     */
    int removeStudent(@Param("courseId") String courseId, @Param("studentId") Long studentId);
    
    /**
     * 添加知识库到课程
     * @param courseId 课程ID
     * @param knowledgeBaseId 知识库ID
     * @return 影响行数
     */
    int addKnowledgeBase(@Param("courseId") String courseId, @Param("knowledgeBaseId") String knowledgeBaseId);
    
    /**
     * 从课程中移除知识库
     * @param courseId 课程ID
     * @param knowledgeBaseId 知识库ID
     * @return 影响行数
     */
    int removeKnowledgeBase(@Param("courseId") String courseId, @Param("knowledgeBaseId") String knowledgeBaseId);
} 
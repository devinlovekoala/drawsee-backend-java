package cn.yifan.drawsee.service.business;

import cn.dev33.satoken.stp.StpUtil;
import cn.yifan.drawsee.exception.ApiError;
import cn.yifan.drawsee.exception.ApiException;
import cn.yifan.drawsee.pojo.dto.CreateCourseDTO;
import cn.yifan.drawsee.pojo.dto.JoinCourseDTO;
import cn.yifan.drawsee.pojo.mongo.Course;
import cn.yifan.drawsee.pojo.vo.CourseVO;
import cn.yifan.drawsee.repository.CourseRepository;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Random;

/**
 * @FileName CourseService
 * @Description 课程服务类
 * @Author devin
 * @date 2025-03-28 11:07
 **/

@Service
public class CourseService {

    @Autowired
    private CourseRepository courseRepository;
    
    @Autowired
    private TeacherService teacherService;

    /**
     * 创建课程
     * @param createCourseDTO 创建课程DTO
     * @return 课程ID
     */
    public String createCourse(CreateCourseDTO createCourseDTO) {
        // 验证用户是否为教师
        teacherService.validateTeacher();
        
        // 检查课程名称是否已存在
        Course existCourse = courseRepository.findByName(createCourseDTO.getName());
        if (existCourse != null) {
            throw new ApiException(ApiError.COURSE_HAD_EXISTED);
        }
        
        // 生成班级码
        String classCode = generateClassCode();
        
        // 创建课程
        Course course = new Course();
        course.setName(createCourseDTO.getName());
        course.setDescription(createCourseDTO.getDescription());
        course.setClassCode(classCode);
        course.setCreatorId(StpUtil.getLoginIdAsLong());
        course.setCreatedAt(new Date());
        course.setUpdatedAt(new Date());
        course.setStudentIds(new ArrayList<>());
        course.setKnowledgeBaseIds(new ArrayList<>());
        course.setIsDeleted(false);
        
        courseRepository.save(course);
        
        return course.getId();
    }
    
    /**
     * 加入课程
     * @param joinCourseDTO 加入课程DTO
     * @return 课程ID
     */
    public String joinCourse(JoinCourseDTO joinCourseDTO) {
        // 检查班级码是否存在
        Course course = courseRepository.findByClassCode(joinCourseDTO.getClassCode());
        if (course == null) {
            throw new ApiException(ApiError.INVALID_CLASS_CODE);
        }
        
        Long userId = StpUtil.getLoginIdAsLong();
        
        // 检查用户是否已加入该课程
        if (course.getStudentIds().contains(userId)) {
            throw new ApiException(ApiError.ALREADY_JOINED);
        }
        
        // 加入课程
        course.getStudentIds().add(userId);
        course.setUpdatedAt(new Date());
        courseRepository.save(course);
        
        return course.getId();
    }
    
    /**
     * 获取我创建的课程列表
     * @return 课程列表
     */
    public List<CourseVO> getMyCreatedCourses() {
        Long userId = StpUtil.getLoginIdAsLong();
        List<Course> courses = courseRepository.findAllByCreatorId(userId);
        return convertToVOList(courses);
    }
    
    /**
     * 获取我加入的课程列表
     * @return 课程列表
     */
    public List<CourseVO> getMyJoinedCourses() {
        Long userId = StpUtil.getLoginIdAsLong();
        List<Course> courses = courseRepository.findByStudentIdsContaining(userId);
        return convertToVOList(courses);
    }
    
    /**
     * 获取课程详情
     * @param id 课程ID
     * @return 课程详情
     */
    public CourseVO getCourseDetail(String id) {
        Course course = courseRepository.findById(id).orElse(null);
        if (course == null) {
            throw new ApiException(ApiError.COURSE_NOT_EXISTED);
        }
        
        CourseVO courseVO = new CourseVO();
        BeanUtils.copyProperties(course, courseVO);
        courseVO.setStudentCount(course.getStudentIds().size());
        
        return courseVO;
    }
    
    /**
     * 生成班级码
     * @return 班级码
     */
    private String generateClassCode() {
        String characters = "0123456789"; // 纯数字班级码
        Random random = new SecureRandom();
        StringBuilder sb = new StringBuilder(6);

        for (int i = 0; i < 6; i++) {
            sb.append(characters.charAt(random.nextInt(characters.length())));
        }

        String code = sb.toString();
        // 检查是否已存在（极小概率冲突时可重试）
        Course existCourse = courseRepository.findByClassCode(code);
        if (existCourse != null) {
            return generateClassCode();
        }
        return code;
    }
    
    /**
     * 将课程列表转换为VO列表
     * @param courses 课程列表
     * @return VO列表
     */
    private List<CourseVO> convertToVOList(List<Course> courses) {
        List<CourseVO> result = new ArrayList<>();
        for (Course course : courses) {
            if (course.getIsDeleted()) {
                continue;
            }
            
            CourseVO vo = new CourseVO();
            BeanUtils.copyProperties(course, vo);
            vo.setStudentCount(course.getStudentIds().size());
            result.add(vo);
        }
        return result;
    }
} 
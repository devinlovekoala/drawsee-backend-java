package cn.yifan.drawsee.service.business;

import cn.dev33.satoken.stp.StpUtil;
import cn.yifan.drawsee.constant.UserRole;
import cn.yifan.drawsee.exception.ApiError;
import cn.yifan.drawsee.exception.ApiException;
import cn.yifan.drawsee.pojo.dto.CreateCourseDTO;
import cn.yifan.drawsee.pojo.dto.JoinCourseDTO;
import cn.yifan.drawsee.pojo.dto.PaginationParams;
import cn.yifan.drawsee.pojo.dto.UpdateCourseDTO;
import cn.yifan.drawsee.pojo.mongo.Course;
import cn.yifan.drawsee.pojo.mongo.KnowledgeBase;
import cn.yifan.drawsee.pojo.vo.*;
import cn.yifan.drawsee.repository.CourseRepository;
import cn.yifan.drawsee.repository.KnowledgeBaseRepository;
import cn.yifan.drawsee.service.business.TeacherInvitationCodeService;
import cn.yifan.drawsee.service.business.KnowledgeBaseService;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import lombok.extern.slf4j.Slf4j;

import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

/**
 * @FileName CourseService
 * @Description 课程服务类
 * @Author devin
 * @date 2025-03-28 11:12
 **/
@Slf4j
@Service
public class CourseService {

    @Autowired
    private CourseRepository courseRepository;
    
    @Autowired
    private TeacherService teacherService;
    
    @Autowired
    private UserRoleService userRoleService;
    
    @Autowired
    private TeacherInvitationCodeService teacherInvitationCodeService;
    
    @Autowired
    private KnowledgeBaseRepository knowledgeBaseRepository;
    
    @Autowired
    private KnowledgeBaseService knowledgeBaseService;

    /**
     * 创建课程
     * @param createCourseDTO 创建课程DTO
     * @return 课程ID
     */
    public String createCourse(CreateCourseDTO createCourseDTO) {
        Long userId = StpUtil.getLoginIdAsLong();
        String userRole = userRoleService.getCurrentUserRole();
        log.info("开始创建课程: userId={}, userRole={}, courseDTO={}", userId, userRole, createCourseDTO);
        
        // 验证用户是否为教师或管理员
        if (!UserRole.ADMIN.equals(userRole) && !UserRole.TEACHER.equals(userRole)) {
            log.warn("用户权限不足: userId={}, userRole={}", userId, userRole);
            throw new ApiException(ApiError.PERMISSION_DENIED);
        }
        
        // 检查课程名称是否已存在
        Course existCourse = courseRepository.findByNameAndIsDeletedFalse(createCourseDTO.getName());
        if (existCourse != null) {
            log.warn("课程名称已存在: name={}, existCourseId={}", createCourseDTO.getName(), existCourse.getId());
            throw new ApiException(ApiError.COURSE_HAD_EXISTED);
        }
        
        // 生成班级码
        String classCode = generateClassCode();
        log.info("生成班级码: classCode={}", classCode);
        
        // 创建课程
        Course course = new Course(
            null, // MongoDB会自动生成ID
            createCourseDTO.getName(),
            createCourseDTO.getCode(),
            classCode,
            createCourseDTO.getDescription(),
            createCourseDTO.getSubject(),
            new ArrayList<>(), // topics
            userId,
            userRole,
            new ArrayList<>(), // studentIds
            new ArrayList<>(), // knowledgeBaseIds
            new Date(), // createdAt
            new Date(), // updatedAt
            false // isDeleted
        );
        
        Course savedCourse = courseRepository.save(course);
        log.info("课程创建成功: courseId={}, name={}, classCode={}", savedCourse.getId(), savedCourse.getName(), savedCourse.getClassCode());
        
        // 如果创建者是教师，为其生成班级邀请码
        if (UserRole.TEACHER.equals(userRole)) {
            teacherInvitationCodeService.generateCodeForCourse(savedCourse.getId(), userId);
            log.info("已为教师生成班级邀请码: courseId={}, teacherId={}", savedCourse.getId(), userId);
        }
        
        return savedCourse.getId();
    }
    
    /**
     * 加入课程
     * @param joinCourseDTO 加入课程DTO
     * @return 课程ID
     */
    public String joinCourse(JoinCourseDTO joinCourseDTO) {
        Long userId = StpUtil.getLoginIdAsLong();
        log.info("开始加入课程: userId={}, classCode={}", userId, joinCourseDTO.getClassCode());
        
        // 检查班级码是否存在
        Course course = courseRepository.findByClassCodeAndIsDeletedFalse(joinCourseDTO.getClassCode());
        if (course == null) {
            log.warn("班级码无效: classCode={}", joinCourseDTO.getClassCode());
            throw new ApiException(ApiError.INVALID_CLASS_CODE);
        }
        
        // 检查用户是否已加入该课程
        if (course.getStudentIds().contains(userId)) {
            log.warn("用户已加入该课程: userId={}, courseId={}", userId, course.getId());
            throw new ApiException(ApiError.ALREADY_JOINED);
        }
        
        // 加入课程
        course.getStudentIds().add(userId);
        course.setUpdatedAt(new Date());
        Course updatedCourse = courseRepository.save(course);
        log.info("用户成功加入课程: userId={}, courseId={}, studentCount={}", 
            userId, updatedCourse.getId(), updatedCourse.getStudentIds().size());
        
        return course.getId();
    }
    
    /**
     * 获取我创建的课程列表
     * @return 课程列表
     */
    public List<CourseVO> getMyCreatedCourses() {
        Long userId = StpUtil.getLoginIdAsLong();
        log.info("获取用户创建的课程: userId={}", userId);
        List<Course> courses = courseRepository.findByCreatorIdAndIsDeletedFalse(userId);
        log.info("查询结果: courses={}", courses);
        return convertToVOList(courses);
    }
    
    /**
     * 获取我加入的课程列表
     * @return 课程列表
     */
    public List<CourseVO> getMyJoinedCourses() {
        Long userId = StpUtil.getLoginIdAsLong();
        log.info("获取用户加入的课程: userId={}", userId);
        List<Course> courses = courseRepository.findByStudentIdsContainingAndIsDeletedFalse(userId);
        log.info("查询结果: courses={}", courses);
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
        
        return new CourseVO(
            course.getId(),
            course.getName(),
            course.getDescription(),
            course.getClassCode(),
            course.getCode(),
            course.getSubject(),
            course.getCreatorId(),
            course.getCreatorRole(),
            course.getCreatedAt(),
            course.getUpdatedAt(),
            course.getStudentIds().size(),
            course.getKnowledgeBaseIds(),
            new ArrayList<>(),  // 暂时不需要知识库详情
            false              // 暂时不需要发布状态
        );
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
        Course existCourse = courseRepository.findByClassCodeAndIsDeletedFalse(code);
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
            
            List<KnowledgeBaseVO> knowledgeBases = new ArrayList<>();
            boolean hasPublishedBase = false;
            
            if (course.getKnowledgeBaseIds() != null && !course.getKnowledgeBaseIds().isEmpty()) {
                for (String knowledgeBaseId : course.getKnowledgeBaseIds()) {
                    KnowledgeBase knowledgeBase = knowledgeBaseRepository.findById(knowledgeBaseId).orElse(null);
                    if (knowledgeBase != null && !knowledgeBase.getIsDeleted()) {
                        KnowledgeBaseVO knowledgeBaseVO = knowledgeBaseService.convertToVO(knowledgeBase);
                        knowledgeBases.add(knowledgeBaseVO);
                        if (knowledgeBase.getIsPublished()) {
                            hasPublishedBase = true;
                        }
                    }
                }
            }
            
            CourseVO vo = new CourseVO(
                course.getId(),
                course.getName(),
                course.getDescription(),
                course.getClassCode(),
                course.getCode(),
                course.getSubject(),
                course.getCreatorId(),
                course.getCreatorRole(),
                course.getCreatedAt(),
                course.getUpdatedAt(),
                course.getStudentIds().size(),
                course.getKnowledgeBaseIds(),
                knowledgeBases,
                hasPublishedBase
            );
            
            result.add(vo);
        }
        return result;
    }
    /**
     * 更新课程信息
     * @param id 课程ID
     * @param updateCourseDTO 更新课程DTO
     * @return 是否更新成功
     */
    public boolean updateCourse(String id, UpdateCourseDTO updateCourseDTO) {
        Long userId = StpUtil.getLoginIdAsLong();
        String userRole = userRoleService.getCurrentUserRole();
        log.info("开始更新课程: courseId={}, userId={}, userRole={}, updateDTO={}", id, userId, userRole, updateCourseDTO);

        // 查找课程
        Course course = courseRepository.findById(id).orElse(null);
        if (course == null || course.getIsDeleted()) {
            log.warn("课程不存在或已删除: courseId={}", id);
            throw new ApiException(ApiError.COURSE_NOT_EXISTED);
        }

        // 验证权限
        if (!UserRole.ADMIN.equals(userRole) && !course.getCreatorId().equals(userId)) {
            log.warn("用户无权更新课程: userId={}, courseId={}, creatorId={}", userId, id, course.getCreatorId());
            throw new ApiException(ApiError.PERMISSION_DENIED);
        }

        // 检查课程名称
        if (!course.getName().equals(updateCourseDTO.getName())) {
            Course existCourse = courseRepository.findByNameAndIsDeletedFalse(updateCourseDTO.getName());
            if (existCourse != null && !existCourse.getId().equals(id)) {
                log.warn("课程名称已存在: name={}, existingCourseId={}", updateCourseDTO.getName(), existCourse.getId());
                throw new ApiException(ApiError.COURSE_HAD_EXISTED);
            }
        }

        // 更新课程信息
        course.setName(updateCourseDTO.getName());
        course.setDescription(updateCourseDTO.getDescription());
        course.setUpdatedAt(new Date());

        Course updatedCourse = courseRepository.save(course);
        log.info("课程更新成功: courseId={}, name={}", updatedCourse.getId(), updatedCourse.getName());
        
        return true;
    }

    /**
     * 删除课程
     * @param id 课程ID
     * @return 是否删除成功
     */
    public boolean deleteCourse(String id) {
        Long userId = StpUtil.getLoginIdAsLong();
        String userRole = userRoleService.getCurrentUserRole();
        log.info("开始删除课程: courseId={}, userId={}, userRole={}", id, userId, userRole);

        // 查找课程
        Course course = courseRepository.findById(id).orElse(null);
        if (course == null || course.getIsDeleted()) {
            log.warn("课程不存在或已删除: courseId={}", id);
            throw new ApiException(ApiError.COURSE_NOT_EXISTED);
        }

        // 验证权限
        if (!UserRole.ADMIN.equals(userRole) && !course.getCreatorId().equals(userId)) {
            log.warn("用户无权删除课程: userId={}, courseId={}, creatorId={}", userId, id, course.getCreatorId());
            throw new ApiException(ApiError.PERMISSION_DENIED);
        }

        // 逻辑删除课程
        course.setIsDeleted(true);
        course.setUpdatedAt(new Date());

        Course deletedCourse = courseRepository.save(course);
        log.info("课程删除成功: courseId={}, name={}", deletedCourse.getId(), deletedCourse.getName());
        
        return true;
    }

    /**
     * 获取可访问的课程列表（包括我创建的和加入的）
     * @return 课程列表
     */
    public List<CourseVO> getAccessibleCourses() {
        Long userId = StpUtil.getLoginIdAsLong();
        String userRole = userRoleService.getCurrentUserRole();
        log.info("获取用户可访问的课程: userId={}, userRole={}", userId, userRole);
        
        // 如果是管理员，返回所有课程
        if (UserRole.ADMIN.equals(userRole)) {
            List<Course> allCourses = courseRepository.findByIsDeletedFalse(Pageable.unpaged()).getContent();
            log.info("管理员查询所有课程结果: courses={}", allCourses);
            return convertToVOList(allCourses);
        }
        
        // 获取我创建的课程
        List<Course> createdCourses = courseRepository.findByCreatorIdAndIsDeletedFalse(userId);
        log.info("用户创建的课程: courses={}", createdCourses);
        
        // 获取我加入的课程
        List<Course> joinedCourses = courseRepository.findByStudentIdsContainingAndIsDeletedFalse(userId);
        log.info("用户加入的课程: courses={}", joinedCourses);
        
        // 合并两个列表并去重
        Set<Course> uniqueCourses = new HashSet<>();
        uniqueCourses.addAll(createdCourses);
        uniqueCourses.addAll(joinedCourses);
        
        return convertToVOList(new ArrayList<>(uniqueCourses));
    }

    /**
     * 获取系统课程列表（可访问的课程）
     */
    public PaginatedResponse<CourseVO> getSystemCourses(PaginationParams params, String subject) {
        Long userId = StpUtil.getLoginIdAsLong();
        String userRole = userRoleService.getCurrentUserRole();
        log.info("开始获取系统课程列表: userId={}, userRole={}, page={}, size={}, subject={}", 
            userId, userRole, params.getPage(), params.getSize(), subject);
        
        Pageable pageable = PageRequest.of(params.getPage() - 1, params.getSize());
        Page<Course> coursePage;

        // 根据用户角色获取不同的课程列表
        if (UserRole.ADMIN.equals(userRole)) {
            // 管理员可以看到所有未删除的课程
            if (subject != null && !subject.isEmpty()) {
                log.debug("管理员按科目查询课程: subject={}", subject);
                coursePage = courseRepository.findBySubjectAndIsDeletedFalse(subject, pageable);
            } else {
                log.debug("管理员查询所有课程");
                coursePage = courseRepository.findByIsDeletedFalse(pageable);
            }
        } else if (UserRole.TEACHER.equals(userRole)) {
            // 教师可以看到自己创建的课程和管理员创建的课程
            if (subject != null && !subject.isEmpty()) {
                log.debug("教师按科目查询课程: subject={}", subject);
                coursePage = courseRepository.findBySubjectAndIsDeletedFalseAndCreatorRoleIn(
                    subject, 
                    List.of(UserRole.ADMIN, userRole),
                    pageable
                );
            } else {
                log.debug("教师查询可见课程");
                coursePage = courseRepository.findByIsDeletedFalseAndCreatorRoleIn(
                    List.of(UserRole.ADMIN, userRole),
                    pageable
                );
            }
        } else {
            // 学生可以看到管理员创建的课程和自己已加入的课程
            if (subject != null && !subject.isEmpty()) {
                log.debug("学生按科目查询课程: subject={}", subject);
                coursePage = courseRepository.findBySubjectAndIsDeletedFalseAndCreatorRoleOrStudentIdsContaining(
                    subject,
                    UserRole.ADMIN,
                    userId,
                    pageable
                );
            } else {
                log.debug("学生查询可见课程");
                coursePage = courseRepository.findByIsDeletedFalseAndCreatorRoleOrStudentIdsContaining(
                    UserRole.ADMIN,
                    userId,
                    pageable
                );
            }
        }
        
        log.info("查询到课程总数: total={}", coursePage.getTotalElements());
        
        List<CourseVO> courseVOs = coursePage.getContent().stream()
            .map(course -> {
                List<KnowledgeBaseVO> knowledgeBases = new ArrayList<>();
                boolean hasPublishedBase = false;
                
                // 获取知识库信息
                if (course.getKnowledgeBaseIds() != null && !course.getKnowledgeBaseIds().isEmpty()) {
                    log.debug("获取课程知识库信息: courseId={}, knowledgeBaseCount={}", 
                        course.getId(), course.getKnowledgeBaseIds().size());
                    
                    for (String knowledgeBaseId : course.getKnowledgeBaseIds()) {
                        KnowledgeBase knowledgeBase = knowledgeBaseRepository.findById(knowledgeBaseId).orElse(null);
                        if (knowledgeBase != null && !knowledgeBase.getIsDeleted()) {
                            KnowledgeBaseVO knowledgeBaseVO = knowledgeBaseService.convertToVO(knowledgeBase);
                            knowledgeBases.add(knowledgeBaseVO);
                            if (knowledgeBase.getIsPublished()) {
                                hasPublishedBase = true;
                            }
                        }
                    }
                }
                
                return new CourseVO(
                    course.getId(),
                    course.getName(),
                    course.getDescription(),
                    course.getClassCode(),
                    course.getCode(),
                    course.getSubject(),
                    course.getCreatorId(),
                    course.getCreatorRole(),
                    course.getCreatedAt(),
                    course.getUpdatedAt(),
                    course.getStudentIds().size(),
                    course.getKnowledgeBaseIds(),
                    knowledgeBases,
                    hasPublishedBase
                );
            })
            .toList();
        
        log.info("成功转换课程列表: courseCount={}", courseVOs.size());
        
        return PaginatedResponse.of(
            courseVOs,
            (int) coursePage.getTotalElements(),
            params.getPage(),
            params.getSize()
        );
    }

    /**
     * 获取用户已加入的课程列表
     */
    public PaginatedResponse<CourseVO> getUserCourses(PaginationParams params) {
        Long userId = StpUtil.getLoginIdAsLong();
        Pageable pageable = PageRequest.of(params.getPage() - 1, params.getSize());
        Page<Course> coursePage = courseRepository.findByStudentIdsContainingAndIsDeletedFalse(userId, pageable);
        
        List<CourseVO> courseVOs = coursePage.getContent().stream()
            .map(course -> new CourseVO(
                course.getId(),
                course.getName(),
                course.getDescription(),
                course.getClassCode(),
                course.getCode(),
                course.getSubject(),
                course.getCreatorId(),
                course.getCreatorRole(),
                course.getCreatedAt(),
                course.getUpdatedAt(),
                course.getStudentIds().size(),
                course.getKnowledgeBaseIds(),
                new ArrayList<>(),  // 分页列表暂时不需要知识库详情
                false              // 分页列表暂时不需要发布状态
            ))
            .toList();
        
        return PaginatedResponse.of(
            courseVOs,
            (int) coursePage.getTotalElements(),
            params.getPage(),
            params.getSize()
        );
    }

    /**
     * 获取用户创建的课程列表
     */
    public PaginatedResponse<CourseVO> getCreatedCourses(PaginationParams params) {
        Long userId = StpUtil.getLoginIdAsLong();
        Pageable pageable = PageRequest.of(params.getPage() - 1, params.getSize());
        Page<Course> coursePage = courseRepository.findByCreatorIdAndIsDeletedFalse(userId, pageable);
        
        List<CourseVO> courseVOs = coursePage.getContent().stream()
            .map(course -> new CourseVO(
                course.getId(),
                course.getName(),
                course.getDescription(),
                course.getClassCode(),
                course.getCode(),
                course.getSubject(),
                course.getCreatorId(),
                course.getCreatorRole(),
                course.getCreatedAt(),
                course.getUpdatedAt(),
                course.getStudentIds().size(),
                course.getKnowledgeBaseIds(),
                new ArrayList<>(),  // 分页列表暂时不需要知识库详情
                false              // 分页列表暂时不需要发布状态
            ))
            .toList();
        
        return PaginatedResponse.of(
            courseVOs,
            (int) coursePage.getTotalElements(),
            params.getPage(),
            params.getSize()
        );
    }

    /**
     * 获取课程统计信息
     */
    public CourseStatsVO getCourseStats(String id) {
        Course course = courseRepository.findById(id)
            .orElseThrow(() -> new ApiException(ApiError.COURSE_NOT_EXISTED));
            
        // 获取学生数量
        int studentCount = course.getStudentIds() != null ? course.getStudentIds().size() : 0;
        
        // 获取知识库数量
        int knowledgeBaseCount = course.getKnowledgeBaseIds() != null ? course.getKnowledgeBaseIds().size() : 0;
        
        // 获取知识点总数
        int totalKnowledgePoints = 0;
        if (course.getKnowledgeBaseIds() != null) {
            for (String knowledgeBaseId : course.getKnowledgeBaseIds()) {
                KnowledgeBase knowledgeBase = knowledgeBaseRepository.findById(knowledgeBaseId).orElse(null);
                if (knowledgeBase != null && !knowledgeBase.getIsDeleted()) {
                    totalKnowledgePoints += knowledgeBase.getKnowledgeIds() != null ? 
                        knowledgeBase.getKnowledgeIds().size() : 0;
                }
            }
        }
        
        // 获取活跃学生数（最近7天有登录记录的学生）
        int activeStudents = 0;
        if (course.getStudentIds() != null) {
            long sevenDaysAgo = System.currentTimeMillis() - 7 * 24 * 60 * 60 * 1000L;
            for (Long studentId : course.getStudentIds()) {
                // TODO: 实现学生活跃度统计，可以通过用户登录记录或学习记录统计
                activeStudents++;
            }
        }
        
        return new CourseStatsVO(
            studentCount,          // 学生总数
            totalKnowledgePoints,  // 知识点总数
            activeStudents,        // 活跃学生数
            knowledgeBaseCount     // 知识库数量
        );
    }

    /**
     * 获取课程学习进度
     */
    public CourseProgressVO getCourseProgress(String id) {
        Long userId = StpUtil.getLoginIdAsLong();
        Course course = courseRepository.findById(id)
            .orElseThrow(() -> new ApiException(ApiError.COURSE_NOT_EXISTED));
            
        if (!course.getStudentIds().contains(userId)) {
            throw new ApiException(ApiError.NO_PERMISSION);
        }
        
        // 获取课程的所有知识点
        int totalKnowledgePoints = 0;
        int completedKnowledgePoints = 0;
        
        if (course.getKnowledgeBaseIds() != null) {
            for (String knowledgeBaseId : course.getKnowledgeBaseIds()) {
                KnowledgeBase knowledgeBase = knowledgeBaseRepository.findById(knowledgeBaseId).orElse(null);
                if (knowledgeBase != null && !knowledgeBase.getIsDeleted()) {
                    if (knowledgeBase.getKnowledgeIds() != null) {
                        totalKnowledgePoints += knowledgeBase.getKnowledgeIds().size();
                        
                        // TODO: 实现学习进度统计，可以通过用户学习记录统计已完成的知识点
                        for (String knowledgeId : knowledgeBase.getKnowledgeIds()) {
                            // 这里需要根据实际业务逻辑判断知识点是否已完成
                            completedKnowledgePoints++;
                        }
                    }
                }
            }
        }
        
        // 获取最后访问时间
        Date lastAccessTime = new Date(); // TODO: 从用户学习记录中获取最后访问时间
        
        // 获取总学习时长（分钟）
        long totalLearningTime = 0; // TODO: 从用户学习记录中获取总学习时长
        
        return new CourseProgressVO(
            completedKnowledgePoints,  // 已完成知识点数
            totalKnowledgePoints,      // 总知识点数
            lastAccessTime,            // 最后访问时间
            totalLearningTime          // 学习时长（分钟）
        );
    }
} 
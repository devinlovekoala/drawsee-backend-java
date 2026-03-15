package cn.yifan.drawsee.service.business;

import cn.dev33.satoken.stp.StpUtil;
import cn.yifan.drawsee.constant.UserRole;
import cn.yifan.drawsee.exception.ApiError;
import cn.yifan.drawsee.exception.ApiException;
import cn.yifan.drawsee.mapper.ClassMapper;
import cn.yifan.drawsee.mapper.ClassMemberMapper;
import cn.yifan.drawsee.mapper.CourseMapper;
import cn.yifan.drawsee.mapper.KnowledgeBaseMapper;
import cn.yifan.drawsee.pojo.dto.CreateCourseDTO;
import cn.yifan.drawsee.pojo.dto.CreateKnowledgeBaseDTO;
import cn.yifan.drawsee.pojo.dto.JoinCourseDTO;
import cn.yifan.drawsee.pojo.dto.PaginationParams;
import cn.yifan.drawsee.pojo.dto.UpdateCourseDTO;
import cn.yifan.drawsee.pojo.entity.Class;
import cn.yifan.drawsee.pojo.entity.ClassMember;
import cn.yifan.drawsee.pojo.entity.Course;
import cn.yifan.drawsee.pojo.entity.KnowledgeBase;
import cn.yifan.drawsee.pojo.vo.*;
import cn.yifan.drawsee.util.UUIDUtils;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * @FileName CourseService @Description 课程服务类 @Author yifan
 *
 * @date 2025-03-28 11:12
 */
@Slf4j
@Service
public class CourseService {

  @Autowired private CourseMapper courseMapper;

  @Autowired private UserRoleService userRoleService;

  @Autowired private TeacherInvitationCodeService teacherInvitationCodeService;

  @Autowired private KnowledgeBaseMapper knowledgeBaseMapper;

  @Autowired private KnowledgeBaseService knowledgeBaseService;

  @Autowired private ClassMapper classMapper;

  @Autowired private ClassMemberMapper classMemberMapper;

  /**
   * 创建课程
   *
   * @param createCourseDTO 创建课程DTO
   * @return 课程ID
   */
  @Transactional(rollbackFor = Exception.class)
  public String createCourse(CreateCourseDTO createCourseDTO) {
    Long userId = StpUtil.getLoginIdAsLong();
    String userRole = userRoleService.getCurrentUserRole();
    log.info("开始创建课程: userId={}, userRole={}, courseDTO={}", userId, userRole, createCourseDTO);

    // 验证用户是否为教师或管理员
    if (!UserRole.ADMIN.equals(userRole) && !UserRole.TEACHER.equals(userRole)) {
      log.warn("用户权限不足: userId={}, userRole={}", userId, userRole);
      throw new ApiException(ApiError.PERMISSION_DENIED, "文件不能为空");
    }

    // 检查课程名称是否已存在
    Course existCourse = courseMapper.getByName(createCourseDTO.getName());
    if (existCourse != null) {
      log.warn(
          "课程名称已存在: name={}, existCourseId={}", createCourseDTO.getName(), existCourse.getId());
      throw new ApiException(ApiError.COURSE_HAD_EXISTED, "文件不能为空");
    }

    // 生成班级码
    String classCode = generateClassCode();
    log.info("生成班级码: classCode={}", classCode);

    // 创建课程
    Course course = new Course();
    String courseId = UUIDUtils.generateUUID();
    course.setId(courseId);
    course.setName(createCourseDTO.getName());
    String courseCode = createCourseDTO.getCode();
    if (courseCode != null) {
      courseCode = courseCode.trim();
    }
    // 如果code为空，使用课程ID的前8位作为默认值
    course.setCode(
        (courseCode == null || courseCode.isBlank()) ? courseId.substring(0, 8) : courseCode);
    course.setClassCode(classCode);
    course.setDescription(createCourseDTO.getDescription());
    String subject = createCourseDTO.getSubject();
    if (subject != null) {
      subject = subject.trim();
    }
    course.setSubject((subject == null || subject.isBlank()) ? null : subject);
    course.setTopics(new ArrayList<>());
    course.setCreatorId(userId);
    course.setCreatorRole(userRole);
    course.setStudentIds(new ArrayList<>());
    course.setKnowledgeBaseIds(new ArrayList<>());
    course.setCreatedAt(new Date());
    course.setUpdatedAt(new Date());
    course.setIsDeleted(false);

    courseMapper.insert(course);
    log.info(
        "课程创建成功: courseId={}, name={}, classCode={}",
        course.getId(),
        course.getName(),
        course.getClassCode());

    // 同步班级表与班级成员（教师）
    ensureClassAndMember(course, userId);

    // 如果创建者是教师，为其生成班级邀请码
    if (UserRole.TEACHER.equals(userRole)) {
      teacherInvitationCodeService.generateCodeForCourse(course.getId(), userId);
      log.info("已为教师生成班级邀请码: courseId={}, teacherId={}", course.getId(), userId);
    }

    return course.getId();
  }

  /**
   * 加入课程
   *
   * @param joinCourseDTO 加入课程DTO
   * @return 课程ID
   */
  @Transactional(rollbackFor = Exception.class)
  public String joinCourse(JoinCourseDTO joinCourseDTO) {
    Long userId = StpUtil.getLoginIdAsLong();
    log.info("开始加入课程: userId={}, classCode={}", userId, joinCourseDTO.getClassCode());

    // 检查班级码是否存在
    Course course = courseMapper.getByClassCode(joinCourseDTO.getClassCode());
    if (course == null) {
      log.warn("班级码无效: classCode={}", joinCourseDTO.getClassCode());
      throw new ApiException(ApiError.INVALID_CLASS_CODE, "文件不能为空");
    }

    // 检查用户是否已加入该课程
    if (course.getStudentIds().contains(userId)) {
      log.warn("用户已加入该课程: userId={}, courseId={}", userId, course.getId());
      throw new ApiException(ApiError.ALREADY_JOINED, "文件不能为空");
    }

    // 加入课程
    courseMapper.addStudent(course.getId(), userId);
    log.info("用户成功加入课程: userId={}, courseId={}", userId, course.getId());

    // 同步班级成员关系
    ensureClassAndMember(course, userId);

    return course.getId();
  }

  /**
   * 获取我创建的课程列表
   *
   * @return 课程列表
   */
  public List<CourseVO> getMyCreatedCourses() {
    Long userId = StpUtil.getLoginIdAsLong();
    log.info("获取用户创建的课程: userId={}", userId);
    List<Course> courses = courseMapper.getByCreatorId(userId, false);
    log.info("查询结果: courses={}", courses);
    return convertToVOList(courses);
  }

  /**
   * 获取我加入的课程列表
   *
   * @return 课程列表
   */
  public List<CourseVO> getMyJoinedCourses() {
    Long userId = StpUtil.getLoginIdAsLong();
    log.info("获取用户加入的课程: userId={}", userId);
    List<Course> courses = courseMapper.getByStudentId(userId, false);
    log.info("查询结果: courses={}", courses);
    return convertToVOList(courses);
  }

  /**
   * 获取课程详情
   *
   * @param id 课程ID
   * @return 课程详情
   */
  public CourseVO getCourseDetail(String id) {
    Course course = courseMapper.getById(id);
    if (course == null || course.getIsDeleted()) {
      throw new ApiException(ApiError.COURSE_NOT_EXISTED, "文件不能为空");
    }

    return convertToVO(course);
  }

  /**
   * 生成班级码
   *
   * @return 班级码
   */
  private String generateClassCode() {
    String chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
    Random random = new SecureRandom();
    StringBuilder sb = new StringBuilder(6);
    for (int i = 0; i < 6; i++) {
      sb.append(chars.charAt(random.nextInt(chars.length())));
    }

    // 检查班级码是否已存在
    String classCode = sb.toString();
    Course existCourse = courseMapper.getByClassCode(classCode);
    if (existCourse != null) {
      // 如果已存在，重新生成
      return generateClassCode();
    }

    return classCode;
  }

  private void ensureClassAndMember(Course course, Long userId) {
    if (course == null || userId == null) {
      return;
    }
    String classCode = course.getClassCode();
    if (classCode == null || classCode.isBlank()) {
      return;
    }

    Class clazz = classMapper.getByClassCode(classCode);
    if (clazz == null) {
      clazz =
          new Class(course.getName(), course.getDescription(), classCode, course.getCreatorId());
      classMapper.insert(clazz);
    }

    if (clazz.getId() == null) {
      return;
    }

    ClassMember existing = classMemberMapper.getByClassIdAndUserId(clazz.getId(), userId);
    if (existing == null) {
      ClassMember member = new ClassMember(clazz.getId(), userId);
      classMemberMapper.insert(member);
    } else if (Boolean.TRUE.equals(existing.getIsDeleted())) {
      existing.setIsDeleted(false);
      classMemberMapper.update(existing);
    }
  }

  /**
   * 将课程列表转换为VO列表
   *
   * @param courses 课程列表
   * @return VO列表
   */
  private List<CourseVO> convertToVOList(List<Course> courses) {
    if (courses == null) {
      return new ArrayList<>();
    }

    return courses.stream().map(this::convertToVO).collect(Collectors.toList());
  }

  /**
   * 将课程对象转换为VO对象
   *
   * @param course 课程对象
   * @return VO对象
   */
  private CourseVO convertToVO(Course course) {
    if (course == null) {
      return null;
    }

    CourseVO vo = new CourseVO();
    BeanUtils.copyProperties(course, vo);

    // 设置学生数量
    if (course.getStudentIds() != null) {
      vo.setStudentCount(course.getStudentIds().size());
    } else {
      vo.setStudentCount(0);
    }

    // 获取知识库列表
    List<KnowledgeBaseVO> knowledgeBases = new ArrayList<>();
    if (course.getKnowledgeBaseIds() != null && !course.getKnowledgeBaseIds().isEmpty()) {
      for (String knowledgeBaseId : course.getKnowledgeBaseIds()) {
        KnowledgeBase knowledgeBase = knowledgeBaseMapper.getById(knowledgeBaseId);
        if (knowledgeBase != null && !knowledgeBase.getIsDeleted()) {
          knowledgeBases.add(knowledgeBaseService.convertToVO(knowledgeBase));
        }
      }
    }
    vo.setKnowledgeBases(knowledgeBases);

    return vo;
  }

  /**
   * 更新课程
   *
   * @param id 课程ID
   * @param updateCourseDTO 更新课程DTO
   * @return 是否成功
   */
  @Transactional(rollbackFor = Exception.class)
  public boolean updateCourse(String id, UpdateCourseDTO updateCourseDTO) {
    Course course = courseMapper.getById(id);
    if (course == null || course.getIsDeleted()) {
      log.warn("课程不存在: id={}", id);
      throw new ApiException(ApiError.COURSE_NOT_EXISTED, "文件不能为空");
    }

    // 检查是否有权限更新
    Long userId = StpUtil.getLoginIdAsLong();
    if (!course.getCreatorId().equals(userId)) {
      log.warn("用户无权更新课程: userId={}, courseId={}, creatorId={}", userId, id, course.getCreatorId());
      throw new ApiException(ApiError.PERMISSION_DENIED, "文件不能为空");
    }

    // 检查课程名称是否已存在
    if (!course.getName().equals(updateCourseDTO.getName())) {
      Course existCourse = courseMapper.getByName(updateCourseDTO.getName());
      if (existCourse != null && !existCourse.getId().equals(id)) {
        log.warn(
            "课程名称已存在: name={}, existCourseId={}", updateCourseDTO.getName(), existCourse.getId());
        throw new ApiException(ApiError.COURSE_HAD_EXISTED, "文件不能为空");
      }
    }

    // 更新课程信息
    course.setName(updateCourseDTO.getName());
    course.setDescription(updateCourseDTO.getDescription());
    String subject = updateCourseDTO.getSubject();
    if (subject != null) {
      subject = subject.trim();
    }
    course.setSubject((subject == null || subject.isBlank()) ? null : subject);
    String courseCode = updateCourseDTO.getCode();
    if (courseCode != null) {
      courseCode = courseCode.trim();
    }
    course.setCode((courseCode == null || courseCode.isBlank()) ? null : courseCode);
    course.setUpdatedAt(new Date());

    courseMapper.update(course);
    log.info("课程更新成功: courseId={}, name={}", course.getId(), course.getName());

    return true;
  }

  /**
   * 删除课程
   *
   * @param id 课程ID
   * @return 是否成功
   */
  @Transactional(rollbackFor = Exception.class)
  public boolean deleteCourse(String id) {
    Course course = courseMapper.getById(id);
    if (course == null || course.getIsDeleted()) {
      log.warn("课程不存在: id={}", id);
      throw new ApiException(ApiError.COURSE_NOT_EXISTED, "文件不能为空");
    }

    // 检查是否有权限删除
    Long userId = StpUtil.getLoginIdAsLong();
    String userRole = userRoleService.getCurrentUserRole();
    if (!course.getCreatorId().equals(userId) && !UserRole.ADMIN.equals(userRole)) {
      log.warn(
          "用户无权删除课程: userId={}, userRole={}, courseId={}, creatorId={}",
          userId,
          userRole,
          id,
          course.getCreatorId());
      throw new ApiException(ApiError.PERMISSION_DENIED, "文件不能为空");
    }

    // 逻辑删除
    course.setIsDeleted(true);
    course.setUpdatedAt(new Date());

    courseMapper.update(course);
    log.info("课程删除成功: courseId={}, name={}", course.getId(), course.getName());

    return true;
  }

  /**
   * 获取可访问的课程列表（包括我创建的和加入的）
   *
   * @return 课程列表
   */
  public List<CourseVO> getAccessibleCourses() {
    Long userId = StpUtil.getLoginIdAsLong();
    String userRole = userRoleService.getCurrentUserRole();
    log.info("获取用户可访问的课程: userId={}, userRole={}", userId, userRole);

    // 如果是管理员，返回所有课程
    if (UserRole.ADMIN.equals(userRole)) {
      List<Course> allCourses = courseMapper.getAll(false);
      log.info("管理员查询所有课程结果: courses={}", allCourses);
      return convertToVOList(allCourses);
    }

    // 获取我创建的课程
    List<Course> createdCourses = courseMapper.getByCreatorId(userId, false);
    log.info("用户创建的课程: courses={}", createdCourses);

    // 获取我加入的课程
    List<Course> joinedCourses = courseMapper.getByStudentId(userId, false);
    log.info("用户加入的课程: courses={}", joinedCourses);

    // 合并去重
    Set<String> courseIds = new HashSet<>();
    List<Course> allCourses = new ArrayList<>();

    for (Course course : createdCourses) {
      if (!courseIds.contains(course.getId())) {
        courseIds.add(course.getId());
        allCourses.add(course);
      }
    }

    for (Course course : joinedCourses) {
      if (!courseIds.contains(course.getId())) {
        courseIds.add(course.getId());
        allCourses.add(course);
      }
    }

    log.info("用户可访问的课程总数: total={}", allCourses.size());
    return convertToVOList(allCourses);
  }

  /**
   * 获取系统课程列表
   *
   * @param params 分页参数
   * @param subject 科目
   * @return 分页课程列表
   */
  public PaginatedResponse<CourseVO> getSystemCourses(PaginationParams params, String subject) {
    String userRole = userRoleService.getCurrentUserRole();
    Long userId = StpUtil.getLoginIdAsLong();
    log.info(
        "获取系统课程: userRole={}, userId={}, params={}, subject={}", userRole, userId, params, subject);

    int offset = (params.getPage() - 1) * params.getSize();
    int limit = params.getSize();
    List<Course> courseList;
    int total = 0;

    // 根据用户角色获取不同的课程列表
    if (UserRole.ADMIN.equals(userRole)) {
      // 管理员可以看到所有课程
      if (subject != null && !subject.isEmpty()) {
        log.debug("管理员按科目查询课程: subject={}", subject);
        courseList = courseMapper.getBySubject(subject, false);
        total = courseMapper.countBySubject(subject, false);
      } else {
        log.debug("管理员查询所有课程");
        courseList = courseMapper.getAll(false);
        total = courseMapper.count(false);
      }
    } else if (UserRole.TEACHER.equals(userRole)) {
      // 教师可以看到自己创建的课程和管理员创建的课程
      if (subject != null && !subject.isEmpty()) {
        log.debug("教师按科目查询课程: subject={}", subject);
        // 这里需要自定义查询，暂时简单处理
        List<Course> adminCourses =
            courseMapper.getBySubjectAndCreatorRole(subject, UserRole.ADMIN, false);
        List<Course> teacherCourses =
            courseMapper.getBySubjectAndCreatorRole(subject, UserRole.TEACHER, false);
        courseList = new ArrayList<>();
        courseList.addAll(adminCourses);
        courseList.addAll(teacherCourses);
        total = adminCourses.size() + teacherCourses.size();
      } else {
        log.debug("教师查询可见课程");
        // 这里需要自定义查询，暂时简单处理
        List<Course> adminCourses = courseMapper.getByCreatorRole(UserRole.ADMIN, false);
        List<Course> teacherCourses = courseMapper.getByCreatorRole(UserRole.TEACHER, false);
        courseList = new ArrayList<>();
        courseList.addAll(adminCourses);
        courseList.addAll(teacherCourses);
        total = adminCourses.size() + teacherCourses.size();
      }
    } else {
      // 学生可以看到管理员创建的课程和自己已加入的课程
      if (subject != null && !subject.isEmpty()) {
        log.debug("学生按科目查询课程: subject={}", subject);
        // 这里需要自定义查询，暂时简单处理
        List<Course> adminCourses =
            courseMapper.getBySubjectAndCreatorRole(subject, UserRole.ADMIN, false);
        List<Course> joinedCourses = courseMapper.getByStudentId(userId, false);
        // 过滤出符合科目的已加入课程
        joinedCourses =
            joinedCourses.stream()
                .filter(c -> subject.equals(c.getSubject()))
                .collect(Collectors.toList());

        courseList = new ArrayList<>();
        courseList.addAll(adminCourses);
        courseList.addAll(joinedCourses);
        total = adminCourses.size() + joinedCourses.size();
      } else {
        log.debug("学生查询可见课程");
        // 这里需要自定义查询，暂时简单处理
        List<Course> adminCourses = courseMapper.getByCreatorRole(UserRole.ADMIN, false);
        List<Course> joinedCourses = courseMapper.getByStudentId(userId, false);

        courseList = new ArrayList<>();
        courseList.addAll(adminCourses);
        courseList.addAll(joinedCourses);
        total = adminCourses.size() + joinedCourses.size();
      }
    }

    // 手动分页
    int fromIndex = Math.min(offset, courseList.size());
    int toIndex = Math.min(fromIndex + limit, courseList.size());
    List<Course> pagedCourses = courseList.subList(fromIndex, toIndex);

    log.info("查询到课程总数: total={}", total);

    List<CourseVO> courseVOs =
        pagedCourses.stream()
            .map(
                course -> {
                  List<KnowledgeBaseVO> knowledgeBases = new ArrayList<>();
                  boolean hasPublishedBase = false;

                  if (course.getKnowledgeBaseIds() != null
                      && !course.getKnowledgeBaseIds().isEmpty()) {

                    for (String knowledgeBaseId : course.getKnowledgeBaseIds()) {
                      KnowledgeBase knowledgeBase = knowledgeBaseMapper.getById(knowledgeBaseId);
                      if (knowledgeBase != null && !knowledgeBase.getIsDeleted()) {
                        // 转换为VO对象
                        KnowledgeBaseVO knowledgeBaseVO =
                            knowledgeBaseService.convertToVO(knowledgeBase);
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
                      course.getStudentIds() != null ? course.getStudentIds().size() : 0,
                      course.getKnowledgeBaseIds(),
                      knowledgeBases,
                      hasPublishedBase);
                })
            .collect(Collectors.toList());

    return PaginatedResponse.of(courseVOs, total, params.getPage(), params.getSize());
  }

  /**
   * 获取我加入的课程列表（分页）
   *
   * @param params 分页参数
   * @return 分页课程列表
   */
  public PaginatedResponse<CourseVO> getUserCourses(PaginationParams params) {
    Long userId = StpUtil.getLoginIdAsLong();
    List<Course> courseList = courseMapper.getByStudentId(userId, false);

    // 手动分页
    int offset = (params.getPage() - 1) * params.getSize();
    int limit = params.getSize();
    int total = courseList.size();

    int fromIndex = Math.min(offset, courseList.size());
    int toIndex = Math.min(fromIndex + limit, courseList.size());
    List<Course> pagedCourses = courseList.subList(fromIndex, toIndex);

    List<CourseVO> courseVOs =
        pagedCourses.stream()
            .map(
                course ->
                    new CourseVO(
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
                        course.getStudentIds() != null ? course.getStudentIds().size() : 0,
                        course.getKnowledgeBaseIds(),
                        new ArrayList<>(),
                        false))
            .collect(Collectors.toList());

    return PaginatedResponse.of(courseVOs, total, params.getPage(), params.getSize());
  }

  /**
   * 获取我创建的课程列表（分页）
   *
   * @param params 分页参数
   * @return 分页课程列表
   */
  public PaginatedResponse<CourseVO> getCreatedCourses(PaginationParams params) {
    Long userId = StpUtil.getLoginIdAsLong();
    List<Course> courseList = courseMapper.getByCreatorId(userId, false);

    // 手动分页
    int offset = (params.getPage() - 1) * params.getSize();
    int limit = params.getSize();
    int total = courseList.size();

    int fromIndex = Math.min(offset, courseList.size());
    int toIndex = Math.min(fromIndex + limit, courseList.size());
    List<Course> pagedCourses = courseList.subList(fromIndex, toIndex);

    List<CourseVO> courseVOs =
        pagedCourses.stream()
            .map(
                course ->
                    new CourseVO(
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
                        course.getStudentIds() != null ? course.getStudentIds().size() : 0,
                        course.getKnowledgeBaseIds(),
                        new ArrayList<>(),
                        false))
            .collect(Collectors.toList());

    return PaginatedResponse.of(courseVOs, total, params.getPage(), params.getSize());
  }

  /** 获取课程统计信息 */
  public CourseStatsVO getCourseStats(String id) {
    Course course = courseMapper.getById(id);
    if (course == null) {
      throw new ApiException(ApiError.COURSE_NOT_EXISTED, "文件不能为空");
    }

    // 获取学生数量
    int studentCount = course.getStudentIds() != null ? course.getStudentIds().size() : 0;

    // 获取知识库数量
    int knowledgeBaseCount =
        course.getKnowledgeBaseIds() != null ? course.getKnowledgeBaseIds().size() : 0;

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
        studentCount, // 学生总数
        0, // 知识点总数，已移除
        activeStudents, // 活跃学生数
        knowledgeBaseCount // 知识库数量
        );
  }

  /** 获取课程学习进度 */
  public CourseProgressVO getCourseProgress(String id) {
    Long userId = StpUtil.getLoginIdAsLong();
    Course course = courseMapper.getById(id);
    if (course == null || !course.getStudentIds().contains(userId)) {
      throw new ApiException(ApiError.NO_PERMISSION, "文件不能为空");
    }

    // 获取最后访问时间
    Date lastAccessTime = new Date(); // TODO: 从用户学习记录中获取最后访问时间

    // 获取总学习时长（分钟）
    long totalLearningTime = 0; // TODO: 从用户学习记录中获取总学习时长

    return new CourseProgressVO(
        0, // 已完成知识点数，已移除
        0, // 总知识点数，已移除
        lastAccessTime, // 最后访问时间
        totalLearningTime // 学习时长（分钟）
        );
  }

  /**
   * 为课程创建知识库
   *
   * @param courseId 课程ID
   * @param createKnowledgeBaseDTO 创建知识库DTO
   * @return 知识库ID
   */
  @Transactional(rollbackFor = Exception.class)
  public String createKnowledgeBaseForCourse(
      String courseId, CreateKnowledgeBaseDTO createKnowledgeBaseDTO) {
    // 先创建知识库
    String knowledgeBaseId = knowledgeBaseService.createKnowledgeBase(createKnowledgeBaseDTO);

    // 将知识库关联到课程
    Course course = courseMapper.getById(courseId);
    if (course == null || course.getIsDeleted()) {
      throw new ApiException(ApiError.COURSE_NOT_EXISTED, "文件不能为空");
    }

    // 检查当前用户是否有权限编辑课程
    Long userId = StpUtil.getLoginIdAsLong();
    if (!course.getCreatorId().equals(userId)) {
      throw new ApiException(ApiError.PERMISSION_DENIED, "文件不能为空");
    }

    // 将知识库ID添加到课程的知识库列表中
    if (course.getKnowledgeBaseIds() == null) {
      course.setKnowledgeBaseIds(new ArrayList<>());
    }
    course.getKnowledgeBaseIds().add(knowledgeBaseId);
    course.setUpdatedAt(new Date());

    // 更新课程
    courseMapper.update(course);

    return knowledgeBaseId;
  }
}

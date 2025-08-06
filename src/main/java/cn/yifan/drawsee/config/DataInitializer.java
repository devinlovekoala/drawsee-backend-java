package cn.yifan.drawsee.config;

import cn.yifan.drawsee.constant.UserRole;
import cn.yifan.drawsee.mapper.*;
import cn.yifan.drawsee.pojo.entity.*;
import cn.yifan.drawsee.util.PasswordUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;

/**
 * 数据初始化器，在应用程序启动时自动创建管理员和教师账户
 * 这解决了用SQL脚本直接插入加密密码导致的无法登录问题
 * 
 * @author yifan
 * @date 2025-04-29
 */
@Component
@Slf4j
public class DataInitializer implements CommandLineRunner {

    @Autowired
    private UserMapper userMapper;
    
    @Autowired
    private AdminMapper adminMapper;
    
    @Autowired
    private TeacherMapper teacherMapper;
    
    @Autowired
    private ClassMapper classMapper;
    
    @Autowired
    private ClassMemberMapper classMemberMapper;
    
    @Autowired
    private KnowledgeBaseMapper knowledgeBaseMapper;
    
    @Autowired
    private CourseMapper courseMapper;
    
    /**
     * 管理员用户初始化参数
     */
    private static final String ADMIN_USERNAME = "drawsee-admin";
    private static final String ADMIN_PASSWORD = "funstack20250328";
    
    /**
     * 教师用户初始化参数
     */
    private static final String TEACHER_USERNAME = "teacher";
    private static final String TEACHER_PASSWORD = "teacher123";
    private static final String TEACHER_TITLE = "副教授";
    private static final String TEACHER_ORGANIZATION = "北京邮电大学";
    
    /**
     * 学生用户初始化参数
     */
    private static final String STUDENT_USERNAME = "student";
    private static final String STUDENT_PASSWORD = "student123";

    @Override
    @Transactional
    public void run(String... args) {
        log.info("开始初始化系统默认用户数据...");
        
        // 初始化管理员账号
        User adminUser = initAdminUser();
        
        // 初始化教师账号
        User teacherUser = initTeacherUser();
        
        // 初始化学生账号
        User studentUser = initStudentUser();
        
        // 只有当教师和学生都创建成功时才初始化示例数据
        if (teacherUser != null && studentUser != null) {
            // 初始化测试班级和班级成员
            cn.yifan.drawsee.pojo.entity.Class testClass = initTestClass(teacherUser.getId());
            if (testClass != null) {
                initClassMember(testClass.getId(), studentUser.getId());
            }
            
            // 初始化示例知识库
            KnowledgeBase knowledgeBase = initKnowledgeBase(teacherUser.getId());
            
            // 初始化示例课程
            if (knowledgeBase != null) {
                // KnowledgeBase的id字段是String类型
                initCourse(teacherUser.getId(), studentUser.getId(), knowledgeBase.getId());
            }
        }
        
        log.info("系统默认用户数据初始化完成！");
    }
    
    /**
     * 初始化管理员账号
     */
    private User initAdminUser() {
        // 检查管理员用户是否已存在
        User adminUser = userMapper.getByUsername(ADMIN_USERNAME);
        
        if (adminUser == null) {
            log.info("创建管理员账号: {}", ADMIN_USERNAME);
            
            // 创建管理员用户，使用PasswordUtil正确加密密码
            adminUser = new User();
            adminUser.setUsername(ADMIN_USERNAME);
            adminUser.setPassword(PasswordUtil.encode(ADMIN_PASSWORD));
            Timestamp now = Timestamp.from(Instant.now());
            adminUser.setCreatedAt(now);
            adminUser.setUpdatedAt(now);
            adminUser.setIsDeleted(false);
            
            userMapper.insert(adminUser);
            
            // 添加到管理员表
            Admin admin = new Admin();
            admin.setUserId(adminUser.getId());
            adminMapper.insert(admin);
            
            log.info("管理员账号创建成功，ID: {}", adminUser.getId());
        } else {
            log.info("管理员账号已存在，ID: {}", adminUser.getId());
        }
        
        return adminUser;
    }
    
    /**
     * 初始化教师账号
     */
    private User initTeacherUser() {
        // 检查教师用户是否已存在
        User teacherUser = userMapper.getByUsername(TEACHER_USERNAME);
        
        if (teacherUser == null) {
            log.info("创建教师账号: {}", TEACHER_USERNAME);
            
            // 创建教师用户，使用PasswordUtil正确加密密码
            teacherUser = new User();
            teacherUser.setUsername(TEACHER_USERNAME);
            teacherUser.setPassword(PasswordUtil.encode(TEACHER_PASSWORD));
            Timestamp now = Timestamp.from(Instant.now());
            teacherUser.setCreatedAt(now);
            teacherUser.setUpdatedAt(now);
            teacherUser.setIsDeleted(false);
            
            userMapper.insert(teacherUser);
            
            // 添加到教师表
            Teacher teacher = new Teacher();
            teacher.setUserId(teacherUser.getId());
            teacher.setTitle(TEACHER_TITLE);
            teacher.setOrganization(TEACHER_ORGANIZATION);
            
            teacherMapper.insert(teacher);
            
            log.info("教师账号创建成功，ID: {}", teacherUser.getId());
        } else {
            log.info("教师账号已存在，ID: {}", teacherUser.getId());
        }
        
        return teacherUser;
    }
    
    /**
     * 初始化学生账号
     */
    private User initStudentUser() {
        // 检查学生用户是否已存在
        User studentUser = userMapper.getByUsername(STUDENT_USERNAME);
        
        if (studentUser == null) {
            log.info("创建学生账号: {}", STUDENT_USERNAME);
            
            // 创建学生用户，使用PasswordUtil正确加密密码
            studentUser = new User();
            studentUser.setUsername(STUDENT_USERNAME);
            studentUser.setPassword(PasswordUtil.encode(STUDENT_PASSWORD));
            Timestamp now = Timestamp.from(Instant.now());
            studentUser.setCreatedAt(now);
            studentUser.setUpdatedAt(now);
            studentUser.setIsDeleted(false);
            
            userMapper.insert(studentUser);
            
            log.info("学生账号创建成功，ID: {}", studentUser.getId());
        } else {
            log.info("学生账号已存在，ID: {}", studentUser.getId());
        }
        
        return studentUser;
    }
    
    /**
     * 初始化测试班级
     */
    private cn.yifan.drawsee.pojo.entity.Class initTestClass(Long teacherId) {
        // 检查是否已存在相同班级码的班级
        String classCode = "123456";
        cn.yifan.drawsee.pojo.entity.Class existingClass = classMapper.getByClassCode(classCode);
        
        if (existingClass == null) {
            log.info("创建测试班级");
            
            cn.yifan.drawsee.pojo.entity.Class testClass = new cn.yifan.drawsee.pojo.entity.Class();
            testClass.setName("测试班级");
            testClass.setDescription("用于系统测试的示例班级");
            testClass.setClassCode(classCode);
            testClass.setTeacherId(teacherId);
            Timestamp now = Timestamp.from(Instant.now());
            testClass.setCreatedAt(now);
            testClass.setUpdatedAt(now);
            testClass.setIsDeleted(false);
            
            classMapper.insert(testClass);
            
            log.info("测试班级创建成功，ID: {}", testClass.getId());
            return testClass;
        } else {
            log.info("测试班级已存在，ID: {}", existingClass.getId());
            return existingClass;
        }
    }
    
    /**
     * 初始化班级成员
     */
    private void initClassMember(Long classId, Long studentId) {
        // 检查是否已存在该班级成员
        ClassMember existingMember = classMemberMapper.getByClassIdAndUserId(classId, studentId);
        
        if (existingMember == null) {
            log.info("将学生 {} 添加到班级 {}", studentId, classId);
            
            ClassMember classMember = new ClassMember();
            classMember.setClassId(classId);
            classMember.setUserId(studentId);
            classMember.setJoinedAt(Timestamp.from(Instant.now()));
            classMember.setIsDeleted(false);
            
            classMemberMapper.insert(classMember);
            
            log.info("班级成员创建成功，ID: {}", classMember.getId());
        } else {
            log.info("班级成员已存在，ID: {}", existingMember.getId());
        }
    }
    
    /**
     * 初始化示例知识库
     */
    private KnowledgeBase initKnowledgeBase(Long creatorId) {
        // 检查是否已存在同名知识库
        String knowledgeBaseName = "物理基础知识库";
        KnowledgeBase existingKnowledgeBase = knowledgeBaseMapper.getByName(knowledgeBaseName);
        
        if (existingKnowledgeBase == null) {
            log.info("创建示例知识库");
            
            KnowledgeBase knowledgeBase = new KnowledgeBase();
            knowledgeBase.setId("kb-test-001"); // 设置ID为字符串类型
            knowledgeBase.setName(knowledgeBaseName);
            knowledgeBase.setDescription("包含高中物理基础知识");
            knowledgeBase.setSubject("物理");
            knowledgeBase.setInvitationCode("ABCDEF");
            knowledgeBase.setCreatorId(creatorId);
            
            // 设置members字段，初始包含创建者ID
            List<Long> members = new ArrayList<>();
            members.add(creatorId);
            knowledgeBase.setMembers(members);
            
            knowledgeBase.setCreatedAt(new Date());
            knowledgeBase.setUpdatedAt(new Date());
            knowledgeBase.setIsDeleted(false);
            knowledgeBase.setIsPublished(true);
            knowledgeBase.setRagEnabled(false);
            knowledgeBase.setRagDocumentCount(0);
            knowledgeBase.setSyncToRagFlow(false);
            
            knowledgeBaseMapper.insert(knowledgeBase);
            
            log.info("示例知识库创建成功，ID: {}", knowledgeBase.getId());
            return knowledgeBase;
        } else {
            log.info("示例知识库已存在，ID: {}", existingKnowledgeBase.getId());
            return existingKnowledgeBase;
        }
    }
    
    /**
     * 初始化示例课程
     */
    private void initCourse(Long creatorId, Long studentId, String knowledgeBaseId) {
        // 检查是否已存在同名课程
        String courseName = "高中物理基础";
        Course existingCourse = courseMapper.getByName(courseName);
        
        if (existingCourse == null) {
            log.info("创建示例课程");
            
            Course course = new Course();
            course.setId("course-test-001"); // 设置ID为字符串类型
            course.setName(courseName);
            course.setCode("PHY101");
            course.setClassCode("XYZ123");
            course.setDescription("高中物理基础课程");
            course.setSubject("物理");
            course.setCreatorId(creatorId);
            course.setCreatorRole("TEACHER");
            
            // 设置学生ID列表和知识库ID列表
            List<Long> studentIds = new ArrayList<>();
            studentIds.add(studentId);
            course.setStudentIds(studentIds);
            
            List<String> knowledgeBaseIds = new ArrayList<>();
            knowledgeBaseIds.add(knowledgeBaseId);
            course.setKnowledgeBaseIds(knowledgeBaseIds);
            
            course.setCreatedAt(new Date());
            course.setUpdatedAt(new Date());
            course.setIsDeleted(false);
            
            courseMapper.insert(course);
            
            log.info("示例课程创建成功，ID: {}", course.getId());
        } else {
            log.info("示例课程已存在，ID: {}", existingCourse.getId());
        }
    }
} 
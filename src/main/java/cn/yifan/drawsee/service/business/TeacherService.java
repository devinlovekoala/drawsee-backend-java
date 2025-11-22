package cn.yifan.drawsee.service.business;

import cn.dev33.satoken.stp.StpUtil;
import cn.yifan.drawsee.exception.ApiError;
import cn.yifan.drawsee.exception.ApiException;
import cn.yifan.drawsee.mapper.TeacherMapper;
import cn.yifan.drawsee.mapper.UserMapper;
import cn.yifan.drawsee.pojo.dto.CreateTeacherDTO;
import cn.yifan.drawsee.pojo.entity.Teacher;
import cn.yifan.drawsee.pojo.entity.User;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * @FileName TeacherService
 * @Description 教师服务类
 * @Author yifan
 * @date 2025-03-28 11:02
 **/

@Service
public class TeacherService {

    @Autowired
    private TeacherMapper teacherMapper;
    
    @Autowired
    private UserMapper userMapper;

    /**
     * 创建教师
     * @param createTeacherDTO 创建教师DTO
     */
    @Transactional
    public void createTeacher(CreateTeacherDTO createTeacherDTO) {
        Long userId = createTeacherDTO.getUserId();
        User user = userMapper.getById(userId);
        if (user == null) {
            throw new ApiException(ApiError.USER_NOT_EXIST, "文件不能为空");
        }
        
        Teacher teacher = teacherMapper.getByUserId(userId);
        if (teacher != null) {
            throw new ApiException(ApiError.TEACHER_HAD_EXISTED, "文件不能为空");
        }
        
        teacher = new Teacher();
        teacher.setUserId(userId);
        teacher.setTitle(createTeacherDTO.getTitle());
        teacher.setOrganization(createTeacherDTO.getOrganization());
        teacherMapper.insert(teacher);
    }
    
    /**
     * 校验用户是否为教师
     * @param userId 用户ID
     * @return 是否为教师
     */
    public boolean isTeacher(Long userId) {
        Teacher teacher = teacherMapper.getByUserId(userId);
        return teacher != null;
    }
    
    /**
     * 验证用户是否为教师
     * @throws ApiException 不是教师时抛出异常
     */
    public void validateTeacher() {
        Long userId = StpUtil.getLoginIdAsLong();
        if (!isTeacher(userId)) {
            throw new ApiException(ApiError.NOT_TEACHER, "文件不能为空");
        }
    }
    
    /**
     * 获取教师信息
     * @param userId 用户ID
     * @return 教师信息
     */
    public Teacher getTeacherByUserId(Long userId) {
        return teacherMapper.getByUserId(userId);
    }
    
    /**
     * 更新教师信息
     * @param teacher 教师信息
     */
    public void updateTeacher(Teacher teacher) {
        teacherMapper.update(teacher);
    }
} 
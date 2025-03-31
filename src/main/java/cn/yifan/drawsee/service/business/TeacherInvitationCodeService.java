package cn.yifan.drawsee.service.business;

import cn.dev33.satoken.stp.StpUtil;
import cn.yifan.drawsee.constant.UserRole;
import cn.yifan.drawsee.exception.ApiError;
import cn.yifan.drawsee.exception.ApiException;
import cn.yifan.drawsee.mapper.TeacherInvitationCodeMapper;
import cn.yifan.drawsee.mapper.TeacherMapper;
import cn.yifan.drawsee.mapper.UserMapper;
import cn.yifan.drawsee.pojo.dto.CreateTeacherInvitationCodeDTO;
import cn.yifan.drawsee.pojo.entity.Teacher;
import cn.yifan.drawsee.pojo.entity.TeacherInvitationCode;
import cn.yifan.drawsee.pojo.entity.User;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.sql.Timestamp;
import java.util.List;
import java.util.Random;

/**
 * @FileName TeacherInvitationCodeService
 * @Description 教师邀请码服务类
 * @Author devin
 * @date 2025-06-11 14:40
 **/

@Service
@Slf4j
public class TeacherInvitationCodeService {

    @Autowired
    private TeacherInvitationCodeMapper teacherInvitationCodeMapper;
    
    @Autowired
    private TeacherMapper teacherMapper;
    
    @Autowired
    private UserMapper userMapper;

    /**
     * 分页获取教师邀请码
     *
     * @param page 页码
     * @param size 每页大小
     * @return 教师邀请码列表
     */
    public List<TeacherInvitationCode> getTeacherInvitationCodesByPage(int page, int size) {
        int offset = (page - 1) * size;
        return teacherInvitationCodeMapper.getByPage(offset, size);
    }

    /**
     * 生成教师邀请码
     *
     * @return 教师邀请码
     */
    public String generateTeacherCode() {
        String characters = "ACDEFGHJKLMNPQRSTUVWXYZ234679"; // 去除了容易混淆的字符
        Random random = new SecureRandom();
        StringBuilder sb = new StringBuilder(8);

        for (int i = 0; i < 8; i++) {
            sb.append(characters.charAt(random.nextInt(characters.length())));
        }

        String code = sb.toString();
        // 检查是否已存在（极小概率冲突时可重试）
        if (teacherInvitationCodeMapper.getByCode(code) != null) {
            return generateTeacherCode();
        }
        return code;
    }

    /**
     * 创建教师邀请码
     *
     * @param createTeacherInvitationCodeDTO 创建教师邀请码DTO
     */
    public void createTeacherInvitationCode(CreateTeacherInvitationCodeDTO createTeacherInvitationCodeDTO) {
        Integer count = createTeacherInvitationCodeDTO.getCount();
        Long creatorId = StpUtil.getLoginIdAsLong();
        String remark = createTeacherInvitationCodeDTO.getRemark();
        
        // 创建count个邀请码
        for (int i = 0; i < count; i++) {
            String code = generateTeacherCode();
            TeacherInvitationCode teacherInvitationCode = new TeacherInvitationCode(code, creatorId);
            teacherInvitationCode.setRemark(remark);
            teacherInvitationCodeMapper.insert(teacherInvitationCode);
        }
    }

    /**
     * 使用教师邀请码
     *
     * @param code 邀请码
     * @return 是否使用成功
     */
    public boolean useTeacherInvitationCode(String code) {
        // 检查邀请码是否存在
        TeacherInvitationCode teacherInvitationCode = teacherInvitationCodeMapper.getByCode(code);
        if (teacherInvitationCode == null) {
            throw new ApiException(ApiError.INVITATION_CODE_NOT_EXISTED);
        }
        
        // 检查邀请码是否有效
        if (!teacherInvitationCode.getIsActive()) {
            throw new ApiException(ApiError.INVITATION_CODE_ALREADY_USED);
        }
        
        Long userId = StpUtil.getLoginIdAsLong();
        User user = userMapper.getById(userId);
        if (user == null) {
            throw new ApiException(ApiError.USER_NOT_EXIST);
        }
        
        // 检查用户是否已经是教师
        Teacher existTeacher = teacherMapper.getByUserId(userId);
        if (existTeacher != null) {
            throw new ApiException(ApiError.TEACHER_HAD_EXISTED);
        }
        
        // 将用户注册为教师
        Teacher teacher = new Teacher(userId);
        teacherMapper.insert(teacher);
        
        // 标记邀请码已使用
        teacherInvitationCode.setUsedBy(userId);
        teacherInvitationCode.setUsedAt(new Timestamp(System.currentTimeMillis()));
        teacherInvitationCode.setIsActive(false);
        teacherInvitationCodeMapper.update(teacherInvitationCode);
        
        return true;
    }
} 
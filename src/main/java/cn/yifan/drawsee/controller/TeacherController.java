package cn.yifan.drawsee.controller;

import cn.dev33.satoken.annotation.SaCheckLogin;
import cn.dev33.satoken.annotation.SaCheckRole;
import cn.yifan.drawsee.constant.UserRole;
import cn.yifan.drawsee.pojo.dto.CreateTeacherDTO;
import cn.yifan.drawsee.pojo.entity.Teacher;
import cn.yifan.drawsee.service.business.TeacherService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

/**
 * @FileName TeacherController
 * @Description 教师控制器类
 * @Author devin
 * @date 2025-03-28 11:15
 **/

@RestController
@RequestMapping("/teacher")
@SaCheckLogin
public class TeacherController {

    @Autowired
    private TeacherService teacherService;

    /**
     * 创建教师
     * @param createTeacherDTO 创建教师DTO
     */
    @PostMapping
    @SaCheckRole(UserRole.ADMIN)
    public void createTeacher(@RequestBody @Valid CreateTeacherDTO createTeacherDTO) {
        teacherService.createTeacher(createTeacherDTO);
    }

    /**
     * 验证用户是否为教师
     */
    @GetMapping("/validate")
    public void validateTeacher() {
        teacherService.validateTeacher();
    }

    /**
     * 获取教师信息
     * @param userId 用户ID
     * @return 教师信息
     */
    @GetMapping("/{userId}")
    @SaCheckRole(UserRole.ADMIN)
    public Teacher getTeacherByUserId(@PathVariable("userId") Long userId) {
        return teacherService.getTeacherByUserId(userId);
    }

    /**
     * 更新教师信息
     * @param teacher 教师信息
     */
    @PutMapping
    @SaCheckRole(value = {UserRole.ADMIN, UserRole.TEACHER})
    public void updateTeacher(@RequestBody Teacher teacher) {
        teacherService.updateTeacher(teacher);
    }
}
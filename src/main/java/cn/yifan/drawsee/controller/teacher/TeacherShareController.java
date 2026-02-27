package cn.yifan.drawsee.controller.teacher;

import cn.dev33.satoken.annotation.SaCheckLogin;
import cn.dev33.satoken.annotation.SaCheckRole;
import cn.dev33.satoken.annotation.SaMode;
import cn.yifan.drawsee.constant.UserRole;
import cn.yifan.drawsee.pojo.vo.ConversationSharePostVO;
import cn.yifan.drawsee.pojo.vo.ClassStudentVO;
import cn.yifan.drawsee.service.business.ConversationShareService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * @FileName TeacherShareController
 * @Description 教师会话分享管理控制器
 * @Author devin
 * @date 2026-02-25
 */

@RestController
@RequestMapping("/teacher/classes")
@SaCheckLogin
public class TeacherShareController {

    @Autowired
    private ConversationShareService conversationShareService;

    @GetMapping("/{classId}/shares")
    @SaCheckRole(value = {UserRole.TEACHER, UserRole.ADMIN}, mode = SaMode.OR)
    public List<ConversationSharePostVO> getClassShares(@PathVariable String classId) {
        return conversationShareService.listClassSharesByCourseOrClass(classId);
    }

    @GetMapping("/{classId}/students")
    @SaCheckRole(value = {UserRole.TEACHER, UserRole.ADMIN}, mode = SaMode.OR)
    public List<ClassStudentVO> getClassStudents(@PathVariable String classId) {
        return conversationShareService.listClassStudentsByCourseOrClass(classId);
    }
}

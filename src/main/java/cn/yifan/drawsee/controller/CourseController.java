package cn.yifan.drawsee.controller;

import cn.dev33.satoken.annotation.SaCheckLogin;
import cn.yifan.drawsee.pojo.dto.CreateCourseDTO;
import cn.yifan.drawsee.pojo.dto.JoinCourseDTO;
import cn.yifan.drawsee.pojo.vo.CourseVO;
import cn.yifan.drawsee.service.business.CourseService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * @FileName CourseController
 * @Description 课程控制器类
 * @Author devin
 * @date 2025-03-28 11:12
 **/

@RestController
@RequestMapping("/course")
@SaCheckLogin
public class CourseController {

    @Autowired
    private CourseService courseService;

    /**
     * 创建课程
     * @param createCourseDTO 创建课程DTO
     * @return 课程ID
     */
    @PostMapping
    public String createCourse(@RequestBody @Valid CreateCourseDTO createCourseDTO) {
        return courseService.createCourse(createCourseDTO);
    }

    /**
     * 加入课程
     * @param joinCourseDTO 加入课程DTO
     * @return 课程ID
     */
    @PostMapping("/join")
    public String joinCourse(@RequestBody @Valid JoinCourseDTO joinCourseDTO) {
        return courseService.joinCourse(joinCourseDTO);
    }

    /**
     * 获取我创建的课程列表
     * @return 课程列表
     */
    @GetMapping("/created")
    public List<CourseVO> getMyCreatedCourses() {
        return courseService.getMyCreatedCourses();
    }

    /**
     * 获取我加入的课程列表
     * @return 课程列表
     */
    @GetMapping("/joined")
    public List<CourseVO> getMyJoinedCourses() {
        return courseService.getMyJoinedCourses();
    }
    
    /**
     * 获取课程详情
     * @param id 课程ID
     * @return 课程详情
     */
    @GetMapping("/{id}")
    public CourseVO getCourseDetail(@PathVariable("id") String id) {
        return courseService.getCourseDetail(id);
    }
} 
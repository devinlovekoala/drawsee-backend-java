package cn.yifan.drawsee.controller;

import cn.dev33.satoken.annotation.SaCheckLogin;
import cn.yifan.drawsee.pojo.Result;
import cn.yifan.drawsee.pojo.dto.CreateCourseDTO;
import cn.yifan.drawsee.pojo.dto.CreateKnowledgeBaseDTO;
import cn.yifan.drawsee.pojo.dto.JoinCourseDTO;
import cn.yifan.drawsee.pojo.dto.UpdateCourseDTO;
import cn.yifan.drawsee.pojo.vo.CourseVO;
import cn.yifan.drawsee.service.business.CourseService;
import cn.yifan.drawsee.service.business.KnowledgeBaseService;
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
    
    @Autowired
    private KnowledgeBaseService knowledgeBaseService;

    /**
     * 创建课程
     * @param createCourseDTO 创建课程DTO
     * @return 课程ID包装在Result对象中
     */
    @PostMapping
    public Result<String> createCourse(@RequestBody @Valid CreateCourseDTO createCourseDTO) {
        String courseId = courseService.createCourse(createCourseDTO);
        return Result.success(courseId);
    }

    /**
     * 加入课程
     * @param joinCourseDTO 加入课程DTO
     * @return 课程ID包装在Result对象中
     */
    @PostMapping("/join")
    public Result<String> joinCourse(@RequestBody @Valid JoinCourseDTO joinCourseDTO) {
        String courseId = courseService.joinCourse(joinCourseDTO);
        return Result.success(courseId);
    }

    /**
     * 获取我创建的课程列表
     * @return 课程列表包装在Result对象中
     */
    @GetMapping("/created")
    public Result<List<CourseVO>> getMyCreatedCourses() {
        List<CourseVO> courses = courseService.getMyCreatedCourses();
        return Result.success(courses);
    }

    /**
     * 获取我加入的课程列表
     * @return 课程列表包装在Result对象中
     */
    @GetMapping("/joined")
    public Result<List<CourseVO>> getMyJoinedCourses() {
        List<CourseVO> courses = courseService.getMyJoinedCourses();
        return Result.success(courses);
    }
    
    /**
     * 获取课程详情
     * @param id 课程ID
     * @return 课程详情包装在Result对象中
     */
    @GetMapping("/{id}")
    public Result<CourseVO> getCourseDetail(@PathVariable("id") String id) {
        CourseVO course = courseService.getCourseDetail(id);
        return Result.success(course);
    }

    /**
     * 为课程创建知识库
     * @param id 课程ID
     * @param createKnowledgeBaseDTO 创建知识库DTO
     * @return 知识库ID包装在Result对象中
     */
    @PostMapping("/{id}/knowledge-base")
    public Result<String> createKnowledgeBaseForCourse(
            @PathVariable("id") String id,
            @RequestBody @Valid CreateKnowledgeBaseDTO createKnowledgeBaseDTO) {
        String knowledgeBaseId = knowledgeBaseService.createKnowledgeBaseForCourse(id, createKnowledgeBaseDTO);
        return Result.success(knowledgeBaseId);
    }
    /**
     * 更新课程
     * @param id 课程ID
     * @param updateCourseDTO 更新课程DTO
     * @return 更新结果包装在Result对象中
     */
    @PutMapping("/{id}")
    public Result<Boolean> updateCourse(
            @PathVariable("id") String id,
            @RequestBody @Valid UpdateCourseDTO updateCourseDTO) {
        boolean result = courseService.updateCourse(id, updateCourseDTO);
        return Result.success(result);
    }

    /**
     * 删除课程
     * @param id 课程ID
     * @return 删除结果包装在Result对象中
     */
    @DeleteMapping("/{id}")
    public Result<Boolean> deleteCourse(@PathVariable("id") String id) {
        boolean result = courseService.deleteCourse(id);
        return Result.success(result);
    }
} 
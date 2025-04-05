package cn.yifan.drawsee.controller;

import cn.dev33.satoken.annotation.SaCheckLogin;
import cn.yifan.drawsee.pojo.Result;
import cn.yifan.drawsee.pojo.dto.*;
import cn.yifan.drawsee.pojo.vo.*;
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
@RequestMapping("/courses")
@SaCheckLogin
public class CourseController {

    @Autowired
    private CourseService courseService;
    
    @Autowired
    private KnowledgeBaseService knowledgeBaseService;

    /**
     * 获取系统课程列表（可访问的课程）
     */
    @GetMapping("/system")
    public Result<PaginatedResponse<CourseVO>> getSystemCourses(
            @Valid PaginationParams params,
            @RequestParam(required = false) String subject) {
        return Result.success(courseService.getSystemCourses(params, subject));
    }

    /**
     * 获取用户已加入的课程列表
     */
    @GetMapping("/user")
    public Result<PaginatedResponse<CourseVO>> getUserCourses(
            @Valid PaginationParams params) {
        return Result.success(courseService.getUserCourses(params));
    }

    /**
     * 获取用户创建的课程列表
     */
    @GetMapping("/created")
    public Result<PaginatedResponse<CourseVO>> getCreatedCourses(
            @Valid PaginationParams params) {
        return Result.success(courseService.getCreatedCourses(params));
    }

    /**
     * 创建课程
     */
    @PostMapping
    public Result<String> createCourse(@RequestBody @Valid CreateCourseDTO createCourseDTO) {
        String courseId = courseService.createCourse(createCourseDTO);
        return Result.success(courseId);
    }

    /**
     * 加入课程
     */
    @PostMapping("/join")
    public Result<String> joinCourse(@RequestBody @Valid JoinCourseDTO joinCourseDTO) {
        String courseId = courseService.joinCourse(joinCourseDTO);
        return Result.success(courseId);
    }
    
    /**
     * 获取课程详情
     */
    @GetMapping("/{id}")
    public Result<CourseVO> getCourseDetail(@PathVariable("id") String id) {
        CourseVO course = courseService.getCourseDetail(id);
        return Result.success(course);
    }

    /**
     * 更新课程
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
     */
    @DeleteMapping("/{id}")
    public Result<Boolean> deleteCourse(@PathVariable("id") String id) {
        boolean result = courseService.deleteCourse(id);
        return Result.success(result);
    }

    /**
     * 获取课程统计信息
     */
    @GetMapping("/{id}/stats")
    public Result<CourseStatsVO> getCourseStats(@PathVariable("id") String id) {
        CourseStatsVO stats = courseService.getCourseStats(id);
        return Result.success(stats);
    }

    /**
     * 获取课程学习进度
     */
    @GetMapping("/{id}/progress")
    public Result<CourseProgressVO> getCourseProgress(@PathVariable("id") String id) {
        CourseProgressVO progress = courseService.getCourseProgress(id);
        return Result.success(progress);
    }

    /**
     * 为课程创建知识库
     */
    @PostMapping("/{id}/knowledge-base")
    public Result<String> createKnowledgeBaseForCourse(
            @PathVariable("id") String id,
            @RequestBody @Valid CreateKnowledgeBaseDTO createKnowledgeBaseDTO) {
        String knowledgeBaseId = knowledgeBaseService.createKnowledgeBaseForCourse(id, createKnowledgeBaseDTO);
        return Result.success(knowledgeBaseId);
    }
} 
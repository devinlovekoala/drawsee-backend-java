package cn.yifan.drawsee.controller;

import cn.dev33.satoken.annotation.SaCheckLogin;
import cn.yifan.drawsee.pojo.Result;
import cn.yifan.drawsee.pojo.dto.*;
import cn.yifan.drawsee.pojo.vo.*;
import cn.yifan.drawsee.service.business.CourseResourceService;
import cn.yifan.drawsee.service.business.CourseService;
import cn.yifan.drawsee.service.business.KnowledgeBaseService;
import jakarta.validation.Valid;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.TimeZone;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

/**
 * @FileName CourseController @Description 课程控制器类 @Author yifan
 *
 * @date 2025-03-28 11:12
 */
@RestController
@RequestMapping("/courses")
@SaCheckLogin
public class CourseController {

  @Autowired private CourseService courseService;

  @Autowired private KnowledgeBaseService knowledgeBaseService;

  @Autowired private CourseResourceService courseResourceService;

  /** 获取系统课程列表（可访问的课程） */
  @GetMapping("/system")
  public Result<PaginatedResponse<CourseVO>> getSystemCourses(
      @Valid PaginationParams params, @RequestParam(required = false) String subject) {
    return Result.success(courseService.getSystemCourses(params, subject));
  }

  /** 获取用户已加入的课程列表 */
  @GetMapping("/user")
  public Result<PaginatedResponse<CourseVO>> getUserCourses(@Valid PaginationParams params) {
    return Result.success(courseService.getUserCourses(params));
  }

  /** 获取用户创建的课程列表 */
  @GetMapping("/created")
  public Result<PaginatedResponse<CourseVO>> getCreatedCourses(@Valid PaginationParams params) {
    return Result.success(courseService.getCreatedCourses(params));
  }

  /** 创建课程 */
  @PostMapping
  public Result<String> createCourse(@RequestBody @Valid CreateCourseDTO createCourseDTO) {
    String courseId = courseService.createCourse(createCourseDTO);
    return Result.success(courseId);
  }

  /** 加入课程 */
  @PostMapping("/join")
  public Result<String> joinCourse(@RequestBody @Valid JoinCourseDTO joinCourseDTO) {
    String courseId = courseService.joinCourse(joinCourseDTO);
    return Result.success(courseId);
  }

  /** 获取课程详情 */
  @GetMapping("/{id}")
  public Result<CourseVO> getCourseDetail(@PathVariable("id") String id) {
    CourseVO course = courseService.getCourseDetail(id);
    return Result.success(course);
  }

  /** 更新课程 */
  @PutMapping("/{id}")
  public Result<Boolean> updateCourse(
      @PathVariable("id") String id, @RequestBody @Valid UpdateCourseDTO updateCourseDTO) {
    boolean result = courseService.updateCourse(id, updateCourseDTO);
    return Result.success(result);
  }

  /** 删除课程 */
  @DeleteMapping("/{id}")
  public Result<Boolean> deleteCourse(@PathVariable("id") String id) {
    boolean result = courseService.deleteCourse(id);
    return Result.success(result);
  }

  /** 获取课程统计信息 */
  @GetMapping("/{id}/stats")
  public Result<CourseStatsVO> getCourseStats(@PathVariable("id") String id) {
    CourseStatsVO stats = courseService.getCourseStats(id);
    return Result.success(stats);
  }

  /** 获取课程学习进度 */
  @GetMapping("/{id}/progress")
  public Result<CourseProgressVO> getCourseProgress(@PathVariable("id") String id) {
    CourseProgressVO progress = courseService.getCourseProgress(id);
    return Result.success(progress);
  }

  /** 获取课程资源（课件 / 任务 / 参考电路图） */
  @GetMapping("/{id}/resources")
  public Result<List<CourseResourceVO>> getCourseResources(
      @PathVariable("id") String id, @RequestParam(required = false) String type) {
    return Result.success(courseResourceService.listResources(id, type));
  }

  /** 创建课程资源（教师/管理员） */
  @PostMapping("/{id}/resources")
  public Result<Long> createCourseResource(
      @PathVariable("id") String id, @RequestBody @Valid CreateCourseResourceDTO dto) {
    return Result.success(courseResourceService.createResource(id, dto));
  }

  /** 上传课程资源（教师/管理员） */
  @PostMapping(value = "/{id}/resources/upload", consumes = "multipart/form-data")
  public Result<Long> uploadCourseResource(
      @PathVariable("id") String id,
      @RequestParam("file") MultipartFile file,
      @ModelAttribute CreateCourseResourceDTO dto,
      @RequestParam(required = false) String dueAt) {
    if (dueAt != null && !dueAt.isBlank()) {
      Date parsed = parseDate(dueAt);
      if (parsed != null) {
        dto.setDueAt(parsed);
      }
    }
    return Result.success(courseResourceService.uploadResource(id, dto, file));
  }

  /** 删除课程资源（教师/管理员） */
  @DeleteMapping("/{id}/resources/{resourceId}")
  public Result<Boolean> deleteCourseResource(
      @PathVariable("id") String id, @PathVariable("resourceId") Long resourceId) {
    return Result.success(courseResourceService.deleteResource(id, resourceId));
  }

  private Date parseDate(String input) {
    if (input == null || input.isBlank()) {
      return null;
    }
    String value = input.trim();
    try {
      // 支持前端 datetime-local: yyyy-MM-dd'T'HH:mm
      if (value.contains("T")) {
        SimpleDateFormat fmt = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm");
        return fmt.parse(value);
      }
      // ISO时间
      SimpleDateFormat iso = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSX");
      iso.setTimeZone(TimeZone.getTimeZone("UTC"));
      return iso.parse(value);
    } catch (ParseException ignore) {
      return null;
    }
  }

  /** 为课程创建知识库 */
  @PostMapping("/{id}/knowledge-base")
  public Result<String> createKnowledgeBaseForCourse(
      @PathVariable("id") String id,
      @RequestBody @Valid CreateKnowledgeBaseDTO createKnowledgeBaseDTO) {
    String knowledgeBaseId =
        knowledgeBaseService.createKnowledgeBaseForCourse(id, createKnowledgeBaseDTO);
    return Result.success(knowledgeBaseId);
  }
}

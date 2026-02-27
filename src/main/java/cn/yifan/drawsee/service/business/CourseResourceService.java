package cn.yifan.drawsee.service.business;

import cn.dev33.satoken.stp.StpUtil;
import cn.yifan.drawsee.constant.UserRole;
import cn.yifan.drawsee.exception.ApiError;
import cn.yifan.drawsee.exception.ApiException;
import cn.yifan.drawsee.mapper.CourseMapper;
import cn.yifan.drawsee.mapper.CourseResourceMapper;
import cn.yifan.drawsee.pojo.dto.CreateCourseResourceDTO;
import cn.yifan.drawsee.pojo.entity.Course;
import cn.yifan.drawsee.pojo.entity.CourseResource;
import cn.yifan.drawsee.pojo.vo.CourseResourceVO;
import cn.yifan.drawsee.service.base.MinioService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

@Slf4j
@Service
public class CourseResourceService {

    @Autowired
    private CourseResourceMapper courseResourceMapper;

    @Autowired
    private CourseMapper courseMapper;

    @Autowired
    private UserRoleService userRoleService;

    @Autowired
    private MinioService minioService;

    public List<CourseResourceVO> listResources(String courseId, String type) {
        Course course = courseMapper.getById(courseId);
        if (course == null || course.getIsDeleted()) {
            throw new ApiException(ApiError.COURSE_NOT_EXISTED, "文件不能为空");
        }

        Long userId = StpUtil.getLoginIdAsLong();
        String userRole = userRoleService.getCurrentUserRole();

        boolean isAdmin = UserRole.ADMIN.equals(userRole);
        boolean isCreator = course.getCreatorId() != null && course.getCreatorId().equals(userId);
        boolean isStudent = isStudentInCourse(course, userId);

        if (!isAdmin && !isCreator && !isStudent) {
            throw new ApiException(ApiError.PERMISSION_DENIED, "文件不能为空");
        }

        List<CourseResource> resources = (type == null || type.isBlank())
            ? courseResourceMapper.listByCourseId(courseId)
            : courseResourceMapper.listByCourseIdAndType(courseId, type);

        return resources.stream()
            .map(this::toVO)
            .collect(Collectors.toList());
    }

    @Transactional(rollbackFor = Exception.class)
    public Long createResource(String courseId, CreateCourseResourceDTO dto) {
        Course course = courseMapper.getById(courseId);
        if (course == null || course.getIsDeleted()) {
            throw new ApiException(ApiError.COURSE_NOT_EXISTED, "文件不能为空");
        }

        Long userId = StpUtil.getLoginIdAsLong();
        String userRole = userRoleService.getCurrentUserRole();
        boolean isAdmin = UserRole.ADMIN.equals(userRole);
        boolean isCreator = course.getCreatorId() != null && course.getCreatorId().equals(userId);

        if (!isAdmin && !isCreator) {
            throw new ApiException(ApiError.PERMISSION_DENIED, "文件不能为空");
        }

        CourseResource resource = new CourseResource();
        BeanUtils.copyProperties(dto, resource);
        resource.setCourseId(courseId);
        resource.setCreatedBy(userId);
        resource.setCreatedAt(new Date());
        resource.setUpdatedAt(new Date());
        resource.setIsDeleted(false);

        courseResourceMapper.insert(resource);
        return resource.getId();
    }

    @Transactional(rollbackFor = Exception.class)
    public Long uploadResource(String courseId, CreateCourseResourceDTO dto, MultipartFile file) {
        Course course = courseMapper.getById(courseId);
        if (course == null || course.getIsDeleted()) {
            throw new ApiException(ApiError.COURSE_NOT_EXISTED, "文件不能为空");
        }

        Long userId = StpUtil.getLoginIdAsLong();
        String userRole = userRoleService.getCurrentUserRole();
        boolean isAdmin = UserRole.ADMIN.equals(userRole);
        boolean isCreator = course.getCreatorId() != null && course.getCreatorId().equals(userId);

        if (!isAdmin && !isCreator) {
            throw new ApiException(ApiError.PERMISSION_DENIED, "文件不能为空");
        }
        if (file == null || file.isEmpty()) {
            throw new ApiException(ApiError.PARAM_ERROR, "文件不能为空");
        }
        if (dto == null || !StringUtils.hasText(dto.getType())) {
            throw new ApiException(ApiError.PARAM_ERROR, "文件不能为空");
        }

        String filename = file.getOriginalFilename();
        if (!isAllowedFile(dto.getType(), filename)) {
            throw new ApiException(ApiError.FILE_TYPE_NOT_SUPPORTED, "文件类型不支持");
        }

        String safeName = StringUtils.hasText(filename) ? filename : "resource-" + System.currentTimeMillis();
        String objectName = String.format("course-resources/%s/%s/%d_%s",
            courseId,
            dto.getType() != null ? dto.getType().toLowerCase(Locale.ROOT) : "other",
            System.currentTimeMillis(),
            safeName
        );

        try {
            String fileUrl = minioService.uploadFile(file, objectName);
            CourseResource resource = new CourseResource();
            BeanUtils.copyProperties(dto, resource);
            resource.setCourseId(courseId);
            resource.setFileUrl(fileUrl);
            resource.setFileName(safeName);
            resource.setFileSize(file.getSize());
            resource.setCreatedBy(userId);
            resource.setCreatedAt(new Date());
            resource.setUpdatedAt(new Date());
            resource.setIsDeleted(false);

            courseResourceMapper.insert(resource);
            return resource.getId();
        } catch (Exception e) {
            log.error("课程资源上传失败: courseId={}, filename={}", courseId, filename, e);
            throw new ApiException(ApiError.UPLOAD_FAILED, "文件不能为空");
        }
    }

    @Transactional(rollbackFor = Exception.class)
    public boolean deleteResource(String courseId, Long resourceId) {
        Course course = courseMapper.getById(courseId);
        if (course == null || course.getIsDeleted()) {
            throw new ApiException(ApiError.COURSE_NOT_EXISTED, "文件不能为空");
        }

        CourseResource resource = courseResourceMapper.getById(resourceId);
        if (resource == null || resource.getIsDeleted()) {
            throw new ApiException(ApiError.RESOURCE_NOT_EXISTED, "文件不能为空");
        }
        if (!courseId.equals(resource.getCourseId())) {
            throw new ApiException(ApiError.RESOURCE_NOT_EXISTED, "文件不能为空");
        }

        Long userId = StpUtil.getLoginIdAsLong();
        String userRole = userRoleService.getCurrentUserRole();
        boolean isAdmin = UserRole.ADMIN.equals(userRole);
        boolean isCreator = course.getCreatorId() != null && course.getCreatorId().equals(userId);

        if (!isAdmin && !isCreator) {
            throw new ApiException(ApiError.PERMISSION_DENIED, "文件不能为空");
        }

        resource.setIsDeleted(true);
        resource.setUpdatedAt(new Date());
        courseResourceMapper.update(resource);
        return true;
    }

    private CourseResourceVO toVO(CourseResource resource) {
        CourseResourceVO vo = new CourseResourceVO();
        BeanUtils.copyProperties(resource, vo);
        if (vo.getFileUrl() != null && !vo.getFileUrl().startsWith("http")) {
            try {
                vo.setFileUrl(minioService.getObjectUrl(vo.getFileUrl()));
            } catch (Exception e) {
                log.warn("生成课程资源链接失败: {}", vo.getFileUrl(), e);
            }
        }
        return vo;
    }

    private boolean isAllowedFile(String type, String filename) {
        if (!StringUtils.hasText(filename)) {
            return false;
        }
        String lower = filename.toLowerCase(Locale.ROOT);
        if ("COURSEWARE".equalsIgnoreCase(type)) {
            return lower.endsWith(".pdf") || lower.endsWith(".ppt") || lower.endsWith(".pptx")
                || lower.endsWith(".doc") || lower.endsWith(".docx");
        }
        if ("TASK".equalsIgnoreCase(type)) {
            return lower.endsWith(".pdf") || lower.endsWith(".ppt") || lower.endsWith(".pptx")
                || lower.endsWith(".doc") || lower.endsWith(".docx") || lower.endsWith(".xls")
                || lower.endsWith(".xlsx") || lower.endsWith(".zip") || lower.endsWith(".rar")
                || lower.endsWith(".7z");
        }
        if ("CIRCUIT_REF".equalsIgnoreCase(type)) {
            return lower.endsWith(".png") || lower.endsWith(".jpg") || lower.endsWith(".jpeg")
                || lower.endsWith(".webp") || lower.endsWith(".svg");
        }
        return true;
    }

    private boolean isStudentInCourse(Course course, Long userId) {
        if (course == null || course.getStudentIds() == null || userId == null) {
            return false;
        }
        String userIdText = String.valueOf(userId);
        for (Object item : course.getStudentIds()) {
            if (item == null) {
                continue;
            }
            if (userIdText.equals(String.valueOf(item))) {
                return true;
            }
        }
        return false;
    }
}

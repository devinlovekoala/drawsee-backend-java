package cn.yifan.drawsee.service.business;

import cn.yifan.drawsee.pojo.entity.UserDocument;
import cn.yifan.drawsee.exception.ApiError;
import cn.yifan.drawsee.exception.ApiException;
import cn.yifan.drawsee.mapper.UserDocumentMapper;
import cn.yifan.drawsee.service.base.MinioService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.UUID;

/**
 * 用户文档服务
 * 
 * @author devin
 * @date 2025-01-28
 */
@Service
@Slf4j
public class UserDocumentService {

    @Autowired
    private UserDocumentMapper userDocumentMapper;

    @Autowired
    private MinioService minioService;

    // 支持的文件类型
    private static final List<String> SUPPORTED_FILE_TYPES = Arrays.asList(
        "application/pdf",
        "image/jpeg",
        "image/jpg",
        "image/png",
        "image/gif",
        "text/plain",
        "application/msword",
        "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
    );

    /**
     * 上传文档
     */
    public UserDocument uploadDocument(MultipartFile file, Long userId, String title, String description) {
        if (file == null || file.isEmpty()) {
            throw new ApiException(ApiError.FILE_UPLOAD_FAILED);
        }

        // 验证文件类型
        String contentType = file.getContentType();
        if (!SUPPORTED_FILE_TYPES.contains(contentType)) {
            throw new ApiException(ApiError.FILE_TYPE_NOT_SUPPORTED);
        }

        try {
            // 上传到MinIO
            String fileName = file.getOriginalFilename();
            String objectName = "user-documents/" + userId + "/" + System.currentTimeMillis() + "_" + fileName;
            String minioUrl = minioService.uploadFile(file, objectName);
            
            // 保存到数据库
            UserDocument document = new UserDocument();
            document.setUuid(UUID.randomUUID().toString());
            document.setUserId(userId);
            document.setTitle(title != null ? title : fileName);
            document.setDescription(description);
            document.setDocumentType(getDocumentTypeFromContentType(contentType));
            // store the presigned URL returned by MinioService so frontend can preview/download
            document.setFileUrl(minioUrl);
            document.setObjectPath(objectName);
            document.setFileSize(file.getSize());
            document.setCreatedAt(new Date());
            document.setUpdatedAt(new Date());
            document.setIsDeleted(false);
            
            userDocumentMapper.insert(document);
            return document;
            
        } catch (Exception e) {
            log.error("上传文档失败: ", e);
            throw new ApiException(ApiError.UPLOAD_FAILED);
        }
    }

    /**
     * 获取用户文档列表
     */
    public List<UserDocument> getUserDocuments(Long userId) {
        return userDocumentMapper.getByUserId(userId);
    }

    /**
     * 获取文档详情
     */
    public UserDocument getDocumentById(Long id, Long userId) {
        UserDocument document = userDocumentMapper.getById(id);
        if (document == null || document.getIsDeleted()) {
            throw new ApiException(ApiError.RESOURCE_NOT_FOUND);
        }
        
        // 验证文档所有权
        if (!document.getUserId().equals(userId)) {
            throw new ApiException(ApiError.ACCESS_DENIED);
        }
        
        return document;
    }

    /**
     * 根据UUID获取文档详情
     */
    public UserDocument getDocumentByUuid(String uuid, Long userId) {
        UserDocument document = userDocumentMapper.getByUuid(uuid);
        if (document == null || document.getIsDeleted()) {
            throw new ApiException(ApiError.RESOURCE_NOT_FOUND);
        }
        
        // 验证文档所有权
        if (!document.getUserId().equals(userId)) {
            throw new ApiException(ApiError.ACCESS_DENIED);
        }
        
        return document;
    }

    /**
     * 删除文档
     */
    public void deleteDocument(Long id, Long userId) {
        getDocumentById(id, userId); // 验证权限
        
        try {
            // TODO: 需要MinioService添加删除方法
            // minioService.deleteObject(document.getObjectPath());
            
            // 逻辑删除记录
            userDocumentMapper.delete(id);
            
        } catch (Exception e) {
            log.error("删除文档失败: ", e);
            throw new ApiException(ApiError.SYSTEM_ERROR);
        }
    }

    /**
     * 获取文档下载URL
     */
    public String getDocumentDownloadUrl(Long id, Long userId) {
        UserDocument document = getDocumentById(id, userId);
        
        try {
            return minioService.getObjectUrl(document.getObjectPath());
        } catch (Exception e) {
            log.error("获取文档下载URL失败: ", e);
            throw new ApiException(ApiError.SYSTEM_ERROR);
        }
    }

    /**
     * 根据内容类型获取文档类型
     */
    private String getDocumentTypeFromContentType(String contentType) {
        switch (contentType) {
            case "application/pdf":
                return "pdf";
            case "application/msword":
            case "application/vnd.openxmlformats-officedocument.wordprocessingml.document":
                return "docx";
            case "text/plain":
                return "txt";
            case "image/jpeg":
            case "image/jpg":
            case "image/png":
            case "image/gif":
                return "image";
            default:
                return "unknown";
        }
    }
}

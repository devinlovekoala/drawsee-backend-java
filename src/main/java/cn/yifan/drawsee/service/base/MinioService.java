package cn.yifan.drawsee.service.base;

import cn.yifan.drawsee.config.MinioConfig;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.StatObjectArgs;
import io.minio.errors.MinioException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/**
 * @FileName MinioService
 * @Description
 * @Author yifan
 * @date 2025-03-09 16:21
 **/

@Service
public class MinioService {

    @Autowired
    private MinioConfig minioConfig;
    @Autowired
    private MinioClient minioClient;

    public String getObjectUrl(String objectName) throws MinioException, IOException, NoSuchAlgorithmException, InvalidKeyException {
        // 添加响应头参数，允许浏览器直接访问资源
        Map<String, String> reqParams = new ConcurrentHashMap<>();
        reqParams.put("response-content-disposition", "inline");
        // 获取文件的 MIME 类型，这里简单根据文件扩展名判断
        String contentType = getContentType(objectName);
        reqParams.put("response-content-type", contentType);
        // 添加支持范围请求的响应头
        reqParams.put("response-accept-ranges", "bytes");

        // 获取文件大小并添加到响应头
        long contentLength = minioClient.statObject(
                StatObjectArgs.builder()
                        .bucket(minioConfig.getBucketName())
                        .object(objectName)
                        .build()
        ).size();
        reqParams.put("response-content-length", String.valueOf(contentLength));

        return minioClient.getPresignedObjectUrl(
            io.minio.GetPresignedObjectUrlArgs.builder()
                .method(io.minio.http.Method.GET)
                .bucket(minioConfig.getBucketName())
                .object(objectName)
                .expiry(60 * 60 * 24) // 默认24小时过期
                .extraQueryParams(reqParams) // 添加额外的查询参数
                .build()
        );
    }

    private String getContentType(String objectName) {
        if (objectName.endsWith(".jpg") || objectName.endsWith(".jpeg")) {
            return "image/jpeg";
        } else if (objectName.endsWith(".png")) {
            return "image/png";
        } else if (objectName.endsWith(".gif")) {
            return "image/gif";
        } else if (objectName.endsWith(".mp4")) {
            return "video/mp4";
        } else if (objectName.endsWith(".pdf")) {
            return "application/pdf";
        } else if (objectName.endsWith(".doc")) {
            return "application/msword";
        } else if (objectName.endsWith(".docx")) {
            return "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
        }
        // 可以根据需要添加更多类型
        return "application/octet-stream";
    }

    /**
     * 上传图片到MinIO
     * @param file 要上传的文件
     * @param objectName 对象名称
     * @return 上传后的对象名称
     * @throws MinioException MinIO异常
     * @throws IOException IO异常
     * @throws NoSuchAlgorithmException 算法异常
     * @throws InvalidKeyException 密钥异常
     */
    public String uploadImage(MultipartFile file, String objectName) throws MinioException, IOException, NoSuchAlgorithmException, InvalidKeyException {
        // 检查文件是否为空
        if (file.isEmpty()) {
            throw new IllegalArgumentException("文件不能为空");
        }
        // 检查文件类型是否为图片
        String contentType = file.getContentType();
        if (contentType == null || !contentType.startsWith("image/")) {
            throw new IllegalArgumentException("只能上传图片文件");
        }
        // 上传文件到MinIO
        minioClient.putObject(
            PutObjectArgs.builder()
                .bucket(minioConfig.getBucketName())
                .object(objectName)
                .stream(file.getInputStream(), file.getSize(), -1)
                .contentType(contentType)
                .build()
        );
        return objectName;
    }

    /**
     * 上传任意文件到MinIO
     * @param file 要上传的文件
     * @param objectName 对象名称
     * @return 上传后的对象URL
     * @throws MinioException MinIO异常
     * @throws IOException IO异常
     * @throws NoSuchAlgorithmException 算法异常
     * @throws InvalidKeyException 密钥异常
     */
    public String uploadFile(MultipartFile file, String objectName) throws MinioException, IOException, NoSuchAlgorithmException, InvalidKeyException {
        // 检查文件是否为空
        if (file.isEmpty()) {
            throw new IllegalArgumentException("文件不能为空");
        }
        
        // 获取文件类型
        String contentType = file.getContentType();
        if (contentType == null) {
            // 如果无法获取Content-Type，根据文件名推断
            contentType = getContentType(Objects.requireNonNull(file.getOriginalFilename()));
        }
        
        // 上传文件到MinIO
        minioClient.putObject(
            PutObjectArgs.builder()
                .bucket(minioConfig.getBucketName())
                .object(objectName)
                .stream(file.getInputStream(), file.getSize(), -1)
                .contentType(contentType)
                .build()
        );
        
        // 返回完整URL
        return getObjectUrl(objectName);
    }
}

package cn.yifan.drawsee.service.base;

import cn.yifan.drawsee.config.MinioConfig;
import cn.yifan.drawsee.pojo.vo.ResourceVO;
import io.minio.*;
import io.minio.errors.MinioException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.InputStreamResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/**
 * @FileName MinioService
 * @Description Minio服务类，处理文件上传下载等操作
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
        String lower = objectName.toLowerCase();
        if (lower.endsWith(".jpg") || lower.endsWith(".jpeg")) {
            return "image/jpeg";
        } else if (lower.endsWith(".png")) {
            return "image/png";
        } else if (lower.endsWith(".gif")) {
            return "image/gif";
        } else if (lower.endsWith(".mp4")) {
            return "video/mp4";
        } else if (lower.endsWith(".pdf")) {
            return "application/pdf";
        } else if (lower.endsWith(".doc")) {
            return "application/msword";
        } else if (lower.endsWith(".docx")) {
            return "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
        } else if (lower.endsWith(".ppt")) {
            return "application/vnd.ms-powerpoint";
        } else if (lower.endsWith(".pptx")) {
            return "application/vnd.openxmlformats-officedocument.presentationml.presentation";
        }
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

    public String uploadImage(java.awt.image.BufferedImage image, String objectName) throws MinioException, IOException, NoSuchAlgorithmException, InvalidKeyException {
		java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream();
		javax.imageio.ImageIO.write(image, "png", baos);
		byte[] bytes = baos.toByteArray();
		java.io.ByteArrayInputStream bais = new java.io.ByteArrayInputStream(bytes);
		minioClient.putObject(
			PutObjectArgs.builder()
				.bucket(minioConfig.getBucketName())
				.object(objectName)
				.stream(bais, bytes.length, -1)
				.contentType("image/png")
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

    public String getObjectBase64(String objectName, int maxBytes) throws MinioException, IOException, NoSuchAlgorithmException, InvalidKeyException {
		GetObjectResponse response = minioClient.getObject(
			GetObjectArgs.builder()
				.bucket(minioConfig.getBucketName())
				.object(objectName)
				.build()
		);
		try (java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream()) {
			byte[] buffer = new byte[8192];
			int total = 0;
			int len;
			while ((len = response.read(buffer)) != -1) {
				if (maxBytes > 0 && total + len > maxBytes) {
					baos.write(buffer, 0, Math.max(0, maxBytes - total));
					break;
				}
				baos.write(buffer, 0, len);
				total += len;
			}
			return java.util.Base64.getEncoder().encodeToString(baos.toByteArray());
		} finally {
			response.close();
		}
	}

	public GetObjectResponse getObjectStream(String objectName) throws MinioException, IOException, NoSuchAlgorithmException, InvalidKeyException {
		return minioClient.getObject(
			GetObjectArgs.builder()
				.bucket(minioConfig.getBucketName())
				.object(objectName)
				.build()
		);
	}

    /**
     * 获取资源信息
     * @param objectName 对象名称
     * @return 资源信息VO
     */
    public ResourceVO getResource(String objectName) throws MinioException, IOException, NoSuchAlgorithmException, InvalidKeyException {
        // 获取对象的元数据
        StatObjectResponse stat = minioClient.statObject(
                StatObjectArgs.builder()
                        .bucket(minioConfig.getBucketName())
                        .object(objectName)
                        .build()
        );
        
        // 获取预签名URL
        String url = getObjectUrl(objectName);
        
        // 返回资源信息
        return new ResourceVO(url, stat.size(), getContentType(objectName));
    }

    /**
     * 下载资源
     * @param objectName 对象名称
     * @return ResponseEntity包含资源流
     */
    public ResponseEntity<Resource> downloadResource(String objectName) throws MinioException, IOException, NoSuchAlgorithmException, InvalidKeyException {
        // 获取对象
        GetObjectResponse response = minioClient.getObject(
                GetObjectArgs.builder()
                        .bucket(minioConfig.getBucketName())
                        .object(objectName)
                        .build()
        );

        // 获取对象的元数据
        StatObjectResponse stat = minioClient.statObject(
                StatObjectArgs.builder()
                        .bucket(minioConfig.getBucketName())
                        .object(objectName)
                        .build()
        );

        // 创建InputStreamResource
        InputStream inputStream = response;
        Resource resource = new InputStreamResource(inputStream);

        // 设置响应头
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(getContentType(objectName)))
                .contentLength(stat.size())
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + objectName + "\"")
                .body(resource);
    }
}

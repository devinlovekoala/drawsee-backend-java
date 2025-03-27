package cn.yifan.drawsee.service.business;

import cn.yifan.drawsee.constant.AiTaskMessageType;
import cn.yifan.drawsee.constant.MinioObjectPath;
import cn.yifan.drawsee.constant.RedisKey;
import cn.yifan.drawsee.exception.ApiError;
import cn.yifan.drawsee.exception.ApiException;
import cn.yifan.drawsee.pojo.dto.GetSolveWaysDTO;
import cn.yifan.drawsee.pojo.dto.UploadAnimationFrameDTO;
import cn.yifan.drawsee.pojo.vo.RecognizeTextVO;
import cn.yifan.drawsee.service.base.AiService;
import cn.yifan.drawsee.service.base.MinioService;
import com.fasterxml.jackson.core.JsonProcessingException;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RStream;
import org.redisson.api.RedissonClient;
import org.redisson.api.stream.StreamAddArgs;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * @FileName ToolService
 * @Description
 * @Author yifan
 * @date 2025-03-22 16:20
 **/

@Service
@Slf4j
public class ToolService {

    @Autowired
    private RedissonClient redissonClient;
    @Autowired
    private AiService aiService;
    @Autowired
    private MinioService minioService;

    public void uploadAnimationFrame(UploadAnimationFrameDTO uploadAnimationFrameDTO) {
        RStream<String, Object> redisStream = redissonClient.getStream(RedisKey.AI_TASK_PREFIX + uploadAnimationFrameDTO.getTaskId());
        Map<String, Object> data = new ConcurrentHashMap<>();
        data.put("nodeId", uploadAnimationFrameDTO.getNodeId());
        data.put("frame", uploadAnimationFrameDTO.getFrame());
        redisStream.add(StreamAddArgs.entries(
        "type", AiTaskMessageType.DATA,
        "data", data
        ));
    }

    public RecognizeTextVO recognizeTextFromImage(MultipartFile file) {
        // 判断file是否为图片
        if (
            !Objects.equals(file.getContentType(), "image/png") &&
            !Objects.equals(file.getContentType(), "image/jpeg") &&
            !Objects.equals(file.getContentType(), "image/jpg")
        ) {
            throw new ApiException(ApiError.FILE_TYPE_NOT_SUPPORTED);
        }
        // 生成UUID，并去掉-
        String uuid = UUID.randomUUID().toString().replace("-", "");
        // 获取原文件后缀
        String suffix = file.getOriginalFilename().substring(file.getOriginalFilename().lastIndexOf("."));
        String objectName = MinioObjectPath.RECOGNIZE_IMAGE_PATH + uuid + suffix;
        try {
            minioService.uploadImage(file, objectName);
        } catch (Exception e) {
            log.error("图片上传失败", e);
            throw new ApiException(ApiError.FILE_UPLOAD_FAILED);
        }
        try {
            String imageUrl = minioService.getObjectUrl(objectName);
            return new RecognizeTextVO(aiService.recognizeTextFromImage(imageUrl));
        } catch (Exception e) {
            log.error("图片识别失败", e);
            throw new ApiException(ApiError.IMAGE_RECOGNIZE_FAILED);
        }
    }

    public List<String> getSolveWays(GetSolveWaysDTO getSolveWaysDTO) {
        String question = getSolveWaysDTO.getQuestion();
        try {
            return aiService.getSolveWays(question);
        } catch (JsonProcessingException e) {
            throw new ApiException(ApiError.SYSTEM_ERROR);
        }
    }
}

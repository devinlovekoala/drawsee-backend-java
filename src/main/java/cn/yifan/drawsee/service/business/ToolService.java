package cn.yifan.drawsee.service.business;

import cn.yifan.drawsee.constant.AiTaskMessageType;
import cn.yifan.drawsee.constant.MinioObjectPath;
import cn.yifan.drawsee.constant.RedisKey;
import cn.yifan.drawsee.exception.ApiError;
import cn.yifan.drawsee.exception.ApiException;
import cn.yifan.drawsee.mapper.NodeMapper;
import cn.yifan.drawsee.pojo.dto.GetSolveWaysDTO;
import cn.yifan.drawsee.pojo.dto.UploadAnimationFrameDTO;
import cn.yifan.drawsee.pojo.entity.Node;
import cn.yifan.drawsee.pojo.vo.RecognizeTextVO;
import cn.yifan.drawsee.service.base.AiService;
import cn.yifan.drawsee.service.base.MinioService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
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
    @Autowired
    private NodeMapper nodeMapper;
    @Autowired
    private ObjectMapper objectMapper;

    /**
     * 处理动画渲染完成后的帧信息
     * 1. 更新Redis流中的节点帧信息
     * 2. 更新节点数据中的progress为"渲染完成"，添加frame字段保存视频URL
     * 
     * @param uploadAnimationFrameDTO 动画帧信息
     */
    public void uploadAnimationFrame(UploadAnimationFrameDTO uploadAnimationFrameDTO) {
        try {
            // 1. 更新Redis流中的节点状态
            RStream<String, Object> redisStream = redissonClient.getStream(RedisKey.AI_TASK_PREFIX + uploadAnimationFrameDTO.getTaskId());
            Map<String, Object> data = new ConcurrentHashMap<>();
            data.put("nodeId", uploadAnimationFrameDTO.getNodeId());
            data.put("frame", uploadAnimationFrameDTO.getFrame());
            data.put("progress", "渲染完成");
            redisStream.add(StreamAddArgs.entries(
                "type", AiTaskMessageType.DATA,
                "data", data
            ));
            
            // 2. 更新节点数据
            Node node = nodeMapper.getById(uploadAnimationFrameDTO.getNodeId());
            if (node != null) {
                // 解析现有节点数据
                Map<String, Object> nodeData = objectMapper.readValue(node.getData(), Map.class);
                
                // 更新节点数据
                nodeData.put("progress", "渲染完成");
                nodeData.put("frame", uploadAnimationFrameDTO.getFrame());
                
                // 保存更新后的节点数据
                node.setData(objectMapper.writeValueAsString(nodeData));
                nodeMapper.update(node);
                
                log.info("动画节点更新完成: nodeId={}, frame={}", node.getId(), uploadAnimationFrameDTO.getFrame());
            } else {
                log.error("未找到对应节点: nodeId={}", uploadAnimationFrameDTO.getNodeId());
            }
        } catch (JsonProcessingException e) {
            log.error("更新节点数据失败: {}", e.getMessage(), e);
        }
    }

    public RecognizeTextVO recognizeTextFromImage(MultipartFile file) {
        // 判断file是否为图片
        if (
            !Objects.equals(file.getContentType(), "image/png") &&
            !Objects.equals(file.getContentType(), "image/jpeg") &&
            !Objects.equals(file.getContentType(), "image/jpg")
        ) {
            throw new ApiException(ApiError.FILE_TYPE_NOT_SUPPORTED, "文件不能为空");
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
            throw new ApiException(ApiError.FILE_UPLOAD_FAILED, "文件不能为空");
        }
        try {
            String imageUrl = minioService.getObjectUrl(objectName);
            return new RecognizeTextVO(aiService.recognizeTextFromImage(imageUrl));
        } catch (Exception e) {
            log.error("图片识别失败", e);
            throw new ApiException(ApiError.IMAGE_RECOGNIZE_FAILED, "文件不能为空");
        }
    }

    public List<String> getSolveWays(GetSolveWaysDTO getSolveWaysDTO) {
        String question = getSolveWaysDTO.getQuestion();
        try {
            return aiService.getSolveWays(question);
        } catch (JsonProcessingException e) {
            throw new ApiException(ApiError.SYSTEM_ERROR, "文件不能为空");
        }
    }
}

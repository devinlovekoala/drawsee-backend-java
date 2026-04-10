package cn.yifan.drawsee.service.business;

import cn.yifan.drawsee.constant.AiTaskMessageType;
import cn.yifan.drawsee.constant.MinioObjectPath;
import cn.yifan.drawsee.constant.RedisKey;
import cn.yifan.drawsee.exception.ApiError;
import cn.yifan.drawsee.exception.ApiException;
import cn.yifan.drawsee.mapper.NodeMapper;
import cn.yifan.drawsee.pojo.dto.GetSolveWaysDTO;
import cn.yifan.drawsee.pojo.dto.UploadAnimationFrameDTO;
import cn.yifan.drawsee.pojo.entity.CircuitDesign;
import cn.yifan.drawsee.pojo.entity.Node;
import cn.yifan.drawsee.pojo.vo.CircuitImageUploadVO;
import cn.yifan.drawsee.pojo.vo.RecognizeTextVO;
import cn.yifan.drawsee.service.base.AiService;
import cn.yifan.drawsee.service.base.MinioService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.OffsetDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RStream;
import org.redisson.api.RedissonClient;
import org.redisson.api.stream.StreamAddArgs;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

/**
 * @FileName ToolService @Description @Author yifan
 *
 * @date 2025-03-22 16:20
 */
@Service
@Slf4j
public class ToolService {

  @Autowired private RedissonClient redissonClient;
  @Autowired private AiService aiService;
  @Autowired private MinioService minioService;
  @Autowired private NodeMapper nodeMapper;
  @Autowired private ObjectMapper objectMapper;

  /**
   * 处理动画渲染完成后的帧信息 1. 更新Redis流中的节点帧信息 2. 更新节点数据中的progress为"渲染完成"，添加frame字段保存视频URL
   *
   * @param uploadAnimationFrameDTO 动画帧信息
   */
  public void uploadAnimationFrame(UploadAnimationFrameDTO uploadAnimationFrameDTO) {
    try {
      // 1. 更新Redis流中的节点状态
      RStream<String, Object> redisStream =
          redissonClient.getStream(RedisKey.AI_TASK_PREFIX + uploadAnimationFrameDTO.getTaskId());
      Map<String, Object> data = new ConcurrentHashMap<>();
      data.put("nodeId", uploadAnimationFrameDTO.getNodeId());
      data.put("frame", uploadAnimationFrameDTO.getFrame());
      data.put("progress", "渲染完成");
      redisStream.add(StreamAddArgs.entries("type", AiTaskMessageType.DATA, "data", data));

      // 2. 更新节点数据
      Node node = nodeMapper.getById(uploadAnimationFrameDTO.getNodeId());
      if (node != null) {
        // 解析现有节点数据
        Map<String, Object> nodeData =
          objectMapper.readValue(node.getData(), new TypeReference<Map<String, Object>>() {});

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
    if (!Objects.equals(file.getContentType(), "image/png")
        && !Objects.equals(file.getContentType(), "image/jpeg")
        && !Objects.equals(file.getContentType(), "image/jpg")) {
      throw new ApiException(ApiError.FILE_TYPE_NOT_SUPPORTED, "文件不能为空");
    }
    // 生成UUID，并去掉-
    String uuid = UUID.randomUUID().toString().replace("-", "");
    // 获取原文件后缀
    String originalFilename = file.getOriginalFilename();
    String suffix = ".png";
    if (originalFilename != null && originalFilename.contains(".")) {
      suffix = originalFilename.substring(originalFilename.lastIndexOf('.'));
    }
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

  public CircuitImageUploadVO uploadCircuitImage(MultipartFile file) {
    validateImageFile(file);
    String originalName = file.getOriginalFilename();
    String suffix = "";
    if (originalName != null && originalName.contains(".")) {
      suffix = originalName.substring(originalName.lastIndexOf('.'));
    } else if (Objects.equals(file.getContentType(), "image/png")) {
      suffix = ".png";
    } else {
      suffix = ".jpg";
    }
    String uuid = UUID.randomUUID().toString().replace("-", "");
    String objectName = MinioObjectPath.CIRCUIT_RECOGNIZE_IMAGE_PATH + uuid + suffix;
    try {
      minioService.uploadImage(file, objectName);
    } catch (Exception e) {
      log.error("电路图片上传失败", e);
      throw new ApiException(ApiError.FILE_UPLOAD_FAILED, "电路图片上传失败");
    }

    try {
      String imageUrl = minioService.getObjectUrl(objectName);
      return new CircuitImageUploadVO(imageUrl);
    } catch (Exception e) {
      log.error("获取电路图片URL失败", e);
      throw new ApiException(ApiError.FILE_UPLOAD_FAILED, "获取图片地址失败");
    }
  }

  public CircuitDesign recognizeCircuitFromImage(MultipartFile file) {
    CircuitImageUploadVO uploadResult = uploadCircuitImage(file);
    return recognizeCircuitFromUploadedImage(uploadResult.getImageUrl());
  }

  public CircuitDesign recognizeCircuitFromUploadedImage(String imageUrl) {
    if (imageUrl == null || imageUrl.isBlank()) {
      throw new ApiException(ApiError.PARAM_ERROR, "图片地址不能为空");
    }
    try {
      CircuitDesign design = aiService.recognizeCircuitDesignFromImage(imageUrl);
      normalizeCircuitDesign(design);
      return design;
    } catch (ApiException e) {
      throw e;
    } catch (Exception e) {
      log.error("电路图识别失败", e);
      throw new ApiException(ApiError.IMAGE_RECOGNIZE_FAILED, "电路图识别失败，请重试");
    }
  }

  private void validateImageFile(MultipartFile file) {
    if (file == null || file.isEmpty()) {
      throw new ApiException(ApiError.PARAM_ERROR, "文件不能为空");
    }
    String contentType = file.getContentType();
    if (contentType == null
        || (!contentType.equals("image/png")
            && !contentType.equals("image/jpeg")
            && !contentType.equals("image/jpg"))) {
      throw new ApiException(ApiError.FILE_TYPE_NOT_SUPPORTED, "仅支持PNG或JPEG图片");
    }
  }

  private void normalizeCircuitDesign(CircuitDesign design) {
    if (design == null) {
      throw new ApiException(ApiError.IMAGE_RECOGNIZE_FAILED, "未识别到电路设计");
    }
    if (design.getElements() == null) {
      design.setElements(new ArrayList<>());
    }
    if (design.getConnections() == null) {
      design.setConnections(new ArrayList<>());
    }
    CircuitDesign.CircuitMetadata metadata = design.getMetadata();
    if (metadata == null) {
      metadata = new CircuitDesign.CircuitMetadata();
      design.setMetadata(metadata);
    }
    if (metadata.getTitle() == null || metadata.getTitle().isBlank()) {
      metadata.setTitle("AI识别电路");
    }
    if (metadata.getDescription() == null || metadata.getDescription().isBlank()) {
      metadata.setDescription("由电路图识别自动生成");
    }
    String now = OffsetDateTime.now().toString();
    if (metadata.getCreatedAt() == null || metadata.getCreatedAt().isBlank()) {
      metadata.setCreatedAt(now);
    }
    metadata.setUpdatedAt(now);
  }
}

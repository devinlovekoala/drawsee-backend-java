package cn.yifan.drawsee.service.business;

import cn.yifan.drawsee.pojo.dto.CreateAiTaskDTO;

/**
 * @FileName ModeAutoDetectionService
 * @Description 自动识别模式服务接口
 * @Author yifan
 * @date 2025-03-08 16:30
 **/
public interface ModeAutoDetectionService {

    /**
     * 根据提示词和学科自动识别合适的AI任务类型
     *
     * @param createAiTaskDTO AI任务创建DTO
     * @return 识别后的AI任务类型
     */
    String detectTaskType(CreateAiTaskDTO createAiTaskDTO);

} 
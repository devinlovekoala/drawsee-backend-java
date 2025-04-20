package cn.yifan.drawsee.service.business;

import cn.yifan.drawsee.constant.AiTaskType;
import cn.yifan.drawsee.pojo.dto.CreateAiTaskDTO;
import cn.yifan.drawsee.service.base.PromptService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import dev.langchain4j.model.chat.ChatLanguageModel;
import lombok.extern.slf4j.Slf4j;

/**
 * @FileName ModeAutoDetectionService
 * @Description 自动识别模式服务类，用于根据提示词内容自动识别合适的AI任务类型
 * @Author yifan
 * @date 2025-03-08 16:35
 **/
@Service
@Slf4j
public class ModeAutoDetectionService {

    @Autowired
    private ChatLanguageModel doubaoChatLanguageModel;
    
    @Autowired
    private PromptService promptService;

    /**
     * 根据提示词自动识别合适的AI任务类型
     * 仅在type为GENERAL时才进行智能识别，否则保持原有类型不变
     *
     * @param createAiTaskDTO AI任务创建DTO
     * @return 识别后的AI任务类型
     */
    public String detectTaskType(CreateAiTaskDTO createAiTaskDTO) {
        // 判断是否需要进行模式识别
        String type = createAiTaskDTO.getType();
        
        // 如果type不是GENERAL，直接返回原始类型
        if (!AiTaskType.GENERAL.equals(type)) {
            log.info("任务类型已明确指定为: {}, 跳过自动识别", type);
            return type;
        }
        
        try {
            String prompt = createAiTaskDTO.getPrompt();
            
            // 使用PromptService获取模式检测提示词
            String detectionPrompt = promptService.getModeDetectionPrompt(prompt);
            String detectedType = doubaoChatLanguageModel.chat(detectionPrompt).trim();
            
            // 验证返回的类型是否有效
            if (isValidTaskType(detectedType)) {
                log.info("AI检测到的任务类型: {}", detectedType);
                return detectedType;
            } else {
                log.warn("AI返回的任务类型 '{}' 无效，使用默认类型: {}", detectedType, AiTaskType.GENERAL);
                return AiTaskType.GENERAL;
            }
        } catch (Exception e) {
            log.error("任务类型检测失败", e);
            return AiTaskType.GENERAL;
        }
    }
    
    /**
     * 验证任务类型是否有效
     *
     * @param taskType 任务类型
     * @return 是否有效
     */
    private boolean isValidTaskType(String taskType) {
        if (taskType == null) {
            return false;
        }
        
        // 检查是否是已定义的任务类型
        return taskType.equals(AiTaskType.GENERAL) ||
               taskType.equals(AiTaskType.SOLVER_FIRST) ||
               taskType.equals(AiTaskType.PLANNER) ||
               taskType.equals(AiTaskType.CIRCUIT_ANALYSIS);
    }
}
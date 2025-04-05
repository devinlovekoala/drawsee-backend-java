package cn.yifan.drawsee.service.business;

import cn.yifan.drawsee.constant.AiTaskType;
import cn.yifan.drawsee.constant.KnowledgeSubject;
import cn.yifan.drawsee.pojo.dto.CreateAiTaskDTO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import dev.langchain4j.model.chat.ChatLanguageModel;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.util.StreamUtils;

import java.nio.charset.StandardCharsets;

/**
 * @FileName ModeAutoDetectionService
 * @Description 自动识别模式服务类
 * @Author yifan
 * @date 2025-03-08 16:35
 **/
@Service
@Slf4j
public class ModeAutoDetectionService {

    @Autowired
    private ChatLanguageModel doubaoChatLanguageModel;
    
    @Autowired
    private ObjectMapper objectMapper;

    private String getModeDetectionPrompt() {
        try {
            ClassPathResource resource = new ClassPathResource("prompt/mode_detection.txt");
            return StreamUtils.copyToString(resource.getInputStream(), StandardCharsets.UTF_8);
        } catch (Exception e) {
            log.error("读取模式检测提示词模板失败", e);
            return "";
        }
    }

    /**
     * 根据提示词和学科自动识别合适的AI任务类型
     *
     * @param createAiTaskDTO AI任务创建DTO
     * @return 识别后的AI任务类型
     */
    public String detectTaskType(CreateAiTaskDTO createAiTaskDTO) {
        try {
            String prompt = createAiTaskDTO.getPrompt().toLowerCase();
            String subject = createAiTaskDTO.getSubject();
            Boolean enableKnowledgeQA = createAiTaskDTO.getEnableKnowledgeQA();

            // 1. 如果是通用学科，根据是否启用知识问答模式决定
            if (KnowledgeSubject.GENERAL.equals(subject)) {
                return Boolean.TRUE.equals(enableKnowledgeQA) ? 
                    AiTaskType.KNOWLEDGE : AiTaskType.GENERAL;
            }

            // 2. 使用AI模型进行更细致的分类
            String detectionPrompt = getModeDetectionPrompt()
                .replace("{{prompt}}", prompt)
                .replace("{{subject}}", subject)
                .replace("{{enableKnowledgeQA}}", String.valueOf(enableKnowledgeQA));
            
            String detectedType = doubaoChatLanguageModel.chat(detectionPrompt).trim();
            
            // 3. 验证返回的类型是否有效
            if (isValidTaskType(detectedType, subject)) {
                log.info("AI检测到的任务类型: {}", detectedType);
                return detectedType;
            }

            // 4. 如果AI返回的类型无效，使用启发式规则进行判断
            if (KnowledgeSubject.LINEAR_ALGEBRA.equals(subject)) {
                if (prompt.contains("动画") || prompt.contains("演示") || 
                    prompt.contains("animation") || prompt.contains("demonstrate")) {
                    return AiTaskType.ANIMATION;
                }
            }

            if (KnowledgeSubject.ELECTRONIC.equals(subject)) {
                if (prompt.contains("分析") || prompt.contains("电路") || 
                    prompt.contains("analyze") || prompt.contains("circuit")) {
                    return AiTaskType.CIRCUIT_ANALYZE;
                }
            }

            // 5. 其他情况根据内容特征判断
            if (prompt.contains("知识点") || prompt.contains("概念") || 
                prompt.contains("knowledge") || prompt.contains("concept")) {
                return AiTaskType.KNOWLEDGE;
            }

            if (prompt.contains("求解") || prompt.contains("计算") || prompt.contains("解答") || 
                prompt.contains("solve") || prompt.contains("calculate")) {
                return AiTaskType.SOLVER_FIRST;
            }

            if (prompt.contains("规划") || prompt.contains("计划") || 
                prompt.contains("plan") || prompt.contains("schedule")) {
                return AiTaskType.PLANNER;
            }

            if (prompt.contains("html") || prompt.contains("网页") || 
                prompt.contains("page") || prompt.contains("制作")) {
                return AiTaskType.HTML_MAKER;
            }

            // 6. 默认返回知识问答模式
            return AiTaskType.KNOWLEDGE;
            
        } catch (Exception e) {
            log.error("任务类型检测失败", e);
            return AiTaskType.KNOWLEDGE;
        }
    }

    /**
     * 验证任务类型是否有效
     *
     * @param taskType 任务类型
     * @param subject 学科类型
     * @return 是否有效
     */
    private boolean isValidTaskType(String taskType, String subject) {
        if (taskType == null) {
            return false;
        }

        // 基础验证：检查是否是已定义的任务类型
        boolean isValidType = taskType.equals(AiTaskType.GENERAL) ||
            taskType.equals(AiTaskType.KNOWLEDGE) ||
            taskType.equals(AiTaskType.ANIMATION) ||
            taskType.equals(AiTaskType.SOLVER_FIRST) ||
            taskType.equals(AiTaskType.PLANNER) ||
            taskType.equals(AiTaskType.HTML_MAKER) ||
            taskType.equals(AiTaskType.CIRCUIT_ANALYZE);

        if (!isValidType) {
            return false;
        }

        // 特殊规则验证
        if (KnowledgeSubject.GENERAL.equals(subject)) {
            // 通用学科不应该出现动画模式和电路分析模式
            return !taskType.equals(AiTaskType.ANIMATION) && 
                   !taskType.equals(AiTaskType.CIRCUIT_ANALYZE);
        }

        if (KnowledgeSubject.LINEAR_ALGEBRA.equals(subject)) {
            // 线性代数学科不应该出现电路分析模式
            return !taskType.equals(AiTaskType.CIRCUIT_ANALYZE);
        }

        if (KnowledgeSubject.ELECTRONIC.equals(subject)) {
            // 电子电路学科不应该出现动画模式
            return !taskType.equals(AiTaskType.ANIMATION);
        }

        return true;
    }
}
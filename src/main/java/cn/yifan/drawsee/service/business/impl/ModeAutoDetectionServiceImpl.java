package cn.yifan.drawsee.service.business.impl;

import cn.yifan.drawsee.constant.AiTaskType;
import cn.yifan.drawsee.constant.KnowledgeSubject;
import cn.yifan.drawsee.pojo.dto.CreateAiTaskDTO;
import cn.yifan.drawsee.service.business.ModeAutoDetectionService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import dev.langchain4j.model.chat.ChatLanguageModel;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;

/**
 * @FileName ModeAutoDetectionServiceImpl
 * @Description 自动识别模式服务实现类
 * @Author yifan
 * @date 2025-03-08 16:35
 **/
@Service
@Slf4j
public class ModeAutoDetectionServiceImpl implements ModeAutoDetectionService {

    @Autowired
    private ChatLanguageModel doubaoChatLanguageModel;
    
    @Autowired
    private ObjectMapper objectMapper;

    private static final String MODE_DETECTION_PROMPT = """
        你是一个专业的对话模式分类器。请根据用户的提问内容，判断最适合的对话模式。
        可选的模式有：
        1. GENERAL - 通用对话模式
        2. KNOWLEDGE - 知识详解模式，适用于概念解释、知识点讲解
        3. ANIMATION - 动画演示模式，适用于需要动态展示的内容
        4. SOLVER_FIRST - 问题求解模式，适用于需要步骤分解的计算题
        5. PLANNER - 学习规划模式，适用于制定学习计划
        6. HTML_MAKER - HTML生成模式，适用于网页内容生成
        7. CIRCUIT_ANALYZE - 电路分析模式，适用于电路相关问题

        用户提问：{{prompt}}
        学科：{{subject}}

        请只返回对应的模式名称，不要有任何多余的解释。
        """;

    /**
     * 根据提示词和学科自动识别合适的AI任务类型
     *
     * @param createAiTaskDTO AI任务创建DTO
     * @return 识别后的AI任务类型
     */
    @Override
    public String detectTaskType(CreateAiTaskDTO createAiTaskDTO) {
        try {
            String prompt = createAiTaskDTO.getPrompt().toLowerCase();
            String subject = createAiTaskDTO.getSubject();

            // 1. 首先进行快速规则匹配
            // 如果是电路学科，优先检查是否是电路分析任务
            if (KnowledgeSubject.ELECTRONIC.equals(subject)) {
                if (prompt.contains("分析") || prompt.contains("电路") || 
                    prompt.contains("analyze") || prompt.contains("circuit")) {
                    return AiTaskType.CIRCUIT_ANALYZE;
                }
            }

            // 2. 使用AI模型进行更细致的分类
            String detectionPrompt = MODE_DETECTION_PROMPT
                .replace("{{prompt}}", prompt)
                .replace("{{subject}}", subject);
            
            String detectedType = doubaoChatLanguageModel.chat(detectionPrompt).trim();
            
            // 3. 验证返回的类型是否有效
            if (isValidTaskType(detectedType)) {
                log.info("AI检测到的任务类型: {}", detectedType);
                return detectedType;
            }

            // 4. 如果AI返回的类型无效，使用启发式规则进行判断
            if (prompt.contains("知识点") || prompt.contains("概念") || 
                prompt.contains("knowledge") || prompt.contains("concept")) {
                return AiTaskType.KNOWLEDGE;
            }

            if (prompt.contains("动画") || prompt.contains("演示") || 
                prompt.contains("animation") || prompt.contains("demonstrate")) {
                return AiTaskType.ANIMATION;
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

            // 5. 默认返回通用对话类型
            return AiTaskType.GENERAL;
            
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
        return taskType != null && (
            taskType.equals(AiTaskType.GENERAL) ||
            taskType.equals(AiTaskType.KNOWLEDGE) ||
            taskType.equals(AiTaskType.ANIMATION) ||
            taskType.equals(AiTaskType.SOLVER_FIRST) ||
            taskType.equals(AiTaskType.PLANNER) ||
            taskType.equals(AiTaskType.HTML_MAKER) ||
            taskType.equals(AiTaskType.CIRCUIT_ANALYZE)
        );
    }
} 
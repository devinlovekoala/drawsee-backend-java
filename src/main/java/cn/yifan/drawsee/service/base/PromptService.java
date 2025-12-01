package cn.yifan.drawsee.service.base;

import cn.yifan.drawsee.annotation.PromptParam;
import cn.yifan.drawsee.annotation.PromptResource;
import cn.yifan.drawsee.pojo.entity.CircuitDesign;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * @FileName PromptService
 * @Description 
 * @Author yifan
 * @date 2025-03-09 23:19
 **/

@Service
public interface PromptService {

    @PromptResource(fromResource = "/prompt/general-chat.txt")
    String getGeneralChatPrompt();

    @PromptResource(fromResource = "/prompt/answer-point.txt")
    String getAnswerPointPrompt(@PromptParam("question") String question);

    @PromptResource(fromResource = "/prompt/answer-detail.txt")
    String getAnswerDetailPrompt(@PromptParam("question") String question, @PromptParam("angle") String angle);

    @PromptResource(fromResource = "/prompt/knowledge-detail-chat.txt")
    String getKnowledgeDetailChatPrompt(@PromptParam("knowledgePoint") String knowledgePoint);

    @PromptResource(fromResource = "/prompt/conv-title.txt")
    String getConvTitlePrompt(@PromptParam("question") String question);

    @PromptResource(fromResource = "/prompt/related-knowledge-points.txt")
    String getRelatedKnowledgePointsPrompt(
        @PromptParam("knowledgePoints") List<String> knowledgePoints,
        @PromptParam("question") String question
    );

    @PromptResource(fromResource = "/prompt/animation-shot-text-list.txt")
    String getAnimationShotTextListPrompt(@PromptParam("question") String question);

    @PromptResource(fromResource = "/prompt/animation-shot-code.txt")
    String getAnimationShotCodePrompt(
        @PromptParam("shotDescription") String shotDescription,
        @PromptParam("shotScript") String shotScript
    );

    @PromptResource(fromResource = "/prompt/animation-shot-merge-code.txt")
    String getAnimationShotMergeCodePrompt(@PromptParam("shotInfoListString") String shotInfoListString);

    @PromptResource(fromResource = "/prompt/animation-chat.txt")
    String getAnimationChatPrompt(@PromptParam("question") String question);

    @PromptResource(fromResource = "/prompt/image-text.txt")
    String getImageTextPrompt();

    @PromptResource(fromResource = "/prompt/circuit-image-to-design.txt")
    String getCircuitImageDesignPrompt();

    @PromptResource(fromResource = "/prompt/solve-ways.txt")
    String getSolveWaysPrompt(@PromptParam("question") String question);

    @PromptResource(fromResource = "/prompt/solver-first-chat.txt")
    String getSolverFirstChatPrompt(
        @PromptParam("question") String question,
        @PromptParam("method") String method
    );

    @PromptResource(fromResource = "/prompt/solver-continue-chat.txt")
    String getSolverContinueChatPrompt();

    @PromptResource(fromResource = "/prompt/solver-summary-chat.txt")
    String getSolverSummaryChatPrompt();

    @PromptResource(fromResource = "/prompt/planner-first.txt")
    String getPlannerFirstPrompt();

    @PromptResource(fromResource = "/prompt/planner-split.txt")
    String getPlannerSplitPrompt();

    @PromptResource(fromResource = "/prompt/html-maker-chat.txt")
    String getHtmlMakerChatPrompt(@PromptParam("question") String question);

    @PromptResource(fromResource = "/prompt/mode-detection.txt")
    String getModeDetectionPrompt(@PromptParam("prompt") String prompt);
    /**
     * 获取电路分析预热提示词模板
     * 生成电路简介与推荐追问
     */
    @PromptResource(fromResource = "/prompt/circuit-analysis.txt")
    String getCircuitWarmupPrompt(
        @PromptParam("design") CircuitDesign design,
        @PromptParam("spiceNetlist") String spiceNetlist
    );
    
    /**
     * 获取电路分析追问详情提示词模板
     * 用于响应指定追问
     */
    @PromptResource(fromResource = "/prompt/circuit-point-detail.txt")
    String getCircuitAnalyzeDetailPrompt(
        @PromptParam("design") CircuitDesign design,
        @PromptParam("spiceNetlist") String spiceNetlist,
        @PromptParam("contextTitle") String contextTitle,
        @PromptParam("contextText") String contextText,
        @PromptParam("followUp") String followUp
    );

    /**
     * 获取PDF电路实验任务分析点提示词模板
     * 用于解析PDF实验任务文档并生成分析点
     *
     * @param text PDF文档提取的文本内容
     * @return 提示词内容
     */
    @PromptResource(fromResource = "/prompt/pdf-circuit-point-analysis.txt")
    String getPdfCircuitPointAnalysisPrompt(@PromptParam("text") String text);

    /**
     * 获取PDF电路实验任务分析点详情提示词模板
     * 用于展开特定分析点的详细内容
     *
     * @param text PDF文档提取的文本内容
     * @param angle 分析角度
     * @return 提示词内容
     */
    @PromptResource(fromResource = "/prompt/pdf-circuit-point-detail.txt")
    String getPdfCircuitPointDetailPrompt(
        @PromptParam("text") String text,
        @PromptParam("angle") String angle
    );

    @PromptResource(fromResource = "/prompt/document-analysis-vision.txt")
    String getDocumentAnalysisVisionPrompt();
}

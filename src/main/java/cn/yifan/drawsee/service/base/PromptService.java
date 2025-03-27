package cn.yifan.drawsee.service.base;

import cn.yifan.drawsee.annotation.PromptParam;
import cn.yifan.drawsee.annotation.PromptResource;

import java.util.List;

/**
 * @FileName PromptService
 * @Description 
 * @Author yifan
 * @date 2025-03-09 23:19
 **/

public interface PromptService {

    @PromptResource(fromResource = "/prompt/general-chat.txt")
    String getGeneralChatPrompt();

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

}

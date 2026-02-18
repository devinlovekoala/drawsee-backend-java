package cn.yifan.drawsee.controller;

import cn.dev33.satoken.annotation.SaCheckLogin;
import cn.dev33.satoken.stp.StpUtil;
import cn.yifan.drawsee.constant.AiTaskType;
import cn.yifan.drawsee.pojo.dto.agentic.AgenticQueryRequest;
import cn.yifan.drawsee.service.base.AgenticRagService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Agentic RAG v2 控制器
 * 提供Agentic RAG查询接口，对接前端Flow系统
 *
 * @author Drawsee Team
 * @date 2025-12-16
 */
@Slf4j
@RestController
@RequestMapping("/api/agentic")
@ConditionalOnProperty(prefix = "drawsee.python-service", name = "enabled", havingValue = "true", matchIfMissing = true)
public class AgenticRagController {

    @Autowired
    private AgenticRagService agenticRagService;

    /**
     * Agentic RAG 查询接口（SSE流式）
     *
     * 该接口对接Python Agentic RAG v2系统，提供：
     * - 双层分类（输入类型 + 意图识别）
     * - 智能路由到对应处理频道
     * - SSE流式返回结果
     *
     * @param query            用户查询
     * @param knowledgeBaseIds 知识库ID列表（逗号分隔）
     * @param hasImage         是否包含图片
     * @param imageUrl         图片URL
     * @param classId          班级ID（可选）
     * @return SSE流式响应
     */
    @GetMapping("/query")
    @SaCheckLogin
    public SseEmitter agenticQuery(
            @RequestParam String query,
            @RequestParam String knowledgeBaseIds,
            @RequestParam(required = false, defaultValue = "false") Boolean hasImage,
            @RequestParam(required = false) String imageUrl,
            @RequestParam(required = false) String classId
    ) {
        Long userId = StpUtil.getLoginIdAsLong();

        log.info("[AgenticRAG] 收到查询请求: userId={}, query='{}', kb_ids={}, has_image={}",
                userId, query.substring(0, Math.min(50, query.length())), knowledgeBaseIds, hasImage);

        // 解析知识库ID列表
        List<String> kbIdList = List.of(knowledgeBaseIds.split(","));

        // 调用AgenticRagService（SSE流式）
        return agenticRagService.agenticQueryStream(
                query,
                kbIdList,
                hasImage,
                imageUrl,
                new HashMap<>(), // context
                userId,
                classId
        );
    }

    /**
     * POST方式的Agentic RAG查询
     *
     * 支持更复杂的请求体，包括上下文信息
     *
     * @param request  查询请求
     * @param classId  班级ID
     * @return SSE流式响应
     */
    @PostMapping("/query")
    @SaCheckLogin
    public SseEmitter agenticQueryPost(
            @RequestBody AgenticQueryRequest request,
            @RequestParam(required = false) String classId
    ) {
        Long userId = StpUtil.getLoginIdAsLong();

        log.info("[AgenticRAG] 收到POST查询请求: userId={}, query='{}', kb_count={}",
                userId,
                request.getQuery().substring(0, Math.min(50, request.getQuery().length())),
                request.getKnowledgeBaseIds() != null ? request.getKnowledgeBaseIds().size() : 0
        );

        return agenticRagService.agenticQueryStream(
                request.getQuery(),
                request.getKnowledgeBaseIds(),
                request.getHasImage(),
                request.getImageUrl(),
                request.getContext(),
                userId,
                classId
        );
    }

    /**
     * 获取Agentic RAG频道状态
     *
     * 返回当前可用和待实现的处理频道
     *
     * @param classId 班级ID
     * @return 频道状态信息
     */
    @GetMapping("/channels/status")
    @SaCheckLogin
    public Map<String, Object> getChannelsStatus(
            @RequestParam(required = false) String classId
    ) {
        Long userId = StpUtil.getLoginIdAsLong();

        log.info("[AgenticRAG] 获取频道状态: userId={}", userId);

        Map<String, Object> status = agenticRagService.getChannelsStatus(userId, classId);

        if (status != null) {
            return Map.of(
                    "success", true,
                    "data", status
            );
        } else {
            return Map.of(
                    "success", false,
                    "error", "获取频道状态失败"
            );
        }
    }

    /**
     * Agentic RAG 健康检查
     *
     * @return 健康状态
     */
    @GetMapping("/health")
    public Map<String, Object> healthCheck() {
        boolean healthy = agenticRagService.healthCheck();

        return Map.of(
                "success", healthy,
                "service", "Agentic RAG v2",
                "status", healthy ? "healthy" : "unhealthy",
                "timestamp", System.currentTimeMillis()
        );
    }

    /**
     * 获取支持的任务类型
     *
     * @return 任务类型列表
     */
    @GetMapping("/task-types")
    public Map<String, Object> getSupportedTaskTypes() {
        return Map.of(
                "success", true,
                "data", List.of(
                        Map.of(
                                "type", AiTaskType.AGENTIC_RAG,
                                "name", "Agentic RAG 通用查询",
                                "description", "自动识别输入类型和意图，智能路由到合适的处理频道"
                        ),
                        Map.of(
                                "type", AiTaskType.AGENTIC_RAG_FORMULA,
                                "name", "公式计算",
                                "description", "确定性公式计算，使用SymPy引擎，100%准确"
                        ),
                        Map.of(
                                "type", AiTaskType.AGENTIC_RAG_NETLIST,
                                "name", "Netlist解析",
                                "description", "SPICE Netlist形式化语言解析"
                        ),
                        Map.of(
                                "type", AiTaskType.AGENTIC_RAG_IMAGE,
                                "name", "电路图分析",
                                "description", "电路图图片识别和分析"
                        )
                )
        );
    }
}

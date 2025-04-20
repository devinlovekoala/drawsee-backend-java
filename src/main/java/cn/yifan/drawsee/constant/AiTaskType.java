package cn.yifan.drawsee.constant;

/**
 * AI任务类型常量
 * 
 * @author yifan
 * @date 2024/03/19
 */
public class AiTaskType {
    
    /**
     * 通用对话
     */
    public static final String GENERAL = "GENERAL";

    /**
     * 通用对话详情
     */
    public static final String GENERAL_DETAIL = "GENERAL_DETAIL";

    /**
     * 知识问答
     */
    public static final String KNOWLEDGE = "KNOWLEDGE";

    /**
     * 知识详解
     */
    public static final String KNOWLEDGE_DETAIL = "KNOWLEDGE_DETAIL";

    /**
     * 动画生成
     */
    public static final String ANIMATION = "ANIMATION";

    /**
     * 问题求解（第一步）
     */
    public static final String SOLVER_FIRST = "SOLVER_FIRST";

    /**
     * 问题求解（继续）
     */
    public static final String SOLVER_CONTINUE = "SOLVER_CONTINUE";

    /**
     * 问题求解（总结）
     */
    public static final String SOLVER_SUMMARY = "SOLVER_SUMMARY";

    /**
     * 学习规划
     */
    public static final String PLANNER = "PLANNER";

    /**
     * HTML生成
     */
    public static final String HTML_MAKER = "HTML_MAKER";
    
    /**
     * 电路分析
     */
    public static final String CIRCUIT_ANALYSIS = "CIRCUIT_ANALYSIS";

    private AiTaskType() {
        // 私有构造函数，防止实例化
    }
}
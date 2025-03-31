package cn.yifan.drawsee.constant;

/**
 * @FileName TaskStatus
 * @Description
 * @Author yifan
 * @date 2025-02-01 10:57
 **/

/**
 * AI任务状态常量
 */
public class AiTaskStatus {

    /**
     * 等待中
     */
    public static final String WAITING = "WAITING";

    /**
     * 处理中
     */
    public static final String PROCESSING = "PROCESSING";

    /**
     * 已完成
     */
    public static final String COMPLETED = "COMPLETED";

    /**
     * 失败
     */
    public static final String FAILED = "FAILED";

    /**
     * 待处理
     */
    public static final String PENDING = "PENDING";

    /**
     * 成功
     */
    public static final String SUCCESS = "SUCCESS";

    private AiTaskStatus() {
        // 私有构造函数，防止实例化
    }
}

package cn.yifan.drawsee.constant;

/**
 * @FileName DocumentProcessStatus
 * @Description 文档处理状态常量
 * @Author yifan
 * @date 2025-08-20 11:00
 **/
public class DocumentProcessStatus {

    /**
     * 待处理
     */
    public static final String PENDING = "PENDING";
    
    /**
     * 处理中
     */
    public static final String PROCESSING = "PROCESSING";
    
    /**
     * 已完成
     */
    public static final String COMPLETED = "COMPLETED";
    
    /**
     * 处理失败
     */
    public static final String FAILED = "FAILED";
    
    /**
     * 已取消
     */
    public static final String CANCELLED = "CANCELLED";
} 
package cn.yifan.drawsee.exception;

/**
 * 业务逻辑异常
 * 
 * @author devin
 * @date 2025-05-08
 */
public class BusinessException extends RuntimeException {
    
    private static final long serialVersionUID = 1L;
    
    /**
     * 错误码
     */
    private String code;
    
    /**
     * 错误消息
     */
    private String message;
    
    /**
     * 构造方法
     * 
     * @param message 错误消息
     */
    public BusinessException(String message) {
        super(message);
        this.message = message;
    }
    
    /**
     * 构造方法
     * 
     * @param code 错误码
     * @param message 错误消息
     */
    public BusinessException(String code, String message) {
        super(message);
        this.code = code;
        this.message = message;
    }
    
    /**
     * 构造方法
     * 
     * @param message 错误消息
     * @param cause 原因
     */
    public BusinessException(String message, Throwable cause) {
        super(message, cause);
        this.message = message;
    }
    
    /**
     * 构造方法
     * 
     * @param code 错误码
     * @param message 错误消息
     * @param cause 原因
     */
    public BusinessException(String code, String message, Throwable cause) {
        super(message, cause);
        this.code = code;
        this.message = message;
    }
    
    /**
     * 获取错误码
     * 
     * @return 错误码
     */
    public String getCode() {
        return code;
    }
    
    /**
     * 获取错误消息
     * 
     * @return 错误消息
     */
    @Override
    public String getMessage() {
        return message;
    }
}
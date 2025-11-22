package cn.yifan.drawsee.pojo.vo;

import lombok.Data;

/**
 * 统一API响应结果封装
 * 
 * @author yifan
 * @date 2025-05-08
 */
@Data
public class R<T> {
    
    /**
     * 状态码
     */
    private Integer code;
    
    /**
     * 消息
     */
    private String message;
    
    /**
     * 数据
     */
    private T data;
    
    /**
     * 时间戳
     */
    private long timestamp;
    
    /**
     * 成功状态码
     */
    public static final int SUCCESS_CODE = 0;
    
    /**
     * 错误状态码
     */
    public static final int ERROR_CODE = 500;
    
    /**
     * 构造方法
     */
    public R() {
        this.timestamp = System.currentTimeMillis();
    }
    
    /**
     * 构造方法
     * 
     * @param code 状态码
     * @param message 消息
     */
    public R(Integer code, String message) {
        this.code = code;
        this.message = message;
        this.timestamp = System.currentTimeMillis();
    }
    
    /**
     * 构造方法
     * 
     * @param code 状态码
     * @param message 消息
     * @param data 数据
     */
    public R(Integer code, String message, T data) {
        this.code = code;
        this.message = message;
        this.data = data;
        this.timestamp = System.currentTimeMillis();
    }
    
    /**
     * 成功
     * 
     * @return 响应对象
     */
    public static <T> R<T> ok() {
        return new R<>(SUCCESS_CODE, "操作成功");
    }
    
    /**
     * 成功
     * 
     * @param message 消息
     * @return 响应对象
     */
    public static <T> R<T> ok(String message) {
        return new R<>(SUCCESS_CODE, message);
    }
    
    /**
     * 成功
     * 
     * @param data 数据
     * @return 响应对象
     */
    public static <T> R<T> ok(T data) {
        return new R<>(SUCCESS_CODE, "操作成功", data);
    }
    
    /**
     * 成功
     * 
     * @param message 消息
     * @param data 数据
     * @return 响应对象
     */
    public static <T> R<T> ok(String message, T data) {
        return new R<>(SUCCESS_CODE, message, data);
    }
    
    /**
     * 失败
     * 
     * @return 响应对象
     */
    public static <T> R<T> error() {
        return new R<>(ERROR_CODE, "操作失败");
    }
    
    /**
     * 失败
     * 
     * @param message 消息
     * @return 响应对象
     */
    public static <T> R<T> error(String message) {
        return new R<>(ERROR_CODE, message);
    }
    
    /**
     * 失败
     * 
     * @param code 状态码
     * @param message 消息
     * @return 响应对象
     */
    public static <T> R<T> error(Integer code, String message) {
        return new R<>(code, message);
    }
    
    /**
     * 失败（与error同义）
     * 
     * @return 响应对象
     */
    public static <T> R<T> fail() {
        return new R<>(ERROR_CODE, "操作失败");
    }
    
    /**
     * 失败（与error同义）
     * 
     * @param message 消息
     * @return 响应对象
     */
    public static <T> R<T> fail(String message) {
        return new R<>(ERROR_CODE, message);
    }
    
    /**
     * 失败（与error同义）
     * 
     * @param code 状态码
     * @param message 消息
     * @return 响应对象
     */
    public static <T> R<T> fail(Integer code, String message) {
        return new R<>(code, message);
    }
} 
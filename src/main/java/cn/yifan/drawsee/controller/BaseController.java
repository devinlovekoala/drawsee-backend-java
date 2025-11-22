package cn.yifan.drawsee.controller;

import cn.yifan.drawsee.exception.ApiError;
import cn.yifan.drawsee.pojo.vo.ResponseVO;

/**
 * 基础控制器，提供通用的响应处理方法
 * 
 * @author yifan
 * @date 2025-05-08
 */
public class BaseController {
    
    /**
     * 返回成功响应
     * 
     * @param data 数据
     * @param <T> 数据类型
     * @return 响应VO
     */
    protected <T> ResponseVO<T> success(T data) {
        return new ResponseVO<>(0, "success", data);
    }
    
    /**
     * 返回成功响应（无数据）
     * 
     * @return 响应VO
     */
    protected <T> ResponseVO<T> success() {
        return new ResponseVO<>(0, "success", null);
    }
    
    /**
     * 返回失败响应
     * 
     * @param error 错误枚举
     * @param <T> 数据类型
     * @return 响应VO
     */
    protected <T> ResponseVO<T> fail(ApiError error) {
        return new ResponseVO<>(error.getCode(), error.getMessage(), null);
    }
    
    /**
     * 返回失败响应
     * 
     * @param code 错误码
     * @param message 错误信息
     * @param <T> 数据类型
     * @return 响应VO
     */
    protected <T> ResponseVO<T> fail(int code, String message) {
        return new ResponseVO<>(code, message, null);
    }
} 
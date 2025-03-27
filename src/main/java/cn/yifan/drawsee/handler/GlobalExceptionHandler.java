package cn.yifan.drawsee.handler;

import cn.dev33.satoken.exception.NotLoginException;
import cn.yifan.drawsee.exception.ApiError;
import cn.yifan.drawsee.exception.ApiException;
import cn.yifan.drawsee.pojo.Result;
import jakarta.validation.ConstraintViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * @FileName GlobalExceptionHandler
 * @Description
 * @Author yifan
 * @date 2025-01-28 20:37
 **/

@RestControllerAdvice
public class GlobalExceptionHandler {

    private ResponseEntity<Result<Object>> getErrorResponse(ApiError error) {
        Integer statusCode = error.getCode();
        String message = error.getMessage();
        return new ResponseEntity<>(
                Result.error(statusCode, message),
                HttpStatus.valueOf(statusCode)
        );
    }

    // 自定义异常
    @ExceptionHandler(ApiException.class)
    public ResponseEntity<Result<Object>> handleApiException(ApiException e) {
        return getErrorResponse(e.getError());
    }

    // 参数校验异常
    @ExceptionHandler(MethodArgumentNotValidException.class) // 方法上
    public ResponseEntity<Result<Object>> handleMethodArgumentNotValidException(MethodArgumentNotValidException e) {
        return getErrorResponse(ApiError.PARAM_ERROR);
    }
    @ExceptionHandler(ConstraintViolationException.class) // 类上
    public ResponseEntity<Result<Object>> handleConstraintViolationException(ConstraintViolationException e) {
        return getErrorResponse(ApiError.PARAM_ERROR);
    }

    // 权限校验异常
    @ExceptionHandler(NotLoginException.class)
    public ResponseEntity<Result<Object>> handleNotLoginException(NotLoginException e) {
        return getErrorResponse(ApiError.NOT_LOGIN);
    }

    // 其他异常
    @ExceptionHandler(Exception.class)
    public ResponseEntity<Result<Object>> handleException(Exception e) {
        e.printStackTrace();
        return getErrorResponse(ApiError.SYSTEM_ERROR);
    }

}

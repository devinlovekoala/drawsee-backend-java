package cn.yifan.drawsee.exception;

import cn.dev33.satoken.exception.NotLoginException;
import cn.yifan.drawsee.pojo.Result;
import cn.yifan.drawsee.pojo.vo.R;
import jakarta.validation.ConstraintViolationException;
import java.io.IOException;
import java.sql.SQLException;
import lombok.extern.slf4j.Slf4j;
import org.apache.catalina.connector.ClientAbortException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.BindException;
import org.springframework.validation.BindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.context.request.async.AsyncRequestNotUsableException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

/**
 * 全局异常处理器
 *
 * @author devin
 * @date 2025-05-08
 */
@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

  /** 业务异常处理 */
  @ExceptionHandler(BusinessException.class)
  @ResponseStatus(HttpStatus.BAD_REQUEST)
  public R<String> handleBusinessException(BusinessException e) {
    log.error("业务异常: {}", e.getMessage(), e);
    if (e.getCode() != null) {
      return R.error(Integer.parseInt(e.getCode()), e.getMessage());
    }
    return R.error(e.getMessage());
  }

  /** API异常处理 */
  @ExceptionHandler(ApiException.class)
  public ResponseEntity<Result<Object>> handleApiException(ApiException e) {
    log.error("API异常: {}", e.getMessage(), e);
    return getErrorResponse(e.getError());
  }

  /** Sa-Token未登录异常处理 */
  @ExceptionHandler(NotLoginException.class)
  public ResponseEntity<Result<Object>> handleNotLoginException(NotLoginException e) {
    log.warn("未登录异常: {}", e.getMessage());
    return getErrorResponse(ApiError.NOT_LOGIN);
  }

  /** 约束违反异常处理 */
  @ExceptionHandler(ConstraintViolationException.class)
  @ResponseStatus(HttpStatus.BAD_REQUEST)
  public ResponseEntity<Result<Object>> handleConstraintViolationException(
      ConstraintViolationException e) {
    log.warn("约束违反异常: {}", e.getMessage());
    return getErrorResponse(ApiError.PARAM_ERROR);
  }

  /** REST客户端异常处理 */
  @ExceptionHandler(RestClientException.class)
  @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
  public R<String> handleRestClientException(RestClientException e) {
    log.error("REST客户端异常: {}", e.getMessage(), e);
    return R.error("调用外部服务失败: " + e.getMessage());
  }

  /** 资源访问异常处理 */
  @ExceptionHandler(ResourceAccessException.class)
  @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
  public R<String> handleResourceAccessException(ResourceAccessException e) {
    log.error("资源访问异常: {}", e.getMessage(), e);
    return R.error("无法连接外部服务: " + e.getMessage());
  }

  /** 参数校验异常处理 */
  @ExceptionHandler(MethodArgumentNotValidException.class)
  @ResponseStatus(HttpStatus.BAD_REQUEST)
  public R<String> handleMethodArgumentNotValidException(MethodArgumentNotValidException e) {
    BindingResult bindingResult = e.getBindingResult();
    StringBuilder sb = new StringBuilder("参数校验失败: ");
    for (FieldError fieldError : bindingResult.getFieldErrors()) {
      sb.append(fieldError.getField())
          .append(": ")
          .append(fieldError.getDefaultMessage())
          .append(", ");
    }
    String msg = sb.toString();
    log.warn("参数校验异常: {}", msg);
    return R.error(msg);
  }

  /** 参数绑定异常处理 */
  @ExceptionHandler(BindException.class)
  @ResponseStatus(HttpStatus.BAD_REQUEST)
  public R<String> handleBindException(BindException e) {
    BindingResult bindingResult = e.getBindingResult();
    StringBuilder sb = new StringBuilder("参数绑定失败: ");
    for (FieldError fieldError : bindingResult.getFieldErrors()) {
      sb.append(fieldError.getField())
          .append(": ")
          .append(fieldError.getDefaultMessage())
          .append(", ");
    }
    String msg = sb.toString();
    log.warn("参数绑定异常: {}", msg);
    return R.error(msg);
  }

  /** 参数类型不匹配异常处理 */
  @ExceptionHandler(MethodArgumentTypeMismatchException.class)
  @ResponseStatus(HttpStatus.BAD_REQUEST)
  public R<String> handleMethodArgumentTypeMismatchException(
      MethodArgumentTypeMismatchException e) {
    log.warn("参数类型不匹配: {}", e.getMessage());
    Class<?> requiredType = e.getRequiredType();
    String requiredTypeName = requiredType != null ? requiredType.getName() : "未知类型";
    return R.error("参数类型不匹配: " + e.getName() + "应为" + requiredTypeName + "类型");
  }

  /** 上传文件大小超出限制处理 */
  @ExceptionHandler(MaxUploadSizeExceededException.class)
  @ResponseStatus(HttpStatus.BAD_REQUEST)
  public R<String> handleMaxUploadSizeExceededException(MaxUploadSizeExceededException e) {
    log.warn("上传文件大小超出限制: {}", e.getMessage());
    return R.error("上传文件大小超出限制");
  }

  /** SQL异常处理 */
  @ExceptionHandler(SQLException.class)
  @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
  public R<String> handleSQLException(SQLException e) {
    log.error("SQL异常: {}", e.getMessage(), e);
    return R.error("数据库操作异常");
  }

  /** IO异常处理 */
  @ExceptionHandler(IOException.class)
  @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
  public R<String> handleIOException(IOException e) {
    if (isClientAbort(e)) {
      log.warn("客户端连接已断开，忽略IO异常: {}", e.getMessage());
      return null;
    }
    log.error("IO异常: {}", e.getMessage(), e);
    return R.error("IO操作异常");
  }

  /** SSE/流式连接断开处理 */
  @ExceptionHandler({AsyncRequestNotUsableException.class, ClientAbortException.class})
  @ResponseStatus(HttpStatus.OK)
  public void handleClientAbort(Exception e) {
    log.warn("客户端连接断开: {}", e.getMessage());
  }

  /** 全局异常处理 */
  @ExceptionHandler(Exception.class)
  @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
  public R<String> handleException(Exception e) {
    log.error("系统异常: {}", e.getMessage(), e);
    return R.error("系统异常，请联系管理员");
  }

  /** 构建错误响应 */
  private ResponseEntity<Result<Object>> getErrorResponse(ApiError error) {
    Integer statusCode = error.getCode();
    String message = error.getMessage();

    // 判断状态码是否在HTTP状态码范围内(100-599)
    HttpStatus httpStatus;
    if (statusCode >= 100 && statusCode < 600) {
      httpStatus = HttpStatus.valueOf(statusCode);
    } else {
      // 超出HTTP状态码范围的错误码，统一用400表示客户端错误
      httpStatus = HttpStatus.BAD_REQUEST;
    }

    return new ResponseEntity<>(Result.error(statusCode, message), httpStatus);
  }

  private boolean isClientAbort(IOException e) {
    if (e instanceof ClientAbortException) {
      return true;
    }
    Throwable cause = e.getCause();
    if (cause instanceof ClientAbortException) {
      return true;
    }
    String message = e.getMessage();
    return message != null && (message.contains("断开的管道") || message.contains("Broken pipe"));
  }
}

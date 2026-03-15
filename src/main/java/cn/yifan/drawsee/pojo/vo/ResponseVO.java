package cn.yifan.drawsee.pojo.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 通用响应VO
 *
 * @author yifan
 * @date 2025-05-08
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ResponseVO<T> {

  /** 状态码（0表示成功） */
  private Integer code;

  /** 响应消息 */
  private String message;

  /** 响应数据 */
  private T data;

  /**
   * 创建成功响应
   *
   * @param data 数据
   * @param <T> 数据类型
   * @return 响应VO
   */
  public static <T> ResponseVO<T> success(T data) {
    return new ResponseVO<>(0, "success", data);
  }

  /**
   * 创建失败响应
   *
   * @param code 错误码
   * @param message 错误信息
   * @param <T> 数据类型
   * @return 响应VO
   */
  public static <T> ResponseVO<T> fail(Integer code, String message) {
    return new ResponseVO<>(code, message, null);
  }
}

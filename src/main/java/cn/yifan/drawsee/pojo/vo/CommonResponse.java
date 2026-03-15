package cn.yifan.drawsee.pojo.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * @FileName CommonResponse @Description 通用响应对象 @Author yifan
 *
 * @date 2025-04-13 15:10
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class CommonResponse<T> {
  /** 状态码 */
  private Integer code;

  /** 消息 */
  private String message;

  /** 数据 */
  private T data;

  /**
   * 成功响应
   *
   * @param data 数据
   * @param <T> 数据类型
   * @return 成功响应
   */
  public static <T> CommonResponse<T> success(T data) {
    return new CommonResponse<>(200, "操作成功", data);
  }

  /**
   * 成功响应
   *
   * @param message 消息
   * @param data 数据
   * @param <T> 数据类型
   * @return 成功响应
   */
  public static <T> CommonResponse<T> success(String message, T data) {
    return new CommonResponse<>(200, message, data);
  }

  /**
   * 错误响应
   *
   * @param code 状态码
   * @param message 消息
   * @param <T> 数据类型
   * @return 错误响应
   */
  public static <T> CommonResponse<T> error(Integer code, String message) {
    return new CommonResponse<>(code, message, null);
  }
}

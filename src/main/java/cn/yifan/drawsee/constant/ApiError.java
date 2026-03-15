package cn.yifan.drawsee.constant;

import lombok.Getter;

/** API错误枚举 */
@Getter
public enum ApiError {

  /** 参数错误 */
  PARAM_ERROR(400, "参数错误"),

  /** 未授权 */
  UNAUTHORIZED(401, "未授权"),

  /** 系统错误 */
  SYSTEM_ERROR(500, "系统错误"),

  /** 每日限制已达到 */
  DAILY_LIMIT_EXCEEDED(429, "已达到每日对话次数限制");

  private final int code;
  private final String message;

  ApiError(int code, String message) {
    this.code = code;
    this.message = message;
  }
}

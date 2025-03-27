package cn.yifan.drawsee.pojo;

import lombok.Data;

/**
 * @FileName Result
 * @Description
 * @Author yifan
 * @date 2025-01-28 15:19
 **/

@Data
public class Result <T> {

    private Integer code;

    private String message;

    private T data;

    private Long timestamp;

    public Result() {
        this.timestamp = System.currentTimeMillis();
    }

    public static <T> Result<T> success(T data) {
        Result<T> result = new Result<>();
        result.setCode(200);
        result.setMessage("success");
        result.setData(data);
        return result;
    }

    public static Result<Object> error(Integer code, String message) {
        Result<Object> result = new Result<>();
        result.setCode(code);
        result.setMessage(message);
        result.setData(null);
        return result;
    }

}

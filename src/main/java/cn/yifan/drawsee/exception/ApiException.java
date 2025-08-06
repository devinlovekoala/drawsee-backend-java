package cn.yifan.drawsee.exception;

import lombok.Getter;

/**
 * @FileName ApiException
 * @Description
 * @Author yifan
 * @date 2025-01-28 20:34
 **/

@Getter
public class ApiException extends RuntimeException {

    private ApiError error;
    private String message;

    public ApiException(ApiError error) {
        this.error = error;
        this.message = error.getMessage();
    }
    
    public ApiException(ApiError error, String message) {
        this.error = error;
        this.message = message;
    }
    
    @Override
    public String getMessage() {
        return message;
    }
}

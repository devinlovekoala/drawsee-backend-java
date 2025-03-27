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

    public ApiException(ApiError error) {
        this.error = error;
    }

}

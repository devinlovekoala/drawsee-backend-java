package cn.yifan.drawsee.handler;

import cn.yifan.drawsee.pojo.Result;
import org.springframework.core.MethodParameter;
import org.springframework.http.MediaType;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyAdvice;

/**
 * @FileName GlobalResponseHandler
 * @Description
 * @Author yifan
 * @date 2025-01-28 20:58
 **/

@RestControllerAdvice
public class GlobalResponseHandler implements ResponseBodyAdvice<Object> {

    /*
    Controller返回后，以及ExceptionHandler返回后都会来这里
    如果 beforeBodyWrite 方法返回 null，Spring Boot 会返回一个空的 ResponseEntity，状态码为 204 No Content。
    如果 beforeBodyWrite 方法返回一个非 ResponseEntity 的对象，Spring Boot 会将这个对象作为响应体，并使用默认的状态码 200 OK 创建一个 ResponseEntity。
    如果 beforeBodyWrite 方法返回一个 ResponseEntity，Spring Boot 会直接使用这个 ResponseEntity 作为最终的响应。
    */
    @Override
    public Object beforeBodyWrite(Object body, MethodParameter returnType, MediaType selectedContentType, Class<? extends HttpMessageConverter<?>> selectedConverterType, ServerHttpRequest request, ServerHttpResponse response) {
        // 如果是 Result，直接返回
        if (body instanceof Result) {
            return body;
        }
        return Result.success(body);
    }

    // 决定哪些方法要经过 beforeBodyWrite
    @Override
    public boolean supports(MethodParameter returnType, Class<? extends HttpMessageConverter<?>> converterType) {
        return true;
    }
}

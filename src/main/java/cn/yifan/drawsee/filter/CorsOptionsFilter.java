package cn.yifan.drawsee.filter;

import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;

/**
 * @FileName CorsOptionsFilter
 * @Description 专门处理OPTIONS预检请求的过滤器
 * @Author yifan
 * @date 2025-05-15 14:30
 **/

@Component
@Order(Ordered.HIGHEST_PRECEDENCE) // 确保此过滤器最先执行
public class CorsOptionsFilter implements Filter {

    // 允许的域名列表，需要与CorsConfig保持一致
    private static final List<String> ALLOWED_ORIGINS = Arrays.asList(
            "http://localhost:3000",  // React默认端口
            "http://localhost:5173",  // Vite默认端口
            "http://localhost:6868",  // 课程服务端口
            "http://127.0.0.1:3000",
            "http://127.0.0.1:5173",
            "http://127.0.0.1:6868",
            "http://42.193.107.127:6868",
            "http://42.193.107.127:3000",
            "http://42.193.107.127:5173",
            "http://drawsee.cn",
            "https://drawsee.cn",
            "http://admin.drawsee.cn",
            "https://admin.drawsee.cn"
    );

    @Override
    public void doFilter(ServletRequest req, ServletResponse res, FilterChain chain) 
            throws IOException, ServletException {
        
        HttpServletRequest request = (HttpServletRequest) req;
        HttpServletResponse response = (HttpServletResponse) res;
        
        String origin = request.getHeader("Origin");
        
        // 对OPTIONS请求直接返回200，并设置必要的CORS头
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            // 只允许配置的源
            if (origin != null && ALLOWED_ORIGINS.contains(origin)) {
                response.setHeader("Access-Control-Allow-Origin", origin);
                response.setHeader("Access-Control-Allow-Methods", "GET, POST, PUT, DELETE, OPTIONS");
                response.setHeader("Access-Control-Allow-Headers", "Authorization, Content-Type, X-Requested-With");
                response.setHeader("Access-Control-Expose-Headers", "Authorization");
                response.setHeader("Access-Control-Max-Age", "3600");
                response.setHeader("Access-Control-Allow-Credentials", "true");
                response.setStatus(HttpServletResponse.SC_OK);
            }
            return;
        }
        
        chain.doFilter(req, res);
    }

    @Override
    public void init(FilterConfig filterConfig) {}

    @Override
    public void destroy() {}
} 
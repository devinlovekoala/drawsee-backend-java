package cn.yifan.drawsee.filter;

import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * @FileName CorsOptionsFilter @Description 专门处理OPTIONS预检请求的过滤器 @Author yifan
 *
 * @date 2025-05-15 14:30
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE) // 确保此过滤器最先执行
public class CorsOptionsFilter implements Filter {

  /** 检查origin是否被允许 支持与CorsConfig一致的通配符模式 */
  private boolean isOriginAllowed(String origin) {
    if (origin == null) {
      return false;
    }

    // 本地开发环境 - 支持所有端口
    if (origin.startsWith("http://localhost:") || origin.startsWith("http://127.0.0.1:")) {
      return true;
    }

    // 允许的公网IP
    if (origin.startsWith("http://42.193.107.127:")
        || origin.startsWith("https://42.193.107.127:")) {
      return true;
    }

    // 正式域名
    List<String> allowedDomains =
        Arrays.asList(
            "http://drawsee.cn",
            "https://drawsee.cn",
            "http://admin.drawsee.cn",
            "https://admin.drawsee.cn");

    // 精确匹配域名
    if (allowedDomains.contains(origin)) {
      return true;
    }

    // 支持 *.drawsee.cn 子域名
    if (origin.matches("^https?://[a-zA-Z0-9-]+\\.drawsee\\.cn$")) {
      return true;
    }

    return false;
  }

  @Override
  public void doFilter(ServletRequest req, ServletResponse res, FilterChain chain)
      throws IOException, ServletException {

    HttpServletRequest request = (HttpServletRequest) req;
    HttpServletResponse response = (HttpServletResponse) res;

    String origin = request.getHeader("Origin");

    // 对OPTIONS请求直接返回200，并设置必要的CORS头
    if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
      // 检查origin是否被允许
      if (isOriginAllowed(origin)) {
        response.setHeader("Access-Control-Allow-Origin", origin);
        response.setHeader("Access-Control-Allow-Methods", "GET, POST, PUT, DELETE, OPTIONS");
        response.setHeader(
            "Access-Control-Allow-Headers", "Authorization, Content-Type, X-Requested-With");
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

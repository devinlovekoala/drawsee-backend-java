package cn.yifan.drawsee.util;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import javax.crypto.SecretKey;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * 内部服务JWT工具类 用于Java与Python微服务之间的认证
 *
 * @author Drawsee Team
 */
@Component
public class InternalJwtUtil {

  @Value("${drawsee.internal-jwt.secret}")
  private String jwtSecret;

  @Value("${drawsee.internal-jwt.expiration:3600}")
  private Long expiration;

  /** 获取签名密钥 */
  private SecretKey getSigningKey() {
    return Keys.hmacShaKeyFor(jwtSecret.getBytes(StandardCharsets.UTF_8));
  }

  /**
   * 生成内部服务JWT Token
   *
   * @param userId 用户ID
   * @param classId 班级ID
   * @param knowledgeBaseId 知识库ID（可选）
   * @return JWT Token
   */
  public String generateToken(Long userId, String classId, String knowledgeBaseId) {
    Map<String, Object> claims = new HashMap<>();
    claims.put("user_id", userId);
    claims.put("class_id", classId);
    if (knowledgeBaseId != null) {
      claims.put("knowledge_base_id", knowledgeBaseId);
    }
    claims.put("issued_at", System.currentTimeMillis());

    return Jwts.builder()
        .setClaims(claims)
        .setSubject(String.valueOf(userId))
        .setIssuedAt(new Date())
        .setExpiration(new Date(System.currentTimeMillis() + expiration * 1000))
        .signWith(getSigningKey())
        .compact();
  }

  /**
   * 验证JWT Token（用于测试）
   *
   * @param token JWT Token
   * @return Claims
   */
  public Claims parseToken(String token) {
    return Jwts.parserBuilder()
        .setSigningKey(getSigningKey())
        .build()
        .parseClaimsJws(token)
        .getBody();
  }

  /**
   * 检查Token是否过期
   *
   * @param token JWT Token
   * @return 是否过期
   */
  public boolean isTokenExpired(String token) {
    try {
      Claims claims = parseToken(token);
      return claims.getExpiration().before(new Date());
    } catch (Exception e) {
      return true;
    }
  }
}

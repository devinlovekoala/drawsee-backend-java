package cn.yifan.drawsee.util;

import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

/**
 * 密码工具类，提供加密和验证功能
 * @author devin
 * @date 2025-04-22
 */
public class PasswordUtil {

    private static final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();

    /**
     * 对密码进行BCrypt加密
     * 
     * @param rawPassword 原始密码
     * @return 加密后的密码
     */
    public static String encode(String rawPassword) {
        return encoder.encode(rawPassword);
    }

    /**
     * 验证密码是否匹配
     * 
     * @param rawPassword 原始密码（明文）
     * @param encodedPassword 加密后的密码
     * @return 是否匹配
     */
    public static boolean matches(String rawPassword, String encodedPassword) {
        return encoder.matches(rawPassword, encodedPassword);
    }
}

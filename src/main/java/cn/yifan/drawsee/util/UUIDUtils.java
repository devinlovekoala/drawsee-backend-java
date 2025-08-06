package cn.yifan.drawsee.util;

import java.util.UUID;

/**
 * UUID工具类
 * 
 * @author devin
 * @date 2025-03-28 17:00
 */
public class UUIDUtils {
    
    /**
     * 生成32位UUID
     * 
     * @return 32位UUID字符串（不带连字符）
     */
    public static String generateUUID() {
        return UUID.randomUUID().toString().replace("-", "");
    }
    
    /**
     * 生成带连字符的UUID
     * 
     * @return 36位UUID字符串（带连字符）
     */
    public static String generateUUIDWithHyphen() {
        return UUID.randomUUID().toString();
    }
    
    /**
     * 生成指定长度的UUID
     * 如果指定长度小于32，则截取原UUID的前N位
     * 如果指定长度大于32，则填充0
     * 
     * @param length 指定长度
     * @return 指定长度的UUID字符串
     */
    public static String generateUUID(int length) {
        String uuid = generateUUID();
        if (length <= 0) {
            return "";
        }
        
        if (length >= 32) {
            StringBuilder sb = new StringBuilder(uuid);
            for (int i = 32; i < length; i++) {
                sb.append("0");
            }
            return sb.toString();
        }
        
        return uuid.substring(0, length);
    }
} 
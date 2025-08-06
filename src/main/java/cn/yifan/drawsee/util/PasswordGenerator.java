package cn.yifan.drawsee.util;

import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

/**
 * 密码生成器工具类
 * 用于生成BCrypt加密的密码哈希，通常在系统初始化和密码迁移时使用
 * 
 * @author yifan
 * @date 2025-04-22
 */
public class PasswordGenerator {

    private static final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();
    
    /**
     * 主方法，用于生成密码的BCrypt哈希值
     * @param args 命令行参数，可以传入要加密的密码
     */
    public static void main(String[] args) {
        if (args.length < 1) {
            System.out.println("用法: java -cp ... cn.yifan.drawsee.util.PasswordGenerator <password>");
            System.out.println("默认测试密码：");
            printEncodedPassword("funstack20250328"); // 管理员密码
            printEncodedPassword("teacher123");      // 教师密码
            printEncodedPassword("student123");      // 学生密码
            return;
        }
        
        printEncodedPassword(args[0]);
    }
    
    /**
     * 打印加密后的密码
     * @param rawPassword 原始密码
     */
    private static void printEncodedPassword(String rawPassword) {
        String encoded = encoder.encode(rawPassword);
        System.out.println("原始密码: " + rawPassword);
        System.out.println("加密后: " + encoded);
        System.out.println("验证结果: " + encoder.matches(rawPassword, encoded));
        System.out.println("----------------------------");
    }
} 
package cn.yifan.drawsee.util;

import cn.yifan.drawsee.constant.RedisKey;
import org.redisson.api.RAtomicLong;
import org.redisson.api.RedissonClient;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;

/**
 * @FileName RedisUtils
 * @Description
 * @Author yifan
 * @date 2025-03-26 13:01
 **/

public class RedisUtils {

    public static RAtomicLong getUseAiCounter(RedissonClient redissonClient, Long userId) {
        String key = RedisKey.USE_AI_COUNT_PREFIX + userId;
        RAtomicLong counter = redissonClient.getAtomicLong(key);
        // 原子操作：如果计数器不存在则初始化并设置过期时间
        if (!counter.isExists()) {
            // 设置过期时间为今日的23:59:59，并且为北京时间

            // 获取当前北京时间
            LocalDateTime now = LocalDateTime.now(ZoneId.of("Asia/Shanghai"));
            // 获取当天23:59:59的时间
            LocalDateTime endOfDay = now.with(LocalTime.MAX);
            // 计算当前时间到当天结束的时长
            Duration duration = Duration.between(now, endOfDay);
            // 将时长转换为秒数
            long ttl = duration.getSeconds();
            // 设置Redis计数器的过期时间
            counter.expire(Duration.ofSeconds(ttl));
        }
        return counter;
    }

}

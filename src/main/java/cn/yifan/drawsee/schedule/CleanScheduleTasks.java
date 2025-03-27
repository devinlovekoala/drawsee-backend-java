package cn.yifan.drawsee.schedule;

import cn.yifan.drawsee.constant.RedisKey;
import lombok.extern.slf4j.Slf4j;

import org.redisson.api.RQueue;
import org.redisson.api.RStream;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * @FileName CleanScheduleTask
 * @Description
 * @Author yifan
 * @date 2025-03-26 13:55
 **/

@Component
@Slf4j
public class CleanScheduleTasks {

    @Autowired
    private RedissonClient redissonClient;

    // 每个小时执行一次
    @Scheduled(cron = "0 0 * * * ?")
    public void cleanAiTasksInRedis() {
        log.info("定时清理Redis中的AI任务");
        RQueue<Long> aiTaskIds = redissonClient.getQueue(RedisKey.CLEAN_AI_TASK_QUEUE_KEY);
        for (Long taskId : aiTaskIds) {
            RStream<Object, Object> redisStream = redissonClient.getStream(RedisKey.AI_TASK_PREFIX + taskId);
            if (redisStream.isExists()) {
                redisStream.delete();
            }
        }
    }

}

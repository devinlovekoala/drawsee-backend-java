package cn.yifan.drawsee.mapper;

import cn.yifan.drawsee.pojo.entity.AiTask;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.sql.Timestamp;
import java.util.List;
import java.util.Map;

/**
 * @FileName TaskMapper
 * @Description
 * @Author yifan
 * @date 2025-01-29 17:08
 **/

@Mapper
public interface AiTaskMapper {

    AiTask getById(Long id);

    List<AiTask> getByUserIdAndConvIdAndStatus(Long userId, Long convId, String status);

    void insert(AiTask aiTask);

    void update(AiTask aiTask);
    
    // 获取总AI任务数
    Long countTotalAiTasks();
    
    // 获取不同类型任务的分布
    List<Map<String, Object>> countTaskTypeDistribution();
    
    // 获取任务平均耗时
    Long calculateAverageTaskDuration();
    
    // 获取总Token消耗量
    Long sumTotalTokensConsumed();
    
    // 获取每日Token消耗统计
    List<Map<String, Object>> countDailyTokenConsumption(@Param("days") int days);
    
    // 获取每周Token消耗统计
    List<Map<String, Object>> countWeeklyTokenConsumption(@Param("weeks") int weeks);
    
    // 获取每月Token消耗统计
    List<Map<String, Object>> countMonthlyTokenConsumption(@Param("months") int months);
    
    // 获取使用过AI任务的用户数量
    Long countUsersWithAiTasks();
    
    // 获取系统每日访问量统计（基于AI任务创建记录）
    List<Map<String, Object>> countDailySystemVisits(@Param("days") int days);
    
    // 获取系统每周访问量统计
    List<Map<String, Object>> countWeeklySystemVisits(@Param("weeks") int weeks);
    
    // 获取系统每月访问量统计
    List<Map<String, Object>> countMonthlySystemVisits(@Param("months") int months);
}
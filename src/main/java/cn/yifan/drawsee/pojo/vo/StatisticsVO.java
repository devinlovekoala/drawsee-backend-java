package cn.yifan.drawsee.pojo.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.List;
import java.util.Map;

/**
 * @FileName StatisticsVO
 * @Description 统计数据VO
 * @Author yifan
 * @date 2025-03-26 10:00
 **/

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StatisticsVO implements Serializable {

    private static final long serialVersionUID = 1L;

    // 用户统计
    private UserStats userStats;

    // 会话统计
    private ConversationStats conversationStats;

    // AI任务统计
    private AiTaskStats aiTaskStats;

    // 综合统计
    private SystemStats systemStats;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class UserStats {
        // 总用户数
        private Long totalUsers;
        
        // 新增用户趋势 - 按天统计
        private List<DailyStats> dailyNewUsers;
        
        // 新增用户趋势 - 按周统计
        private List<WeeklyStats> weeklyNewUsers;
        
        // 新增用户趋势 - 按月统计
        private List<MonthlyStats> monthlyNewUsers;
        
        // 最近7天活跃用户数
        private Long activeUsersLast7Days;
        
        // 最近30天活跃用户数
        private Long activeUsersLast30Days;
        
        // 次日留存率
        private Double retentionRateNextDay;
        
        // 7日留存率
        private Double retentionRate7Days;
        
        // 30日留存率
        private Double retentionRate30Days;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ConversationStats {
        // 总会话数
        private Long totalConversations;
        
        // 会话创建趋势 - 按天统计
        private List<DailyStats> dailyNewConversations;
        
        // 会话创建趋势 - 按周统计
        private List<WeeklyStats> weeklyNewConversations;
        
        // 会话创建趋势 - 按月统计
        private List<MonthlyStats> monthlyNewConversations;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AiTaskStats {
        // 总AI任务数
        private Long totalAiTasks;
        
        // 任务类型分布
        private Map<String, Long> taskTypeDistribution;
        
        // 任务平均耗时(毫秒)
        private Long averageTaskDuration;
        
        // 总Token消耗量
        private Long totalTokensConsumed;
        
        // Token消耗趋势 - 按天统计
        private List<DailyStats> dailyTokenConsumption;
        
        // Token消耗趋势 - 按周统计
        private List<WeeklyStats> weeklyTokenConsumption;
        
        // Token消耗趋势 - 按月统计
        private List<MonthlyStats> monthlyTokenConsumption;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SystemStats {
        // 系统访问量趋势 - 按天统计
        private List<DailyStats> dailySystemVisits;
        
        // 系统访问量趋势 - 按周统计
        private List<WeeklyStats> weeklySystemVisits;
        
        // 系统访问量趋势 - 按月统计
        private List<MonthlyStats> monthlySystemVisits;
        
        // 用户行为漏斗 - 注册用户数
        private Long registeredUsers;
        
        // 用户行为漏斗 - 创建会话的用户数
        private Long usersWithConversations;
        
        // 用户行为漏斗 - 使用AI任务的用户数
        private Long usersWithAiTasks;
        
        // 转化率 - 注册到创建会话
        private Double conversionRateRegToConv;
        
        // 转化率 - 创建会话到使用AI任务
        private Double conversionRateConvToAi;
        
        // 转化率 - 注册到使用AI任务
        private Double conversionRateRegToAi;
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DailyStats {
        private String date; // 格式: YYYY-MM-DD
        private Long value;
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class WeeklyStats {
        private String weekStart; // 周开始日期，格式: YYYY-MM-DD
        private String weekEnd;   // 周结束日期，格式: YYYY-MM-DD
        private Long value;
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class MonthlyStats {
        private String yearMonth; // 格式: YYYY-MM
        private Long value;
    }
} 
package cn.yifan.drawsee.service.business;

import cn.yifan.drawsee.constant.RedisKey;
import cn.yifan.drawsee.mapper.AiTaskMapper;
import cn.yifan.drawsee.mapper.ConversationMapper;
import cn.yifan.drawsee.mapper.UserMapper;
import cn.yifan.drawsee.pojo.vo.StatisticsVO;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

/**
 * @FileName StatisticsService @Description 统计数据服务 @Author yifan
 *
 * @date 2025-03-26 10:00
 */
@Service
@Slf4j
public class StatisticsService {

  @Autowired private UserMapper userMapper;

  @Autowired private ConversationMapper conversationMapper;

  @Autowired private AiTaskMapper aiTaskMapper;

  /** 获取统计数据 使用Spring缓存，缓存1小时 */
  @Cacheable(
      value = RedisKey.DASHBOARD_STATISTICS_KEY, // 指定缓存名称，对应CacheConfig中配置的缓存
      unless = "#result == null" // 当返回值为null时不缓存
      )
  public StatisticsVO getStatistics() {
    log.info("开始获取统计数据");

    try {
      // 异步获取用户统计
      CompletableFuture<StatisticsVO.UserStats> userStatsFuture =
          CompletableFuture.supplyAsync(this::getUserStats);

      // 异步获取会话统计
      CompletableFuture<StatisticsVO.ConversationStats> conversationStatsFuture =
          CompletableFuture.supplyAsync(this::getConversationStats);

      // 异步获取AI任务统计
      CompletableFuture<StatisticsVO.AiTaskStats> aiTaskStatsFuture =
          CompletableFuture.supplyAsync(this::getAiTaskStats);

      // 异步获取系统综合统计
      CompletableFuture<StatisticsVO.SystemStats> systemStatsFuture =
          CompletableFuture.supplyAsync(this::getSystemStats);

      // 等待所有异步任务完成并组装结果
      CompletableFuture.allOf(
              userStatsFuture, conversationStatsFuture, aiTaskStatsFuture, systemStatsFuture)
          .join();

      return StatisticsVO.builder()
          .userStats(userStatsFuture.get())
          .conversationStats(conversationStatsFuture.get())
          .aiTaskStats(aiTaskStatsFuture.get())
          .systemStats(systemStatsFuture.get())
          .build();

    } catch (InterruptedException | ExecutionException e) {
      log.error("获取统计数据异常", e);
      Thread.currentThread().interrupt();
      throw new RuntimeException("获取统计数据失败", e);
    }
  }

  /** 获取用户统计 */
  private StatisticsVO.UserStats getUserStats() {
    // 获取总用户数
    Long totalUsers = userMapper.countTotalUsers();

    // 获取每日新增用户趋势(最近30天)
    List<StatisticsVO.DailyStats> dailyNewUsers =
        convertToDailyStats(userMapper.countDailyNewUsers(30));

    // 获取每周新增用户趋势(最近12周)
    List<StatisticsVO.WeeklyStats> weeklyNewUsers =
        convertToWeeklyStats(userMapper.countWeeklyNewUsers(12));

    // 获取每月新增用户趋势(最近12个月)
    List<StatisticsVO.MonthlyStats> monthlyNewUsers =
        convertToMonthlyStats(userMapper.countMonthlyNewUsers(12));

    // 获取最近7天活跃用户数
    Timestamp now = Timestamp.valueOf(LocalDateTime.now());
    Timestamp sevenDaysAgo = Timestamp.valueOf(LocalDateTime.now().minusDays(7));
    Long activeUsersLast7Days = userMapper.countActiveUsersBetween(sevenDaysAgo, now);

    // 获取最近30天活跃用户数
    Timestamp thirtyDaysAgo = Timestamp.valueOf(LocalDateTime.now().minusDays(30));
    Long activeUsersLast30Days = userMapper.countActiveUsersBetween(thirtyDaysAgo, now);

    // 获取次日留存率(取一周前的数据)
    Timestamp baseDate = Timestamp.valueOf(LocalDateTime.now().minusDays(7));
    Double retentionRateNextDay = userMapper.calculateRetentionRate(baseDate, 1);

    // 获取7日留存率(取一周前的数据)
    Double retentionRate7Days = userMapper.calculateRetentionRate(baseDate, 7);

    // 获取30日留存率(取一个月前的数据)
    Timestamp monthAgoDate = Timestamp.valueOf(LocalDateTime.now().minusDays(30));
    Double retentionRate30Days = userMapper.calculateRetentionRate(monthAgoDate, 30);

    return StatisticsVO.UserStats.builder()
        .totalUsers(totalUsers)
        .dailyNewUsers(dailyNewUsers)
        .weeklyNewUsers(weeklyNewUsers)
        .monthlyNewUsers(monthlyNewUsers)
        .activeUsersLast7Days(activeUsersLast7Days)
        .activeUsersLast30Days(activeUsersLast30Days)
        .retentionRateNextDay(retentionRateNextDay)
        .retentionRate7Days(retentionRate7Days)
        .retentionRate30Days(retentionRate30Days)
        .build();
  }

  /** 获取会话统计 */
  private StatisticsVO.ConversationStats getConversationStats() {
    // 获取总会话数
    Long totalConversations = conversationMapper.countTotalConversations();

    // 获取每日新增会话趋势(最近30天)
    List<StatisticsVO.DailyStats> dailyNewConversations =
        convertToDailyStats(conversationMapper.countDailyNewConversations(30));

    // 获取每周新增会话趋势(最近12周)
    List<StatisticsVO.WeeklyStats> weeklyNewConversations =
        convertToWeeklyStats(conversationMapper.countWeeklyNewConversations(12));

    // 获取每月新增会话趋势(最近12个月)
    List<StatisticsVO.MonthlyStats> monthlyNewConversations =
        convertToMonthlyStats(conversationMapper.countMonthlyNewConversations(12));

    return StatisticsVO.ConversationStats.builder()
        .totalConversations(totalConversations)
        .dailyNewConversations(dailyNewConversations)
        .weeklyNewConversations(weeklyNewConversations)
        .monthlyNewConversations(monthlyNewConversations)
        .build();
  }

  /** 获取AI任务统计 */
  private StatisticsVO.AiTaskStats getAiTaskStats() {
    // 获取总AI任务数
    Long totalAiTasks = aiTaskMapper.countTotalAiTasks();

    // 获取任务类型分布
    List<Map<String, Object>> taskTypeDistributionData = aiTaskMapper.countTaskTypeDistribution();
    Map<String, Long> taskTypeDistribution =
        taskTypeDistributionData.stream()
            .collect(
                Collectors.toMap(map -> (String) map.get("type"), map -> (Long) map.get("value")));

    // 获取任务平均耗时
    Long averageTaskDuration = aiTaskMapper.calculateAverageTaskDuration();

    // 获取总Token消耗量
    Long totalTokensConsumed = aiTaskMapper.sumTotalTokensConsumed();

    // 获取每日Token消耗趋势(最近30天)
    List<StatisticsVO.DailyStats> dailyTokenConsumption =
        convertToDailyStats(aiTaskMapper.countDailyTokenConsumption(30));

    // 获取每周Token消耗趋势(最近12周)
    List<StatisticsVO.WeeklyStats> weeklyTokenConsumption =
        convertToWeeklyStats(aiTaskMapper.countWeeklyTokenConsumption(12));

    // 获取每月Token消耗趋势(最近12个月)
    List<StatisticsVO.MonthlyStats> monthlyTokenConsumption =
        convertToMonthlyStats(aiTaskMapper.countMonthlyTokenConsumption(12));

    return StatisticsVO.AiTaskStats.builder()
        .totalAiTasks(totalAiTasks)
        .taskTypeDistribution(taskTypeDistribution)
        .averageTaskDuration(averageTaskDuration)
        .totalTokensConsumed(totalTokensConsumed)
        .dailyTokenConsumption(dailyTokenConsumption)
        .weeklyTokenConsumption(weeklyTokenConsumption)
        .monthlyTokenConsumption(monthlyTokenConsumption)
        .build();
  }

  /** 获取系统综合统计 */
  private StatisticsVO.SystemStats getSystemStats() {
    // 获取每日系统访问量趋势(最近30天)
    List<StatisticsVO.DailyStats> dailySystemVisits =
        convertToDailyStats(aiTaskMapper.countDailySystemVisits(30));

    // 获取每周系统访问量趋势(最近12周)
    List<StatisticsVO.WeeklyStats> weeklySystemVisits =
        convertToWeeklyStats(aiTaskMapper.countWeeklySystemVisits(12));

    // 获取每月系统访问量趋势(最近12个月)
    List<StatisticsVO.MonthlyStats> monthlySystemVisits =
        convertToMonthlyStats(aiTaskMapper.countMonthlySystemVisits(12));

    // 用户行为漏斗数据
    Long registeredUsers = userMapper.countTotalUsers();
    Long usersWithConversations = conversationMapper.countUsersWithConversations();
    Long usersWithAiTasks = aiTaskMapper.countUsersWithAiTasks();

    // 计算转化率(百分比)
    Double conversionRateRegToConv =
        registeredUsers > 0 ? (usersWithConversations * 100.0 / registeredUsers) : 0.0;
    Double conversionRateConvToAi =
        usersWithConversations > 0 ? (usersWithAiTasks * 100.0 / usersWithConversations) : 0.0;
    Double conversionRateRegToAi =
        registeredUsers > 0 ? (usersWithAiTasks * 100.0 / registeredUsers) : 0.0;

    return StatisticsVO.SystemStats.builder()
        .dailySystemVisits(dailySystemVisits)
        .weeklySystemVisits(weeklySystemVisits)
        .monthlySystemVisits(monthlySystemVisits)
        .registeredUsers(registeredUsers)
        .usersWithConversations(usersWithConversations)
        .usersWithAiTasks(usersWithAiTasks)
        .conversionRateRegToConv(conversionRateRegToConv)
        .conversionRateConvToAi(conversionRateConvToAi)
        .conversionRateRegToAi(conversionRateRegToAi)
        .build();
  }

  /** 转换为DailyStats对象列表 */
  private List<StatisticsVO.DailyStats> convertToDailyStats(List<Map<String, Object>> data) {
    return data.stream()
        .map(
            map ->
                StatisticsVO.DailyStats.builder()
                    .date((String) map.get("date"))
                    .value(((Number) map.get("value")).longValue())
                    .build())
        .collect(Collectors.toList());
  }

  /** 转换为WeeklyStats对象列表 */
  private List<StatisticsVO.WeeklyStats> convertToWeeklyStats(List<Map<String, Object>> data) {
    return data.stream()
        .map(
            map ->
                StatisticsVO.WeeklyStats.builder()
                    .weekStart((String) map.get("weekStart"))
                    .weekEnd((String) map.get("weekEnd"))
                    .value(((Number) map.get("value")).longValue())
                    .build())
        .collect(Collectors.toList());
  }

  /** 转换为MonthlyStats对象列表 */
  private List<StatisticsVO.MonthlyStats> convertToMonthlyStats(List<Map<String, Object>> data) {
    return data.stream()
        .map(
            map ->
                StatisticsVO.MonthlyStats.builder()
                    .yearMonth((String) map.get("yearMonth"))
                    .value(((Number) map.get("value")).longValue())
                    .build())
        .collect(Collectors.toList());
  }
}

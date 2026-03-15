package cn.yifan.drawsee.mapper;

import cn.yifan.drawsee.pojo.entity.User;
import java.sql.Timestamp;
import java.util.List;
import java.util.Map;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * @FileName UserMapper @Description @Author yifan
 *
 * @date 2025-01-28 09:23
 */
@Mapper
public interface UserMapper {

  User getById(Long id);

  User getByUsername(String username);

  void insert(User user);

  // 获取总用户数
  Long countTotalUsers();

  // 获取某个时间段内的新增用户数
  Long countNewUsersBetween(
      @Param("startTime") Timestamp startTime, @Param("endTime") Timestamp endTime);

  // 获取每日新增用户统计
  List<Map<String, Object>> countDailyNewUsers(@Param("days") int days);

  // 获取每周新增用户统计
  List<Map<String, Object>> countWeeklyNewUsers(@Param("weeks") int weeks);

  // 获取每月新增用户统计
  List<Map<String, Object>> countMonthlyNewUsers(@Param("months") int months);

  // 获取特定时间段内活跃的用户数(有AI任务记录)
  Long countActiveUsersBetween(
      @Param("startTime") Timestamp startTime, @Param("endTime") Timestamp endTime);

  // 获取留存率计算所需数据
  // 参数是基准日期，返回在该日期注册的用户中，在特定后续日期(次日/7天后/30天后)仍活跃的比例
  Double calculateRetentionRate(
      @Param("baseDate") Timestamp baseDate, @Param("afterDays") int afterDays);
}

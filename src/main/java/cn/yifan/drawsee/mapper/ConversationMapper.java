package cn.yifan.drawsee.mapper;

import cn.yifan.drawsee.pojo.entity.Conversation;
import java.sql.Timestamp;
import java.util.List;
import java.util.Map;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * @FileName ConversationMapper @Description @Author yifan
 *
 * @date 2025-01-28 09:49
 */
@Mapper
public interface ConversationMapper {

  Conversation getById(Long id);

  List<Conversation> getByUserId(Long userId);

  List<Conversation> listByUserIds(@Param("userIds") List<Long> userIds);

  void insert(Conversation conversation);

  void update(Conversation conversation);

  // 获取总会话数
  Long countTotalConversations();

  // 获取某个时间段内的新增会话数
  Long countNewConversationsBetween(
      @Param("startTime") Timestamp startTime, @Param("endTime") Timestamp endTime);

  // 获取每日新增会话统计
  List<Map<String, Object>> countDailyNewConversations(@Param("days") int days);

  // 获取每周新增会话统计
  List<Map<String, Object>> countWeeklyNewConversations(@Param("weeks") int weeks);

  // 获取每月新增会话统计
  List<Map<String, Object>> countMonthlyNewConversations(@Param("months") int months);

  // 获取有会话的用户数量
  Long countUsersWithConversations();
}

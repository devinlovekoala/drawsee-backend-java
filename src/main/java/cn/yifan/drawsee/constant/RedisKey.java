package cn.yifan.drawsee.constant;

/**
 * @FileName RedisKeyPrefix @Description @Author yifan
 *
 * @date 2025-03-07 14:33
 */
public class RedisKey {

  /** AI任务前缀 */
  public static final String AI_TASK_PREFIX = "ai_task:";

  public static final String CACHE_SPACE = "cache";

  public static final String CACHE_PREFIX = CACHE_SPACE + ":";

  public static final String INVITATION_CODE_PAGE_KEY = CACHE_PREFIX + "invitation-code-page";

  public static final String TEACHER_INVITATION_CODE_PAGE_KEY =
      CACHE_PREFIX + "teacher-invitation-code-page";

  public static final String DASHBOARD_STATISTICS_KEY = CACHE_PREFIX + "dashboard-statistics";

  public static final String COUNT_SPACE = "count";

  public static final String COUNT_PREFIX = COUNT_SPACE + ":";

  public static final String USE_AI_COUNT_PREFIX = COUNT_PREFIX + "use-ai:";

  public static final String CLEAN_AI_TASK_QUEUE_KEY = "clean-ai-task-queue";

  /** 每日对话次数前缀 */
  public static final String DAILY_CONVERSATION_COUNT = "daily_conversation_count:";

  private RedisKey() {
    // 私有构造函数，防止实例化
  }
}

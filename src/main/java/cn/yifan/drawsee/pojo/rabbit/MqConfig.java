package cn.yifan.drawsee.pojo.rabbit;

import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * @FileName MqConfig @Description @Author yifan
 *
 * @date 2025-03-22 09:17
 */
@Data
@NoArgsConstructor
public class MqConfig {

  private String exchangeName;

  private String queueName;

  private String routingKey;

  private Integer queueCount;

  private String queueConcurrency = "5-10";
}

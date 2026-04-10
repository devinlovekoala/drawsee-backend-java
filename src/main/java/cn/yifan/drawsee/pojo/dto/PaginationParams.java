package cn.yifan.drawsee.pojo.dto;

import jakarta.validation.constraints.Min;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * @FileName PaginationParams @Description 分页参数 @Author yifan
 *
 * @date 2025-03-28 14:34
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class PaginationParams {
  @Min(1)
  private Integer page = 1; // 页码，从1开始

  @Min(1)
  private Integer size = 10; // 每页大小，默认10
}

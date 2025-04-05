package cn.yifan.drawsee.pojo.vo;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.util.List;

/**
 * @FileName PaginatedResponse
 * @Description 分页响应
 * @Author devin
 * @date 2025-03-28 14:36
 **/

@Data
@NoArgsConstructor
@AllArgsConstructor
public class PaginatedResponse<T> {
    private List<T> items;        // 数据列表
    private Integer total;        // 总记录数
    private Integer page;         // 当前页码
    private Integer size;         // 每页大小
    private Integer totalPages;   // 总页数

    public static <T> PaginatedResponse<T> of(List<T> items, Integer total, Integer page, Integer size) {
        return new PaginatedResponse<>(
            items,
            total,
            page,
            size,
            (total + size - 1) / size  // 计算总页数
        );
    }
} 
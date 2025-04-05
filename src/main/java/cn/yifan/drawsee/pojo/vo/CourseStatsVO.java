package cn.yifan.drawsee.pojo.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;

/**
 * @FileName CourseStatsVO
 * @Description 课程统计信息VO类
 * @Author devin
 * @date 2025-03-28 10:59
 **/

@Data
@AllArgsConstructor
@NoArgsConstructor
public class CourseStatsVO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 学生总数
     */
    private Integer studentCount;

    /**
     * 知识点总数
     */
    private Integer knowledgePointCount;

    /**
     * 活跃学生数（最近7天有学习记录的学生数）
     */
    private Integer activeStudentCount;

    /**
     * 知识库数量
     */
    private Integer knowledgeBaseCount;
} 
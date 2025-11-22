package cn.yifan.drawsee.pojo.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;
import java.sql.Timestamp;

/**
 * @FileName ClassVO
 * @Description 班级VO
 * @Author yifan
 * @date 2025-06-10 11:45
 **/

@Data
@AllArgsConstructor
@NoArgsConstructor
public class ClassVO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private Long id;
    
    private String name;
    
    private String description;
    
    private String classCode;
    
    private Long teacherId;
    
    private Timestamp createdAt;
    
    private Timestamp updatedAt;
} 
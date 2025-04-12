package cn.yifan.drawsee.pojo.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;
import java.sql.Timestamp;

/**
 * @FileName TaskVO
 * @Description
 * @Author devin
 * @date 2025-01-29 22:12
 **/

@Data
@AllArgsConstructor
@NoArgsConstructor
public class AiTaskVO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private Long id;

    private String type;

    private String status;

    private Long userId;

    private Long convId;

    private Timestamp createdAt;

    private Timestamp updatedAt;

}
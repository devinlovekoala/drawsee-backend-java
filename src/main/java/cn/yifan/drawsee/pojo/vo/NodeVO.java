package cn.yifan.drawsee.pojo.vo;

import cn.yifan.drawsee.pojo.XYPosition;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;
import java.sql.Timestamp;
import java.util.Map;

/**
 * @FileName NodeVO
 * @Description
 * @Author yifan
 * @date 2025-01-29 20:53
 **/

@Data
@AllArgsConstructor
@NoArgsConstructor
public class NodeVO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private Long id;

    private String type;

    private Map<String, Object> data;

    private XYPosition position;

    private Long height;

    private Long parentId;

    private Long convId;

    private Long userId;

    private Timestamp createdAt;

    private Timestamp updatedAt;

}

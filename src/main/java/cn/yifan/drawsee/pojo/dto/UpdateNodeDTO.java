package cn.yifan.drawsee.pojo.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * @FileName UpdateNodeDTO
 * @Description
 * @Author yifan
 * @date 2025-03-21 15:50
 **/

@Data
@AllArgsConstructor
@NoArgsConstructor
public class UpdateNodeDTO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private ConcurrentHashMap<String, Object> data;

}

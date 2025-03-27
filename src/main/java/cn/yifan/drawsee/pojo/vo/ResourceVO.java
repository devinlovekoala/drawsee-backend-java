package cn.yifan.drawsee.pojo.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;

/**
 * @FileName ResourceVO
 * @Description
 * @Author yifan
 * @date 2025-03-10 10:29
 **/

@Data
@AllArgsConstructor
@NoArgsConstructor
public class ResourceVO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private String url;

}

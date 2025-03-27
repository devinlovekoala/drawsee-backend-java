package cn.yifan.drawsee.pojo.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;

/**
 * @FileName RecognizeTextVO
 * @Description
 * @Author yifan
 * @date 2025-03-23 16:21
 **/

@Data
@AllArgsConstructor
@NoArgsConstructor
public class RecognizeTextVO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private String text;

}

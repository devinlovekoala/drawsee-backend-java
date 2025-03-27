package cn.yifan.drawsee.pojo.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;

/**
 * @FileName UploadAnimationFrameDTO
 * @Description
 * @Author yifan
 * @date 2025-03-22 16:21
 **/

@Data
@AllArgsConstructor
@NoArgsConstructor
public class UploadAnimationFrameDTO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private Long taskId;

    private Long nodeId;

    private String frame;

}

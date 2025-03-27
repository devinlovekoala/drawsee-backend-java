package cn.yifan.drawsee.pojo.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.web.multipart.MultipartFile;

import java.io.Serial;
import java.io.Serializable;

/**
 * @FileName RecognizeTextDTO
 * @Description
 * @Author yifan
 * @date 2025-03-23 14:21
 **/

@Data
@AllArgsConstructor
@NoArgsConstructor
public class RecognizeTextDTO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private MultipartFile file;

}

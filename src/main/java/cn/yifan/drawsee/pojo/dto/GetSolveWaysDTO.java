package cn.yifan.drawsee.pojo.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;

/**
 * @FileName GetSolveWaysDTO
 * @Description
 * @Author yifan
 * @date 2025-03-23 16:24
 **/

@Data
@AllArgsConstructor
@NoArgsConstructor
public class GetSolveWaysDTO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private String question;

}

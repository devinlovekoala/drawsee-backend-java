package cn.yifan.drawsee.pojo.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * @FileName ResourceVO
 * @Description 资源信息VO
 * @Author devin
 * @date 2025-03-09 16:21
 **/
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ResourceVO {
    /**
     * 资源访问URL
     */
    private String url;
    
    /**
     * 资源大小（字节）
     */
    private Long size;
    
    /**
     * 资源类型
     */
    private String contentType;
}
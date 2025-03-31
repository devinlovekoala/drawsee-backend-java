package cn.yifan.drawsee.pojo.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * @FileName ResourceCountVO
 * @Description 资源数量统计VO
 * @Author devin
 * @date 2025-06-20 16:30
 **/

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class ResourceCountVO {

    /**
     * 资源总数
     */
    private int total;
    
    /**
     * PDF文档数量
     */
    private int pdf;
    
    /**
     * Word文档数量
     */
    private int word;
    
    /**
     * 视频数量
     */
    private int mp4;
    
    /**
     * B站资源数量
     */
    private int bilibili;
}
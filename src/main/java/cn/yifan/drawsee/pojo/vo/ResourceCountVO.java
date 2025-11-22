package cn.yifan.drawsee.pojo.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * @FileName ResourceCountVO
 * @Description 资源数量统计VO
 * @Author yifan
 * @date 2025-06-20 16:30
 * @update 2025-10-05 14:35 更新字段命名以更好地反映资源类型
 **/

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class ResourceCountVO {

    /**
     * 资源总数
     */
    private int totalCount;
    
    /**
     * PDF文档数量
     */
    private int pdfCount;
    
    /**
     * Word文档数量
     */
    private int wordCount;
    
    /**
     * 视频数量
     */
    private int videoCount;
    
    /**
     * B站资源数量
     */
    private int bilibiliCount;
}
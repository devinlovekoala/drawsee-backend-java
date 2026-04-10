package cn.yifan.drawsee.pojo.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 上传电路图片后的响应 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class CircuitImageUploadVO {
  private String imageUrl;
}

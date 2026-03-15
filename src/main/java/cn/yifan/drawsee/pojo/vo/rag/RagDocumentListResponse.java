package cn.yifan.drawsee.pojo.vo.rag;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * RAGFlow文档列表响应
 *
 * @author yifan
 * @date 2025-05-08
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class RagDocumentListResponse {

  /** 文档列表 */
  private List<RagDocumentVO> documents;

  /** 总数 */
  private Integer total;
}

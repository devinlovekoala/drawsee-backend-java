package cn.yifan.drawsee.pojo.vo.rag;

import lombok.Builder;
import lombok.Data;

/** 文档上传返回对象 */
@Data
@Builder
public class KnowledgeDocumentUploadVO {

  private KnowledgeDocumentVO document;

  private String taskId;
}

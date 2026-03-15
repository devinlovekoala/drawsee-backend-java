package cn.yifan.drawsee.constant;

/**
 * 知识库文档处理状态
 *
 * @author devin
 * @date 2025-10-10
 */
public enum KnowledgeDocumentStatus {

  /** 文件已上传，等待处理 */
  UPLOADED,

  /** 正在解析正文 */
  PARSING,

  /** 正在进行分块 */
  CHUNKING,

  /** 正在生成向量 */
  EMBEDDING,

  /** 正在写入向量数据库 */
  INDEXING,

  /** 全部流程完成 */
  COMPLETED,

  /** 流程失败 */
  FAILED
}

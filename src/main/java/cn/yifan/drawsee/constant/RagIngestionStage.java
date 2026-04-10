package cn.yifan.drawsee.constant;

/**
 * RAG 文档入库任务阶段
 *
 * @author devin
 * @date 2025-10-10
 */
public enum RagIngestionStage {

  /** 等待处理 */
  PENDING,

  /** 上传至对象存储并记录元数据 */
  STORED,

  /** 文本解析阶段 */
  PARSING,

  /** 文本清洗与分块阶段 */
  CHUNKING,

  /** 生成向量阶段 */
  EMBEDDING,

  /** 向量入库阶段 */
  INDEXING,

  /** 完成 */
  COMPLETED,

  /** 失败 */
  FAILED
}

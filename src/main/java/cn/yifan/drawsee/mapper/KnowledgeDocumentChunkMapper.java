package cn.yifan.drawsee.mapper;

import cn.yifan.drawsee.pojo.entity.KnowledgeDocumentChunk;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 文档分块 Mapper
 *
 * @author yifan
 * @date 2025-10-10
 */
@Mapper
public interface KnowledgeDocumentChunkMapper {

  int insertBatch(@Param("chunks") List<KnowledgeDocumentChunk> chunks);

  List<KnowledgeDocumentChunk> listByDocumentId(@Param("documentId") String documentId);

  int deleteByDocumentId(@Param("documentId") String documentId);

  int countByKnowledgeBaseId(@Param("knowledgeBaseId") String knowledgeBaseId);
}

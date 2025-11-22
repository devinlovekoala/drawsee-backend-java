package cn.yifan.drawsee.mapper;

import cn.yifan.drawsee.pojo.entity.KnowledgeDocument;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 知识库文档 Mapper
 *
 * @author yifan
 * @date 2025-10-10
 */
@Mapper
public interface KnowledgeDocumentMapper {

    int insert(KnowledgeDocument document);

    int update(KnowledgeDocument document);

    KnowledgeDocument getById(@Param("id") String id);

    List<KnowledgeDocument> listByKnowledgeBaseId(@Param("knowledgeBaseId") String knowledgeBaseId,
                                                  @Param("includeDeleted") boolean includeDeleted);

    int softDelete(@Param("id") String id);

    int increaseChunkCount(@Param("id") String id, @Param("delta") int delta);

    int setChunkCount(@Param("id") String id, @Param("chunkCount") int chunkCount);

    int updatePageCount(@Param("id") String id, @Param("pageCount") Integer pageCount);

    int countByKnowledgeBaseId(@Param("knowledgeBaseId") String knowledgeBaseId);

    int countCompletedByKnowledgeBaseId(@Param("knowledgeBaseId") String knowledgeBaseId);
}

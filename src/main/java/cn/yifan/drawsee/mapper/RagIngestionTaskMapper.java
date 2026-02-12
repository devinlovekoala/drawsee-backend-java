package cn.yifan.drawsee.mapper;

import cn.yifan.drawsee.pojo.entity.RagIngestionTask;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * RAG 入库任务 Mapper
 *
 * @author devin
 * @date 2025-10-10
 */
@Mapper
public interface RagIngestionTaskMapper {

    int insert(RagIngestionTask task);

    int update(RagIngestionTask task);

    RagIngestionTask getById(@Param("id") String id);

    List<RagIngestionTask> listByDocumentId(@Param("documentId") String documentId);

    List<RagIngestionTask> listPendingTasks(@Param("limit") int limit);

    List<RagIngestionTask> listTasks(
        @Param("knowledgeBaseId") String knowledgeBaseId,
        @Param("documentId") String documentId,
        @Param("stage") String stage,
        @Param("status") String status,
        @Param("offset") int offset,
        @Param("limit") int limit
    );
}

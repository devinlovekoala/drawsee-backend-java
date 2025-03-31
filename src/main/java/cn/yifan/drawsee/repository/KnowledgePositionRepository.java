package cn.yifan.drawsee.repository;

import cn.yifan.drawsee.pojo.mongo.KnowledgePosition;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * @FileName KnowledgePositionRepository
 * @Description 知识点位置信息仓库
 * @Author devin
 * @date 2025-04-12 11:20
 **/

@Repository
public interface KnowledgePositionRepository extends MongoRepository<KnowledgePosition, String> {

    /**
     * 根据知识点ID查找位置信息
     * @param knowledgeId 知识点ID
     * @return 位置信息
     */
    KnowledgePosition findByKnowledgeId(String knowledgeId);
    
    /**
     * 根据知识库ID查找所有知识点位置信息
     * @param knowledgeBaseId 知识库ID
     * @return 位置信息列表
     */
    List<KnowledgePosition> findAllByKnowledgeBaseId(String knowledgeBaseId);
    
    /**
     * 根据知识库ID和知识点ID查找位置信息
     * @param knowledgeBaseId 知识库ID
     * @param knowledgeId 知识点ID
     * @return 位置信息
     */
    KnowledgePosition findByKnowledgeBaseIdAndKnowledgeId(String knowledgeBaseId, String knowledgeId);
    
    /**
     * 删除指定知识库下的所有知识点位置
     * @param knowledgeBaseId 知识库ID
     */
    void deleteAllByKnowledgeBaseId(String knowledgeBaseId);
    
    /**
     * 删除指定知识点的位置信息
     * @param knowledgeId 知识点ID
     */
    void deleteByKnowledgeId(String knowledgeId);
} 
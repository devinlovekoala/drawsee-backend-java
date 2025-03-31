package cn.yifan.drawsee.repository;

import cn.yifan.drawsee.pojo.mongo.KnowledgeBase;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * @FileName KnowledgeBaseRepository
 * @Description 知识库Repository接口
 * @Author devin
 * @date 2025-03-28 10:38
 **/

@Repository
public interface KnowledgeBaseRepository extends MongoRepository<KnowledgeBase, String> {

    KnowledgeBase findByName(String name);

    List<KnowledgeBase> findAllByCreatorId(Long creatorId);
    
    KnowledgeBase findByInvitationCode(String invitationCode);
    
    List<KnowledgeBase> findByMembersContaining(Long userId);
    
    List<KnowledgeBase> findByCreatorIdAndIsPublishedTrue(Long creatorId);
    
    List<KnowledgeBase> findByIsPublishedTrue();
} 
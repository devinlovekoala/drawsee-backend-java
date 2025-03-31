package cn.yifan.drawsee.repository;

import cn.yifan.drawsee.pojo.mongo.Knowledge;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * @FileName KnowledgeRepository
 * @Description
 * @Author yifan
 * @date 2025-01-31 11:45
 **/

@Repository
public interface KnowledgeRepository extends MongoRepository<Knowledge, String> {

    Knowledge findByName(String name);

    List<Knowledge> findAllBySubject(String subject);

    Knowledge findBySubjectAndName(String subject, String name);

    List<Knowledge> findAllByIdIn(List<String> ids);

}

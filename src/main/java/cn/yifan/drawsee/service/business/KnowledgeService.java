package cn.yifan.drawsee.service.business;

import cn.yifan.drawsee.exception.ApiError;
import cn.yifan.drawsee.exception.ApiException;
import cn.yifan.drawsee.pojo.dto.AddKnowledgeDTO;
import cn.yifan.drawsee.pojo.dto.UpdateKnowledgeDTO;
import cn.yifan.drawsee.pojo.mongo.Knowledge;
import cn.yifan.drawsee.repository.KnowledgeRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import java.util.ArrayList;
import java.util.List;

@Service
public class KnowledgeService {

    @Autowired
    private KnowledgeRepository knowledgeRepository;

    public List<Knowledge> getSubjectAllKnowledge(String subject) {
        return knowledgeRepository.findAllBySubject(subject);
    }

    public void addSubjectKnowledge(String subject, AddKnowledgeDTO addKnowledgeDTO) {
        Knowledge knowledge = knowledgeRepository.findBySubjectAndName(subject, addKnowledgeDTO.getName());
        if (knowledge != null) {
            throw new ApiException(ApiError.KNOWLEDGE_HAD_EXISTED);
        }
        Knowledge parent = knowledgeRepository.findById(addKnowledgeDTO.getParentId()).orElse(null);
        if (parent == null) {
            throw new ApiException(ApiError.KNOWLEDGE_PARENT_NOT_EXISTED);
        }
        knowledge = new Knowledge();
        knowledge.setSubject(subject);
        knowledge.setName(addKnowledgeDTO.getName());
        knowledge.setAliases(addKnowledgeDTO.getAliases());
        knowledge.setResources(addKnowledgeDTO.getResources());
        knowledge.setLevel(parent.getLevel() + 1);
        knowledge.setParentId(parent.getId());
        knowledge.setChildrenIds(new ArrayList<>());
        knowledgeRepository.save(knowledge);
    }

    public void updateKnowledge(String id, UpdateKnowledgeDTO updateKnowledgeDTO) {
        Knowledge knowledge = knowledgeRepository.findByName(updateKnowledgeDTO.getName());
        if (knowledge != null) {
            throw new ApiException(ApiError.KNOWLEDGE_HAD_EXISTED);
        }
        knowledge = knowledgeRepository.findById(id).orElse(null);
        if (knowledge == null) {
            throw new ApiException(ApiError.KNOWLEDGE_NOT_EXISTED);
        }
        Knowledge parent = knowledgeRepository.findById(updateKnowledgeDTO.getParentId()).orElse(null);
        if (parent == null) {
            throw new ApiException(ApiError.KNOWLEDGE_PARENT_NOT_EXISTED);
        }
        knowledge.setName(updateKnowledgeDTO.getName());
        knowledge.setAliases(updateKnowledgeDTO.getAliases());
        knowledge.setResources(updateKnowledgeDTO.getResources());
        knowledge.setLevel(parent.getLevel() + 1);
        knowledge.setParentId(parent.getId());
        knowledgeRepository.save(knowledge);
        for (String childId : knowledge.getChildrenIds()) {
            Knowledge child = knowledgeRepository.findById(childId).orElse(null);
            if (child != null) {
                child.setLevel(parent.getLevel() + 2);
                knowledgeRepository.save(child);
            }
        }
    }

    public void deleteKnowledge(String id) {
        Knowledge knowledge = knowledgeRepository.findById(id).orElse(null);
        if (knowledge == null) {
            throw new ApiException(ApiError.KNOWLEDGE_NOT_EXISTED);
        }
        knowledgeRepository.delete(knowledge);
    }

}

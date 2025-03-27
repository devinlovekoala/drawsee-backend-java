package cn.yifan.drawsee.controller;

import cn.yifan.drawsee.pojo.dto.AddKnowledgeDTO;
import cn.yifan.drawsee.pojo.dto.UpdateKnowledgeDTO;
import cn.yifan.drawsee.pojo.mongo.Knowledge;
import cn.yifan.drawsee.service.business.KnowledgeService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/knowledge")
public class KnowledgeController {

    @Autowired
    private KnowledgeService knowledgeService;

    @GetMapping("/{subject}")
    public List<Knowledge> getSubjectAllKnowledge(@PathVariable("subject") String subject) {
        return knowledgeService.getSubjectAllKnowledge(subject);
    }

    @PostMapping("/{subject}")
    public void addSubjectKnowledge(@PathVariable("subject") String subject, @RequestBody AddKnowledgeDTO addKnowledgeDTO) {
        knowledgeService.addSubjectKnowledge(subject, addKnowledgeDTO);
    }

    @PutMapping("/{id}")
    public void updateKnowledge(
        @PathVariable("id") String id,
        @RequestBody UpdateKnowledgeDTO updateKnowledgeDTO
    ) {
        knowledgeService.updateKnowledge(id, updateKnowledgeDTO);
    }

    @DeleteMapping("/{id}")
    public void deleteKnowledge(@PathVariable("id") String id) {
        knowledgeService.deleteKnowledge(id);
    }

}
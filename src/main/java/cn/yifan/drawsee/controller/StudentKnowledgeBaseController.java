package cn.yifan.drawsee.controller;

import cn.dev33.satoken.annotation.SaCheckLogin;
import cn.yifan.drawsee.constant.UserRole;
import cn.yifan.drawsee.pojo.dto.JoinKnowledgeBaseDTO;
import cn.yifan.drawsee.pojo.mongo.Knowledge;
import cn.yifan.drawsee.pojo.vo.KnowledgeBaseVO;
import cn.yifan.drawsee.pojo.vo.ResourceCountVO;
import cn.yifan.drawsee.service.business.KnowledgeBaseService;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * @FileName StudentKnowledgeBaseController
 * @Description 学生知识库控制器类，提供学生用户访问已加入知识库的接口
 * @Author devin
 * @date 2025-08-15 16:30
 **/

@RestController
@RequestMapping("/student/knowledge/base")
@SaCheckLogin
public class StudentKnowledgeBaseController {

    private static final Logger logger = LoggerFactory.getLogger(StudentKnowledgeBaseController.class);

    @Autowired
    private KnowledgeBaseService knowledgeBaseService;

    /**
     * 获取学生可访问的所有知识库列表
     * 包括通过邀请码加入的知识库
     * @return 知识库列表
     */
    @GetMapping
    public List<KnowledgeBaseVO> getAccessibleKnowledgeBases() {
        return knowledgeBaseService.getKnowledgeBasesForCurrentUser();
    }

    /**
     * 学生加入知识库
     * @param joinKnowledgeBaseDTO 加入知识库DTO
     * @return 知识库ID
     */
    @PostMapping("/join")
    public String joinKnowledgeBase(@RequestBody @Valid JoinKnowledgeBaseDTO joinKnowledgeBaseDTO) {
        return knowledgeBaseService.joinKnowledgeBase(joinKnowledgeBaseDTO);
    }

    /**
     * 获取学生已加入的知识库列表
     * @return 知识库列表
     */
    @GetMapping("/joined")
    public List<KnowledgeBaseVO> getJoinedKnowledgeBases() {
        return knowledgeBaseService.getMyJoinedKnowledgeBases();
    }

    /**
     * 获取知识库详情
     * @param id 知识库ID
     * @return 知识库详情
     */
    @GetMapping("/{id}")
    public KnowledgeBaseVO getKnowledgeBaseDetail(@PathVariable("id") String id) {
        return knowledgeBaseService.getKnowledgeBaseDetail(id);
    }

    /**
     * 获取知识库中的知识点列表
     * @param id 知识库ID
     * @return 知识点列表
     */
    @GetMapping("/{id}/knowledge")
    public List<Knowledge> getKnowledgeBaseKnowledgePoints(@PathVariable("id") String id) {
        return knowledgeBaseService.getKnowledgeBaseKnowledgePoints(id);
    }

    /**
     * 获取知识点资源统计信息
     * @param knowledgeId 知识点ID
     * @return 资源统计信息
     */
    @GetMapping("/knowledge/resource/count/{knowledgeId}")
    public ResourceCountVO getKnowledgeResourceCount(@PathVariable("knowledgeId") String knowledgeId) {
        return knowledgeBaseService.getKnowledgeResourceCount(knowledgeId);
    }
} 
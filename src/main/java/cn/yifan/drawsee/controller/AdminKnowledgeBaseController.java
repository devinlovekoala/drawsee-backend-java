package cn.yifan.drawsee.controller;

import cn.dev33.satoken.annotation.SaCheckRole;
import cn.yifan.drawsee.constant.UserRole;
import cn.yifan.drawsee.pojo.dto.CreateKnowledgeBaseDTO;
import cn.yifan.drawsee.pojo.vo.KnowledgeBaseVO;
import cn.yifan.drawsee.service.business.KnowledgeBaseService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * @FileName AdminKnowledgeBaseController
 * @Description 管理员知识库控制器类
 * @Author devin
 * @date 2025-08-15 15:30
 **/

@RestController
@RequestMapping("/admin/knowledge/base")
@SaCheckRole(UserRole.ADMIN)
public class AdminKnowledgeBaseController {

    @Autowired
    private KnowledgeBaseService knowledgeBaseService;

    /**
     * 管理员创建知识库
     * @param createKnowledgeBaseDTO 创建知识库DTO
     * @param isPublished 是否立即发布
     * @return 知识库ID
     */
    @PostMapping
    public String createKnowledgeBase(
            @RequestBody @Valid CreateKnowledgeBaseDTO createKnowledgeBaseDTO,
            @RequestParam(defaultValue = "true") boolean isPublished
    ) {
        return knowledgeBaseService.createKnowledgeBaseByAdmin(createKnowledgeBaseDTO, isPublished);
    }

    /**
     * 管理员获取所有知识库列表
     * @return 知识库列表
     */
    @GetMapping
    public List<KnowledgeBaseVO> getAllKnowledgeBases() {
        // 这里需要在KnowledgeBaseService中添加一个获取所有知识库的方法
        // 暂时先使用现有方法返回我创建的知识库
        return knowledgeBaseService.getMyCreatedKnowledgeBases();
    }
} 
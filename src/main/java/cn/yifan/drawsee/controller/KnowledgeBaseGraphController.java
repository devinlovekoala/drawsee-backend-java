package cn.yifan.drawsee.controller;

import cn.yifan.drawsee.pojo.dto.AddKnowledgeDTO;
import cn.yifan.drawsee.pojo.dto.UpdateKnowledgeDTO;
import cn.yifan.drawsee.pojo.mongo.KnowledgePosition;
import cn.yifan.drawsee.service.business.KnowledgeBaseGraphService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 知识库图谱控制器 TODO
 */
@RestController
@RequestMapping({"/api/knowledge-base/graph", "/knowledge-base/graph"})
@RequiredArgsConstructor
public class KnowledgeBaseGraphController {

    private final KnowledgeBaseGraphService knowledgeBaseGraphService;

    /**
     * 获取知识库的知识点列表
     *
     * @param knowledgeBaseId 知识库ID
     * @return 知识点列表
     */
    @GetMapping("/{knowledgeBaseId}/knowledge-points")
    public ResponseEntity<?> getKnowledgeBaseKnowledgePoints(@PathVariable String knowledgeBaseId) {
        return ResponseEntity.ok(knowledgeBaseGraphService.getKnowledgeBaseKnowledgePoints(knowledgeBaseId));
    }

    /**
     * 添加知识点到知识库
     *
     * @param knowledgeBaseId  知识库ID
     * @param addKnowledgeDTO  知识点数据
     * @return 新创建的知识点ID
     */
    @PostMapping("/{knowledgeBaseId}/knowledge-points")
    public ResponseEntity<?> addKnowledgeBaseKnowledgePoint(
            @PathVariable String knowledgeBaseId,
            @RequestBody AddKnowledgeDTO addKnowledgeDTO) {
        String knowledgeId = knowledgeBaseGraphService.addKnowledgeBaseKnowledgePoint(knowledgeBaseId, addKnowledgeDTO);
        return ResponseEntity.ok().body(Map.of("id", knowledgeId));
    }

    /**
     * 更新知识库中的知识点
     *
     * @param knowledgeBaseId     知识库ID
     * @param knowledgeId         知识点ID
     * @param updateKnowledgeDTO  更新的知识点数据
     * @return 操作结果
     */
    @PutMapping("/{knowledgeBaseId}/knowledge-points/{knowledgeId}")
    public ResponseEntity<?> updateKnowledgeBaseKnowledgePoint(
            @PathVariable String knowledgeBaseId,
            @PathVariable String knowledgeId,
            @RequestBody UpdateKnowledgeDTO updateKnowledgeDTO) {
        knowledgeBaseGraphService.updateKnowledgeBaseKnowledgePoint(knowledgeBaseId, knowledgeId, updateKnowledgeDTO);
        return ResponseEntity.ok().build();
    }

    /**
     * 删除知识库中的知识点
     *
     * @param knowledgeBaseId 知识库ID
     * @param knowledgeId     知识点ID
     * @return 操作结果
     */
    @DeleteMapping("/{knowledgeBaseId}/knowledge-points/{knowledgeId}")
    public ResponseEntity<?> deleteKnowledgeBaseKnowledgePoint(
            @PathVariable String knowledgeBaseId,
            @PathVariable String knowledgeId) {
        knowledgeBaseGraphService.deleteKnowledgeBaseKnowledgePoint(knowledgeBaseId, knowledgeId);
        return ResponseEntity.ok().build();
    }

    /**
     * 获取知识库图谱数据
     *
     * @param knowledgeBaseId 知识库ID
     * @return 图谱数据（节点和边）
     */
    @GetMapping("/{knowledgeBaseId}")
    public ResponseEntity<?> getKnowledgeBaseGraph(@PathVariable String knowledgeBaseId) {
        return ResponseEntity.ok(knowledgeBaseGraphService.getKnowledgeBaseGraph(knowledgeBaseId));
    }
    
    /**
     * 更新知识库图谱节点位置
     *
     * @param knowledgeBaseId 知识库ID
     * @param nodePositions   节点位置数据
     * @return 操作结果
     */
    @PutMapping("/{knowledgeBaseId}/positions")
    public ResponseEntity<?> updateKnowledgeBaseNodePositions(
            @PathVariable String knowledgeBaseId,
            @RequestBody Map<String, Object> nodePositions) {
        knowledgeBaseGraphService.updateKnowledgeBaseNodePositions(knowledgeBaseId, nodePositions);
        return ResponseEntity.ok().build();
    }
    
    /**
     * 获取知识库中所有知识点的位置信息
     *
     * @param knowledgeBaseId 知识库ID
     * @return 知识点位置列表
     */
    @GetMapping("/{knowledgeBaseId}/positions")
    public ResponseEntity<?> getKnowledgeBaseNodePositions(@PathVariable String knowledgeBaseId) {
        List<KnowledgePosition> positions = knowledgeBaseGraphService.getKnowledgeBaseNodePositions(knowledgeBaseId);
        return ResponseEntity.ok(positions);
    }
    
    /**
     * 获取特定知识点的位置信息
     *
     * @param knowledgeBaseId 知识库ID
     * @param knowledgeId 知识点ID
     * @return 知识点位置信息
     */
    @GetMapping("/{knowledgeBaseId}/positions/{knowledgeId}")
    public ResponseEntity<?> getKnowledgeNodePosition(
            @PathVariable String knowledgeBaseId,
            @PathVariable String knowledgeId) {
        KnowledgePosition position = knowledgeBaseGraphService.getKnowledgeNodePosition(knowledgeBaseId, knowledgeId);
        return ResponseEntity.ok(position);
    }
}
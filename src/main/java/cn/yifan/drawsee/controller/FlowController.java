package cn.yifan.drawsee.controller;

import cn.dev33.satoken.annotation.SaCheckLogin;
import cn.yifan.drawsee.pojo.dto.CreateAiTaskDTO;
import cn.yifan.drawsee.pojo.dto.UpdateNodeDTO;
import cn.yifan.drawsee.pojo.dto.UpdateNodesDTO;
import cn.yifan.drawsee.pojo.vo.*;
import cn.yifan.drawsee.service.business.FlowService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;
import java.util.Map;

/**
 * @FileName FlowController
 * @Description
 * @Author yifan
 * @date 2025-01-28 22:38
 **/

@RestController
@RequestMapping("/flow")
@SaCheckLogin
public class FlowController {

    @Autowired
    private FlowService flowService;

    @GetMapping("/conversations")
    public List<ConversationVO> getConversations() {
        return flowService.getConversations();
    }

    @DeleteMapping("/conversations/{convId}")
    public void deleteConversation(@PathVariable Long convId) {
        flowService.deleteConversation(convId);
    }

    @GetMapping("/nodes")
    public List<NodeVO> getNodes(@RequestParam Long convId) {
        return flowService.getNodes(convId);
    }

    @PostMapping("/nodes")
    public void updateNodes(@RequestBody @Valid UpdateNodesDTO updateNodesDTO) {
        flowService.updateNodes(updateNodesDTO);
    }

    @PostMapping("/nodes/{nodeId}")
    public void updateNode(@PathVariable Long nodeId, @RequestBody UpdateNodeDTO updateNodeDTO) {
        flowService.updateNode(nodeId, updateNodeDTO);
    }

    @DeleteMapping("/nodes/{nodeId}")
    public void deleteNode(@PathVariable Long nodeId) {
        flowService.deleteNode(nodeId);
    }

    @GetMapping("/tasks")
    public List<AiTaskVO> getProcessingTasks(@RequestParam Long convId) {
        return flowService.getProcessingTasks(convId);
    }

    @PostMapping("/tasks")
    public CreateAiTaskVO createTask(@RequestBody @Valid CreateAiTaskDTO createAiTaskDTO) {
        return flowService.createTask(createAiTaskDTO);
    }

    @GetMapping("/completion")
    public SseEmitter getCompletion(@RequestParam Long taskId) {
        SseEmitter emitter = new SseEmitter(0L); // 设置超时时间为0，表示永不超时
        flowService.getCompletion(emitter, taskId);
        return emitter;
    }

    @GetMapping("/resources")
    public ResourceVO getResource(@RequestParam String objectName) {
        return flowService.getResource(objectName);
    }

}

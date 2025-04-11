package cn.yifan.drawsee.controller;

import cn.dev33.satoken.annotation.SaCheckLogin;
import cn.dev33.satoken.annotation.SaIgnore;
import cn.yifan.drawsee.pojo.dto.CircuitAnalysisDTO;
import cn.yifan.drawsee.pojo.dto.GetSolveWaysDTO;
import cn.yifan.drawsee.pojo.dto.RecognizeTextDTO;
import cn.yifan.drawsee.pojo.dto.UploadAnimationFrameDTO;
import cn.yifan.drawsee.pojo.entity.CircuitDesign;
import cn.yifan.drawsee.pojo.vo.RecognizeTextVO;
import cn.yifan.drawsee.service.business.ToolService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

/**
 * @FileName ToolController
 * @Description
 * @Author yifan
 * @date 2025-03-22 16:17
 **/

@RestController
@RequestMapping("/tool")
@SaCheckLogin
public class ToolController {

    @Autowired
    private ToolService toolService;

    @PostMapping("/animation_frame")
    @SaIgnore
    public void uploadAnimationFrame(@RequestBody UploadAnimationFrameDTO uploadAnimationFrameDTO) {
        toolService.uploadAnimationFrame(uploadAnimationFrameDTO);
    }

    @PostMapping(value = "/recognize_text", consumes = "multipart/form-data")
    public RecognizeTextVO recognizeTextFromImage(@RequestPart("file") MultipartFile file) {
        return toolService.recognizeTextFromImage(file);
    }

    @PostMapping("/solve_ways")
    public List<String> getSolveWays(@RequestBody GetSolveWaysDTO getSolveWaysDTO) {
        return toolService.getSolveWays(getSolveWaysDTO);
    }

    /**
     * 生成电路SPICE网表
     * 
     * @param circuitAnalysisDTO 电路分析DTO
     * @return SPICE网表内容
     */
    // @PostMapping("/circuit/spice")
    // public String generateSpiceNetlist(@RequestBody CircuitAnalysisDTO circuitAnalysisDTO) {
    //     return toolService.generateSpiceNetlist(circuitAnalysisDTO.getCircuitDesign());
    // }

    /**
     * 电路分析
     * 
     * @param circuitAnalysisDTO 电路分析DTO
     * @return 分析结果
     */
    // @PostMapping("/circuit/analyze")
    // public Map<String, Object> analyzeCircuit(@RequestBody CircuitAnalysisDTO circuitAnalysisDTO) {
    //     return toolService.analyzeCircuit(circuitAnalysisDTO.getCircuitDesign());
    // }
}

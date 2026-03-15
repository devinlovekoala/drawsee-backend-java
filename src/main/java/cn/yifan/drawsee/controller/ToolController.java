package cn.yifan.drawsee.controller;

import cn.dev33.satoken.annotation.SaCheckLogin;
import cn.dev33.satoken.annotation.SaIgnore;
import cn.yifan.drawsee.pojo.dto.CircuitImageConvertRequest;
import cn.yifan.drawsee.pojo.dto.GetSolveWaysDTO;
import cn.yifan.drawsee.pojo.dto.UploadAnimationFrameDTO;
import cn.yifan.drawsee.pojo.entity.CircuitDesign;
import cn.yifan.drawsee.pojo.vo.CircuitImageUploadVO;
import cn.yifan.drawsee.pojo.vo.RecognizeTextVO;
import cn.yifan.drawsee.service.business.ToolService;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

/**
 * @FileName ToolController @Description @Author yifan
 *
 * @date 2025-03-22 16:17
 */
@RestController
@RequestMapping("/tool")
@SaCheckLogin
@Slf4j
public class ToolController {

  @Autowired private ToolService toolService;

  @PostMapping("/animation_frame")
  @SaIgnore
  public void uploadAnimationFrame(@RequestBody UploadAnimationFrameDTO uploadAnimationFrameDTO) {
    toolService.uploadAnimationFrame(uploadAnimationFrameDTO);
  }

  @PostMapping(value = "/recognize_text", consumes = "multipart/form-data")
  public RecognizeTextVO recognizeTextFromImage(@RequestPart("file") MultipartFile file) {
    return toolService.recognizeTextFromImage(file);
  }

  @PostMapping(value = "/recognize_circuit", consumes = "multipart/form-data")
  public CircuitDesign recognizeCircuitFromImage(@RequestPart("file") MultipartFile file) {
    return toolService.recognizeCircuitFromImage(file);
  }

  @PostMapping(value = "/recognize_circuit/upload", consumes = "multipart/form-data")
  public CircuitImageUploadVO uploadCircuitImage(@RequestPart("file") MultipartFile file) {
    return toolService.uploadCircuitImage(file);
  }

  @PostMapping(value = "/recognize_circuit/convert")
  public CircuitDesign recognizeCircuitFromUploaded(
      @RequestBody CircuitImageConvertRequest request) {
    return toolService.recognizeCircuitFromUploadedImage(request.getImageUrl());
  }

  @PostMapping("/solve_ways")
  public List<String> getSolveWays(@RequestBody GetSolveWaysDTO getSolveWaysDTO) {
    return toolService.getSolveWays(getSolveWaysDTO);
  }
}

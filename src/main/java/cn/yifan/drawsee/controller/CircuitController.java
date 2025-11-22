package cn.yifan.drawsee.controller;

import cn.dev33.satoken.annotation.SaCheckLogin;
import cn.dev33.satoken.stp.StpUtil;
import cn.yifan.drawsee.exception.BusinessException;
import cn.yifan.drawsee.pojo.Result;
import cn.yifan.drawsee.pojo.entity.CircuitDesign;
import cn.yifan.drawsee.service.business.CircuitDesignService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * @FileName CircuitController
 * @Description 电路设计控制器
 * @Author yifan
 * @date 2025-07-18 16:40
 **/
@RestController
@RequestMapping("/api/circuits")
@SaCheckLogin
@Slf4j
public class CircuitController extends BaseController {

    @Autowired
    private CircuitDesignService circuitDesignService;

    /**
     * 保存电路设计
     * @param circuitDesign 电路设计数据
     * @return 保存结果
     */
    @PostMapping
    public Result<Map<String, Object>> saveCircuitDesign(@RequestBody CircuitDesign circuitDesign) {
        try {
            Long userId = StpUtil.getLoginIdAsLong();
            Map<String, Object> result = circuitDesignService.saveCircuitDesign(userId, circuitDesign);
            
            if ((Boolean) result.get("success")) {
                log.info("保存电路设计成功, id: {}", result.get("id"));
                return Result.success(result);
            } else {
                log.error("保存电路设计失败");
                Result<Map<String, Object>> errorResult = new Result<>();
                errorResult.setCode(500);
                errorResult.setMessage("保存电路设计失败");
                return errorResult;
            }
        } catch (Exception e) {
            log.error("保存电路设计失败", e);
            Result<Map<String, Object>> errorResult = new Result<>();
            errorResult.setCode(500);
            errorResult.setMessage("保存电路设计失败: " + e.getMessage());
            return errorResult;
        }
    }

    /**
     * 获取电路设计列表
     * @return 电路设计列表
     */
    @GetMapping
    public Result<Map<String, Object>> getCircuitDesigns() {
        try {
            Long userId = StpUtil.getLoginIdAsLong();
            Map<String, Object> result = circuitDesignService.getCircuitDesigns(userId);
            return Result.success(result);
        } catch (Exception e) {
            log.error("获取电路设计列表失败", e);
            Result<Map<String, Object>> errorResult = new Result<>();
            errorResult.setCode(500);
            errorResult.setMessage("获取电路设计列表失败: " + e.getMessage());
            return errorResult;
        }
    }

    /**
     * 获取电路设计详情
     * @param id 电路设计ID
     * @return 电路设计详情
     */
    @GetMapping("/{id}")
    public Result<CircuitDesign> getCircuitDesignById(@PathVariable String id) {
        try {
            Long userId = StpUtil.getLoginIdAsLong();
            Long designId = Long.parseLong(id);
            CircuitDesign design = circuitDesignService.getCircuitDesignById(userId, designId);
            return Result.success(design);
        } catch (BusinessException e) {
            log.error("获取电路设计详情失败: {}", e.getMessage());
            Result<CircuitDesign> errorResult = new Result<>();
            errorResult.setCode(500);
            errorResult.setMessage(e.getMessage());
            return errorResult;
        } catch (NumberFormatException e) {
            log.error("获取电路设计详情失败: 无效的ID格式 {}", id);
            Result<CircuitDesign> errorResult = new Result<>();
            errorResult.setCode(400);
            errorResult.setMessage("无效的电路设计ID格式");
            return errorResult;
        } catch (Exception e) {
            log.error("获取电路设计详情失败", e);
            Result<CircuitDesign> errorResult = new Result<>();
            errorResult.setCode(500);
            errorResult.setMessage("获取电路设计详情失败: " + e.getMessage());
            return errorResult;
        }
    }

    /**
     * 删除电路设计
     * @param id 电路设计ID
     * @return 删除结果
     */
    @DeleteMapping("/{id}")
    public Result<Map<String, Object>> deleteCircuitDesign(@PathVariable String id) {
        try {
            Long userId = StpUtil.getLoginIdAsLong();
            Long designId = Long.parseLong(id);
            Map<String, Object> result = circuitDesignService.deleteCircuitDesign(userId, designId);
            
            if ((Boolean) result.get("success")) {
                log.info("删除电路设计成功, id: {}", id);
                return Result.success(result);
            } else {
                log.error("删除电路设计失败, id: {}", id);
                Result<Map<String, Object>> errorResult = new Result<>();
                errorResult.setCode(500);
                errorResult.setMessage("删除电路设计失败");
                return errorResult;
            }
        } catch (NumberFormatException e) {
            log.error("删除电路设计失败: 无效的ID格式 {}", id);
            Result<Map<String, Object>> errorResult = new Result<>();
            errorResult.setCode(400);
            errorResult.setMessage("无效的电路设计ID格式");
            return errorResult;
        } catch (Exception e) {
            log.error("删除电路设计失败", e);
            Result<Map<String, Object>> errorResult = new Result<>();
            errorResult.setCode(500);
            errorResult.setMessage("删除电路设计失败: " + e.getMessage());
            return errorResult;
        }
    }

    /**
     * 更新电路设计
     * @param id 电路设计ID
     * @param circuitDesign 电路设计数据
     * @return 更新结果
     */
    @PutMapping("/{id}")
    public Result<Map<String, Object>> updateCircuitDesign(@PathVariable String id,
                                                           @RequestBody CircuitDesign circuitDesign) {
        try {
            Long userId = StpUtil.getLoginIdAsLong();
            Long designId = Long.parseLong(id);
            Map<String, Object> result = circuitDesignService.updateCircuitDesign(userId, designId, circuitDesign);

            if ((Boolean) result.get("success")) {
                log.info("更新电路设计成功, id: {}", id);
                return Result.success(result);
            } else {
                log.error("更新电路设计失败, id: {}", id);
                Result<Map<String, Object>> errorResult = new Result<>();
                errorResult.setCode(500);
                errorResult.setMessage("更新电路设计失败");
                return errorResult;
            }
        } catch (NumberFormatException e) {
            log.error("更新电路设计失败: 无效的ID格式 {}", id);
            Result<Map<String, Object>> errorResult = new Result<>();
            errorResult.setCode(400);
            errorResult.setMessage("无效的电路设计ID格式");
            return errorResult;
        } catch (Exception e) {
            log.error("更新电路设计失败", e);
            Result<Map<String, Object>> errorResult = new Result<>();
            errorResult.setCode(500);
            errorResult.setMessage("更新电路设计失败: " + e.getMessage());
            return errorResult;
        }
    }
}

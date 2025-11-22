package cn.yifan.drawsee.service.business;

import cn.yifan.drawsee.pojo.entity.CircuitDesign;

import java.util.List;
import java.util.Map;

/**
 * @FileName CircuitDesignService
 * @Description 电路设计服务接口
 * @Author yifan
 * @date 2025-07-18 16:25
 **/
public interface CircuitDesignService {

    /**
     * 保存电路设计
     * @param userId 用户ID
     * @param circuitDesign 电路设计
     * @return 保存结果
     */
    Map<String, Object> saveCircuitDesign(Long userId, CircuitDesign circuitDesign);

    /**
     * 获取用户的电路设计列表
     * @param userId 用户ID
     * @return 电路设计列表
     */
    Map<String, Object> getCircuitDesigns(Long userId);

    /**
     * 根据ID获取电路设计详情
     * @param userId 用户ID
     * @param id 电路设计ID
     * @return 电路设计详情
     */
    CircuitDesign getCircuitDesignById(Long userId, Long id);

    /**
     * 删除电路设计
     * @param userId 用户ID
     * @param id 电路设计ID
     * @return 删除结果
     */
    Map<String, Object> deleteCircuitDesign(Long userId, Long id);

    /**
     * 更新电路设计
     * @param userId 用户ID
     * @param id 电路设计ID
     * @param circuitDesign 电路设计数据
     * @return 更新结果
     */
    Map<String, Object> updateCircuitDesign(Long userId, Long id, CircuitDesign circuitDesign);
}

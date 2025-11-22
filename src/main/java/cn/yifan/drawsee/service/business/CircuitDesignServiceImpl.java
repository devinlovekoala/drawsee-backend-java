package cn.yifan.drawsee.service.business;

import cn.yifan.drawsee.exception.BusinessException;
import cn.yifan.drawsee.mapper.CircuitDesignMapper;
import cn.yifan.drawsee.pojo.entity.CircuitDesign;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * @FileName CircuitDesignServiceImpl
 * @Description 电路设计服务实现类
 * @Author yifan
 * @date 2025-07-18 16:30
 **/
@Slf4j
@Service
public class CircuitDesignServiceImpl implements CircuitDesignService {

    @Autowired
    private CircuitDesignMapper circuitDesignMapper;

    @Autowired
    private ObjectMapper objectMapper;

    @Override
    public Map<String, Object> saveCircuitDesign(Long userId, CircuitDesign circuitDesign) {
        // 结果Map
        Map<String, Object> result = new HashMap<>(2);
        
        try {
            // 设置或更新元数据
            if (circuitDesign.getMetadata() == null) {
                CircuitDesign.CircuitMetadata metadata = new CircuitDesign.CircuitMetadata();
                metadata.setTitle("电路设计");
                metadata.setDescription("使用DrawSee创建的电路");
                metadata.setCreatedAt(new Date().toString());
                metadata.setUpdatedAt(new Date().toString());
                circuitDesign.setMetadata(metadata);
            } else {
                if (circuitDesign.getMetadata().getUpdatedAt() == null) {
                    circuitDesign.getMetadata().setUpdatedAt(new Date().toString());
                }
            }

            // 将CircuitDesign对象转换为JSON字符串
            String circuitDesignJson = objectMapper.writeValueAsString(circuitDesign);

            // 准备参数
            Map<String, Object> params = new HashMap<>(5);
            params.put("userId", userId);
            params.put("title", circuitDesign.getMetadata().getTitle());
            params.put("description", circuitDesign.getMetadata().getDescription());
            params.put("data", circuitDesignJson);

            // 保存到数据库
            int rows = circuitDesignMapper.saveCircuitDesign(params);
            if (rows > 0) {
                result.put("id", params.get("id").toString());
                result.put("success", true);
            } else {
                result.put("id", "");
                result.put("success", false);
                log.error("保存电路设计失败，未影响任何行");
            }
        } catch (JsonProcessingException e) {
            log.error("保存电路设计失败，序列化异常", e);
            result.put("id", "");
            result.put("success", false);
        } catch (Exception e) {
            log.error("保存电路设计失败", e);
            result.put("id", "");
            result.put("success", false);
        }
        
        return result;
    }

    @Override
    public Map<String, Object> getCircuitDesigns(Long userId) {
        Map<String, Object> result = new ConcurrentHashMap<>();
        List<Map<String, Object>> designs = new ArrayList<>();
        
        try {
            // 从数据库获取电路设计列表
            List<Map<String, Object>> dbDesigns = circuitDesignMapper.getCircuitDesignsByUserId(userId);
            
            // 转换为前端需要的格式
            for (Map<String, Object> design : dbDesigns) {
                Map<String, Object> designItem = new HashMap<>(4);
                designItem.put("id", design.get("id").toString());
                designItem.put("title", design.get("title"));
                designItem.put("createdAt", design.get("created_at").toString());
                designItem.put("updatedAt", design.get("updated_at").toString());
                designs.add(designItem);
            }
        } catch (Exception e) {
            log.error("获取电路设计列表失败", e);
        }
        
        result.put("designs", designs);
        return result;
    }

    @Override
    public CircuitDesign getCircuitDesignById(Long userId, Long id) {
        try {
            // 获取电路设计数据
            Map<String, Object> design = circuitDesignMapper.getCircuitDesignById(id);
            
            if (design == null) {
                throw new BusinessException("电路设计不存在");
            }
            
            // 验证所有权
            Long designUserId = Long.valueOf(design.get("user_id").toString());
            if (!designUserId.equals(userId)) {
                throw new BusinessException("无权访问该电路设计");
            }
            
            // 解析JSON字符串为CircuitDesign对象
            String data = design.get("data").toString();
            return objectMapper.readValue(data, CircuitDesign.class);
        } catch (BusinessException e) {
            log.error("获取电路设计详情失败: {}", e.getMessage());
            throw e;
        } catch (JsonProcessingException e) {
            log.error("获取电路设计详情失败，解析异常", e);
            throw new BusinessException("电路设计数据格式错误");
        } catch (Exception e) {
            log.error("获取电路设计详情失败", e);
            throw new BusinessException("获取电路设计详情失败");
        }
    }

    @Override
    public Map<String, Object> deleteCircuitDesign(Long userId, Long id) {
        Map<String, Object> result = new ConcurrentHashMap<>();
        
        try {
            // 删除电路设计
            int rows = circuitDesignMapper.deleteCircuitDesign(id, userId);
            result.put("success", rows > 0);
        } catch (Exception e) {
            log.error("删除电路设计失败", e);
            result.put("success", false);
        }
        
        return result;
    }

    @Override
    public Map<String, Object> updateCircuitDesign(Long userId, Long id, CircuitDesign circuitDesign) {
        Map<String, Object> result = new HashMap<>(2);

        try {
            CircuitDesign.CircuitMetadata metadata = circuitDesign.getMetadata();
            if (metadata == null) {
                metadata = new CircuitDesign.CircuitMetadata();
                metadata.setTitle("电路设计");
                metadata.setDescription("使用DrawSee创建的电路");
                metadata.setCreatedAt(new Date().toString());
                circuitDesign.setMetadata(metadata);
            }
            metadata.setUpdatedAt(new Date().toString());

            String circuitDesignJson = objectMapper.writeValueAsString(circuitDesign);

            Map<String, Object> params = new HashMap<>(6);
            params.put("id", id);
            params.put("userId", userId);
            params.put("title", metadata.getTitle());
            params.put("description", metadata.getDescription());
            params.put("data", circuitDesignJson);

            int rows = circuitDesignMapper.updateCircuitDesign(params);
            result.put("id", id.toString());
            result.put("success", rows > 0);

            if (rows == 0) {
                log.warn("更新电路设计失败，未影响任何行. id: {}", id);
            }
        } catch (JsonProcessingException e) {
            log.error("更新电路设计失败，序列化异常", e);
            result.put("id", "");
            result.put("success", false);
        } catch (Exception e) {
            log.error("更新电路设计失败", e);
            result.put("id", "");
            result.put("success", false);
        }

        return result;
    }
}

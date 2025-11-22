package cn.yifan.drawsee.pojo.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;

/**
 * @FileName CircuitNodeVO
 * @Description 电路节点标号信息VO
 * @Author yifan
 * @date 2025-04-13 14:36
 **/

@Data
@NoArgsConstructor
@AllArgsConstructor
public class CircuitNodeVO implements Serializable {
    
    @Serial
    private static final long serialVersionUID = 1L;
    
    /**
     * 节点列表
     */
    private List<CircuitNode> nodes;
    
    /**
     * 电路节点信息
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CircuitNode {
        /**
         * 节点标识
         */
        private String nodeId;
        
        /**
         * 节点名称
         */
        private String nodeName;
        
        /**
         * 节点描述
         */
        private String description;
        
        /**
         * 节点相关联的元件ID列表
         */
        private List<String> elementIds;
        
        /**
         * 节点相关联的端口ID列表
         */
        private List<PortReference> ports;
    }
    
    /**
     * 端口引用信息
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PortReference {
        /**
         * 元件ID
         */
        private String elementId;
        
        /**
         * 端口ID
         */
        private String portId;
    }
}
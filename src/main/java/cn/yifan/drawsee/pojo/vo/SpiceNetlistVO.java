package cn.yifan.drawsee.pojo.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * @FileName SpiceNetlistVO
 * @Description SPICE网表视图对象
 * @Author yifan
 * @date 2025-07-18 15:10
 **/
@Data
@NoArgsConstructor
@AllArgsConstructor
public class SpiceNetlistVO {
    /**
     * 完整的SPICE网表
     */
    private String netlist;
    
    /**
     * 网表中的节点列表
     */
    private List<String> nodes;
    
    /**
     * 网表中的元件列表
     */
    private List<String> components;
}
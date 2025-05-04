package cn.yifan.drawsee.pojo.vo;

import cn.yifan.drawsee.pojo.mongo.Knowledge;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;
import java.util.Map;

/**
 * @FileName DocumentProcessResultVO
 * @Description 文档处理结果VO
 * @Author devin
 * @date 2025-08-20 10:40
 **/

@Data
@AllArgsConstructor
@NoArgsConstructor
public class DocumentProcessResultVO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 任务ID
     */
    private String taskId;
    
    /**
     * 处理状态：PENDING, PROCESSING, COMPLETED, FAILED, CANCELLED
     */
    private String status;
    
    /**
     * 处理进度（0-100）
     */
    private Integer progress;
    
    /**
     * 提取的知识点数量
     */
    private Integer extractedCount;
    
    /**
     * 成功导入的知识点数量
     */
    private Integer importedCount;
    
    /**
     * 知识点层级结构 (树形结构)
     */
    private List<KnowledgeNodeVO> knowledgeStructure;
    
    /**
     * 错误信息（如果处理失败）
     */
    private String errorMessage;
    
    /**
     * 额外信息，如处理时间、提取的关键词等
     */
    private Map<String, Object> additionalInfo;
    
    /**
     * 知识点节点VO（内部类）
     */
    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    public static class KnowledgeNodeVO implements Serializable {
        
        @Serial
        private static final long serialVersionUID = 1L;
        
        /**
         * 知识点ID
         */
        private String id;
        
        /**
         * 知识点标题
         */
        private String title;
        
        /**
         * 知识点描述
         */
        private String description;
        
        /**
         * 层级深度（1-5）
         */
        private Integer level;
        
        /**
         * 父节点ID
         */
        private String parentId;
        
        /**
         * 子知识点
         */
        private List<KnowledgeNodeVO> children;
        
        /**
         * 关键词列表
         */
        private List<String> keywords;
        
        /**
         * 自动生成的内容摘要
         */
        private String summary;
    }
} 
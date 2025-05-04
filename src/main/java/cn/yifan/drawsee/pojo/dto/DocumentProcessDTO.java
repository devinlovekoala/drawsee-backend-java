package cn.yifan.drawsee.pojo.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;

/**
 * @FileName DocumentProcessDTO
 * @Description 文档处理参数DTO
 * @Author devin
 * @date 2025-08-20 10:35
 **/

@Data
@AllArgsConstructor
@NoArgsConstructor
public class DocumentProcessDTO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 知识库ID
     */
    private String knowledgeBaseId;
    
    /**
     * 处理选项，JSON格式，可包含以下选项：
     * - extractKeywords: 是否提取关键词(true/false)
     * - buildKnowledgeGraph: 是否构建知识图谱(true/false)
     * - maxDepth: 提取的最大层级深度(1-5)
     * - aiModel: 使用的AI模型(basic/advanced)
     */
    private String options;
    
    /**
     * 文档语言（默认为中文）
     */
    private String language = "zh";
    
    /**
     * 是否自动分配优先级（根据章节层级自动分配）
     */
    private Boolean autoAssignPriority = true;
    
    /**
     * 是否自动创建知识点关联
     */
    private Boolean autoCreateRelations = true;
    
    /**
     * 分析深度，决定AI提取知识点的层级深度（1-5，默认为3）
     */
    private Integer analysisDepth = 3;
} 
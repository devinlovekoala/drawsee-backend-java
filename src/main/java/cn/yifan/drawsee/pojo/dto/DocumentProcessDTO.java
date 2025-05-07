package cn.yifan.drawsee.pojo.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;

/**
 * @FileName DocumentProcessDTO
 * @Description 文档处理参数DTO，优化用于处理教材目录页
 * @Author devin
 * @date 2025-08-20 10:35
 * @update 2025-09-01 13:20 简化为仅处理教材目录页PDF
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
     * 目录页分析选项，JSON格式，可包含以下选项：
     * - detectChapterNumbers: 是否检测章节编号(true/false)
     * - trimPageNumbers: 是否移除页码(true/false)
     * - aiModel: 使用的AI模型类型(basic/advanced)
     */
    private String options = "{}";
    
    /**
     * 文档语言（默认为中文）
     */
    private String language = "zh";
    
    /**
     * 是否自动创建知识点层级关系
     */
    private Boolean autoCreateRelations = true;
    
    /**
     * 分析深度，决定目录层级深度（1-5，默认为3）
     * 1: 仅章节
     * 2: 章节和小节
     * 3: 章节、小节和子节
     * 4-5: 更深层级结构
     */
    private Integer analysisDepth = 3;
    
    /**
     * 教材类型（如：小学语文、初中数学等）
     */
    private String textbookType;
    
    /**
     * 年级（如：一年级、初一等）
     */
    private String grade;
    
    /**
     * 学期（上学期/下学期）
     */
    private String semester;
    
    /**
     * 页面范围，用于指定PDF文档中目录所在的页面范围
     * 当上传完整教材PDF时，可以指定目录所在的页面范围，以便系统只处理这些页面
     */
    private PageRange pageRange;
    
    /**
     * 页面范围内部类
     */
    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    public static class PageRange implements Serializable {
        private static final long serialVersionUID = 1L;
        
        /**
         * 起始页码（从1开始）
         */
        private int start = 1;
        
        /**
         * 结束页码
         */
        private int end = 5;
    }
} 
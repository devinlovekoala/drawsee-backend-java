package cn.yifan.drawsee.pojo.entity;

import lombok.Data;
import java.io.Serializable;
import java.util.Date;

/**
 * 用户文档实体类
 * 用于存储用户上传的实验任务描述文档等信息
 * 
 * @author devin
 * @date 2025-07-25 18:30
 */
@Data
public class UserDocument implements Serializable {
    
    /**
     * 主键ID
     */
    private Long id;
    
    /**
     * 文档UUID
     */
    private String uuid;
    
    /**
     * 所属用户ID
     */
    private Long userId;
    
    /**
     * 文档类型
     * pdf - PDF文档
     * docx - Word文档
     * txt - 文本文档
     */
    private String documentType;
    
    /**
     * 文档标题
     */
    private String title;
    
    /**
     * 文档描述
     */
    private String description;
    
    /**
     * 文档URL
     */
    private String fileUrl;
    
    /**
     * MinIO对象存储路径
     */
    private String objectPath;
    
    /**
     * 文件大小（字节）
     */
    private Long fileSize;
    
    /**
     * 文档标签，用于分类
     */
    private String tags;
    
    /**
     * 创建时间
     */
    private Date createdAt;
    
    /**
     * 更新时间
     */
    private Date updatedAt;
    
    /**
     * 是否已删除
     */
    private Boolean isDeleted;
} 
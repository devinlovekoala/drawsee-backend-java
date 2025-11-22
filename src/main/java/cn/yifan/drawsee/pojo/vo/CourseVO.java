package cn.yifan.drawsee.pojo.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;
import java.util.Date;
import java.util.List;

/**
 * @FileName CourseVO
 * @Description 课程的VO类
 * @Author yifan
 * @date 2025-03-28 10:59
 **/

@Data
@AllArgsConstructor
@NoArgsConstructor
public class CourseVO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private String id;

    private String name;

    private String description;
    
    private String classCode;
    
    private String code;
    
    private String subject;

    private Long creatorId;
    
    private String creatorRole;

    private Date createdAt;

    private Date updatedAt;
    
    private Integer studentCount;
    
    private List<String> knowledgeBaseIds;
    
    private List<KnowledgeBaseVO> knowledgeBases;
    
    private Boolean isPublished;  // 是否有已发布的知识库
} 
package cn.yifan.drawsee.pojo.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * @FileName AddKnowledgeDTO
 * @Description 添加知识点DTO
 * @Author devin
 * @date 2025-03-30 11:10
 **/

@Data
@AllArgsConstructor
@NoArgsConstructor
public class AddKnowledgeDTO {
    
    private String name;
    
    private String subject;
    
    private Integer level;
    
    private String parentId;
    
    private List<String> aliases;
    
}
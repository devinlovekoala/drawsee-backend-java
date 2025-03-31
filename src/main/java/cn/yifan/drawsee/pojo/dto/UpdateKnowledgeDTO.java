package cn.yifan.drawsee.pojo.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * @FileName UpdateKnowledgeDTO
 * @Description 更新知识点DTO
 * @Author devin
 * @date 2025-03-30 11:15
 **/

@Data
@AllArgsConstructor
@NoArgsConstructor
public class UpdateKnowledgeDTO {
    
    private String name;
    
    private String subject;
    
    private Integer level;
    
    private String parentId;
    
    private List<String> aliases;
    
}
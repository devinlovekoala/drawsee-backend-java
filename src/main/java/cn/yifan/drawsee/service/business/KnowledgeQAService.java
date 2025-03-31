package cn.yifan.drawsee.service.business;

import cn.yifan.drawsee.pojo.dto.CreateAiTaskDTO;

/**
 * 知识问答服务接口
 * 
 * @author yifan
 * @date 2024/03/19
 */
public interface KnowledgeQAService {
    
    /**
     * 处理知识问答请求
     * 
     * @param createAiTaskDTO AI任务创建DTO
     * @return 处理后的问答结果
     */
    String processKnowledgeQuery(CreateAiTaskDTO createAiTaskDTO);

    /**
     * 获取知识问答的置信度
     * 
     * @param createAiTaskDTO AI任务创建DTO
     * @return 置信度评分（0-1之间的浮点数）
     */
    double getConfidenceScore(CreateAiTaskDTO createAiTaskDTO);

    /**
     * 获取知识来源引用
     * 
     * @param createAiTaskDTO AI任务创建DTO
     * @return 知识来源的引用信息
     */
    String getKnowledgeSource(CreateAiTaskDTO createAiTaskDTO);
}
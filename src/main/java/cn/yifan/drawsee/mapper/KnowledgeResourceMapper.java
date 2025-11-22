package cn.yifan.drawsee.mapper;

import cn.yifan.drawsee.pojo.entity.KnowledgeResource;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Map;

/**
 * 知识资源Mapper接口
 * 
 * @author yifan
 * @date 2025-03-28 20:00
 */
@Mapper
public interface KnowledgeResourceMapper {

    /**
     * 插入资源
     * 
     * @param resource 资源对象
     * @return 影响行数
     */
    int insert(KnowledgeResource resource);
    
    /**
     * 更新资源
     * 
     * @param resource 资源对象
     * @return 影响行数
     */
    int update(KnowledgeResource resource);
    
    /**
     * 根据ID查询资源
     * 
     * @param id 资源ID
     * @return 资源对象
     */
    KnowledgeResource getById(@Param("id") String id);
    
    /**
     * 根据知识库ID查询资源
     * 
     * @param knowledgeBaseId 知识库ID
     * @param isDeleted 是否删除
     * @return 资源列表
     */
    List<KnowledgeResource> getByKnowledgeBaseId(@Param("knowledgeBaseId") String knowledgeBaseId, @Param("isDeleted") boolean isDeleted);
    
    /**
     * 根据知识库ID和资源类型查询资源
     * 
     * @param knowledgeBaseId 知识库ID
     * @param resourceType 资源类型
     * @param isDeleted 是否删除
     * @return 资源列表
     */
    List<KnowledgeResource> getByKnowledgeBaseIdAndType(
            @Param("knowledgeBaseId") String knowledgeBaseId,
            @Param("resourceType") String resourceType,
            @Param("isDeleted") boolean isDeleted);
    
    /**
     * 根据URL查询资源
     * 
     * @param url 资源URL
     * @param isDeleted 是否删除
     * @return 资源对象
     */
    KnowledgeResource getByUrl(@Param("url") String url, @Param("isDeleted") boolean isDeleted);
    
    /**
     * 统计知识库资源数量
     * 
     * @param knowledgeBaseId 知识库ID
     * @return 类型和数量的映射
     */
    List<Map<String, Object>> countResourcesByKnowledgeBaseId(@Param("knowledgeBaseId") String knowledgeBaseId);
    
    /**
     * 逻辑删除资源
     * 
     * @param id 资源ID
     * @return 影响行数
     */
    int logicDelete(@Param("id") String id);
    
    /**
     * 根据知识点ID查询资源
     * 
     * @param knowledgeId 知识点ID
     * @param isDeleted 是否删除
     * @return 资源列表
     */
    List<KnowledgeResource> getByKnowledgeId(@Param("knowledgeId") String knowledgeId, @Param("isDeleted") boolean isDeleted);
} 
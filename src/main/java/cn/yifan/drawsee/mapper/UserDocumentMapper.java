package cn.yifan.drawsee.mapper;

import cn.yifan.drawsee.pojo.entity.UserDocument;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 用户文档Mapper接口
 * 
 * @author yifan
 * @date 2025-07-25 18:40
 */
@Mapper
public interface UserDocumentMapper {
    
    /**
     * 插入用户文档
     * 
     * @param userDocument 用户文档对象
     * @return 影响的行数
     */
    int insert(UserDocument userDocument);
    
    /**
     * 根据ID查询用户文档
     * 
     * @param id 文档ID
     * @return 用户文档对象
     */
    UserDocument getById(@Param("id") Long id);
    
    /**
     * 根据UUID查询用户文档
     * 
     * @param uuid 文档UUID
     * @return 用户文档对象
     */
    UserDocument getByUuid(@Param("uuid") String uuid);
    
    /**
     * 更新用户文档
     * 
     * @param userDocument 用户文档对象
     * @return 影响的行数
     */
    int update(UserDocument userDocument);
    
    /**
     * 逻辑删除用户文档
     * 
     * @param id 文档ID
     * @return 影响的行数
     */
    int delete(@Param("id") Long id);
    
    /**
     * 获取用户的所有文档
     * 
     * @param userId 用户ID
     * @return 用户文档列表
     */
    List<UserDocument> getByUserId(@Param("userId") Long userId);
    
    /**
     * 根据文档类型获取用户文档
     * 
     * @param userId 用户ID
     * @param documentType 文档类型
     * @return 用户文档列表
     */
    List<UserDocument> getByUserIdAndType(@Param("userId") Long userId, @Param("documentType") String documentType);
} 
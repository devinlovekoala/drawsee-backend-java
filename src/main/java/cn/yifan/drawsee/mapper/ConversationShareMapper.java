package cn.yifan.drawsee.mapper;

import cn.yifan.drawsee.pojo.entity.ConversationShare;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * @FileName ConversationShareMapper
 * @Description 会话分享Mapper接口
 * @Author devin
 * @date 2026-02-25
 */

@Mapper
public interface ConversationShareMapper {

    ConversationShare getById(Long id);

    ConversationShare getByToken(@Param("shareToken") String shareToken);

    List<ConversationShare> listByClassId(@Param("classId") Long classId);

    void insert(ConversationShare conversationShare);

    void update(ConversationShare conversationShare);

    void increaseViewCount(@Param("id") Long id);
}

package cn.yifan.drawsee.mapper;

import cn.yifan.drawsee.constant.RedisKey;
import cn.yifan.drawsee.pojo.entity.InvitationCode;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;

/**
 * @FileName InvitationCodeMapper @Description @Author yifan
 *
 * @date 2025-03-25 08:50
 */
@Mapper
public interface InvitationCodeMapper {

  InvitationCode getById(Long id);

  InvitationCode getByCode(String code);

  InvitationCode getByUsedBy(Long usedBy);

  @Cacheable(value = RedisKey.INVITATION_CODE_PAGE_KEY, key = "#offset + '-' + #size")
  List<InvitationCode> getByPage(int offset, int size);

  @CacheEvict(value = RedisKey.INVITATION_CODE_PAGE_KEY, allEntries = true)
  void insert(InvitationCode invitationCode);

  @CacheEvict(value = RedisKey.INVITATION_CODE_PAGE_KEY, allEntries = true)
  void update(InvitationCode invitationCode);
}

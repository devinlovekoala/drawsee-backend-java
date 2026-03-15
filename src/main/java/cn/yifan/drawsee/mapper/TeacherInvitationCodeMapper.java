package cn.yifan.drawsee.mapper;

import cn.yifan.drawsee.constant.RedisKey;
import cn.yifan.drawsee.pojo.entity.TeacherInvitationCode;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;

/**
 * @FileName TeacherInvitationCodeMapper @Description 教师邀请码Mapper接口 @Author yifan
 *
 * @date 2025-06-11 14:25
 */
@Mapper
public interface TeacherInvitationCodeMapper {

  TeacherInvitationCode getById(Long id);

  TeacherInvitationCode getByCode(String code);

  TeacherInvitationCode getByUsedBy(Long usedBy);

  @Cacheable(value = RedisKey.TEACHER_INVITATION_CODE_PAGE_KEY, key = "#offset + '-' + #size")
  List<TeacherInvitationCode> getByPage(int offset, int size);

  @CacheEvict(value = RedisKey.TEACHER_INVITATION_CODE_PAGE_KEY, allEntries = true)
  void insert(TeacherInvitationCode teacherInvitationCode);

  @CacheEvict(value = RedisKey.TEACHER_INVITATION_CODE_PAGE_KEY, allEntries = true)
  void update(TeacherInvitationCode teacherInvitationCode);
}

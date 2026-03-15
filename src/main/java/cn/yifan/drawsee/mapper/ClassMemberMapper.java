package cn.yifan.drawsee.mapper;

import cn.yifan.drawsee.pojo.entity.ClassMember;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;

/**
 * @FileName ClassMemberMapper @Description 班级成员Mapper接口 @Author yifan
 *
 * @date 2025-06-10 10:35
 */
@Mapper
public interface ClassMemberMapper {

  void insert(ClassMember classMember);

  ClassMember getById(Long id);

  ClassMember getByClassIdAndUserId(Long classId, Long userId);

  List<ClassMember> getByClassId(Long classId);

  List<ClassMember> getByUserId(Long userId);

  void update(ClassMember classMember);
}

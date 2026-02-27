package cn.yifan.drawsee.service.business;

import cn.dev33.satoken.stp.StpUtil;
import cn.yifan.drawsee.constant.UserRole;
import cn.yifan.drawsee.exception.ApiError;
import cn.yifan.drawsee.exception.ApiException;
import cn.yifan.drawsee.mapper.ClassMapper;
import cn.yifan.drawsee.mapper.ClassMemberMapper;
import cn.yifan.drawsee.mapper.ConversationMapper;
import cn.yifan.drawsee.mapper.ConversationShareMapper;
import cn.yifan.drawsee.mapper.CourseMapper;
import cn.yifan.drawsee.mapper.NodeMapper;
import cn.yifan.drawsee.mapper.UserMapper;
import cn.yifan.drawsee.pojo.XYPosition;
import cn.yifan.drawsee.pojo.dto.CreateConversationShareDTO;
import cn.yifan.drawsee.pojo.entity.Class;
import cn.yifan.drawsee.pojo.entity.ClassMember;
import cn.yifan.drawsee.pojo.entity.Conversation;
import cn.yifan.drawsee.pojo.entity.ConversationShare;
import cn.yifan.drawsee.pojo.entity.Course;
import cn.yifan.drawsee.pojo.entity.Node;
import cn.yifan.drawsee.pojo.entity.User;
import cn.yifan.drawsee.pojo.vo.ClassStudentVO;
import cn.yifan.drawsee.pojo.vo.*;
import cn.yifan.drawsee.util.UUIDUtils;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * @FileName ConversationShareService
 * @Description 会话分享服务
 * @Author devin
 * @date 2026-02-25
 */

@Service
@Slf4j
public class ConversationShareService {

    @Autowired
    private ConversationMapper conversationMapper;
    @Autowired
    private ConversationShareMapper conversationShareMapper;
    @Autowired
    private NodeMapper nodeMapper;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private ClassMapper classMapper;
    @Autowired
    private ClassMemberMapper classMemberMapper;
    @Autowired
    private CourseMapper courseMapper;
    @Autowired
    private UserRoleService userRoleService;
    @Autowired
    private UserMapper userMapper;

    public ConversationShareVO createShare(Long convId, CreateConversationShareDTO dto) {
        Long userId = StpUtil.getLoginIdAsLong();
        Conversation conversation = conversationMapper.getById(convId);
        if (conversation == null || Boolean.TRUE.equals(conversation.getIsDeleted())) {
            throw new ApiException(ApiError.CONVERSATION_NOT_EXISTED, "文件不能为空");
        }
        if (!userId.equals(conversation.getUserId())) {
            throw new ApiException(ApiError.PERMISSION_DENIED, "文件不能为空");
        }

        Long classId = resolveClassId(dto != null ? dto.getClassId() : null);
        if (classId != null) {
            validateClassAccess(userId, classId);
        }

        Boolean allowContinue = dto != null && dto.getAllowContinue() != null ? dto.getAllowContinue() : Boolean.TRUE;

        String shareToken = generateUniqueToken();
        ConversationShare share = new ConversationShare();
        share.setConvId(convId);
        share.setUserId(userId);
        share.setClassId(classId);
        share.setShareToken(shareToken);
        share.setAllowContinue(allowContinue);
        share.setViewCount(0L);
        share.setIsDeleted(false);
        conversationShareMapper.insert(share);

        ConversationShareVO shareVO = new ConversationShareVO();
        BeanUtils.copyProperties(share, shareVO);
        shareVO.setSharePath(buildSharePath(shareToken));
        return shareVO;
    }

    public ShareConversationVO getShareByToken(String shareToken) {
        ConversationShare share = conversationShareMapper.getByToken(shareToken);
        if (share == null || Boolean.TRUE.equals(share.getIsDeleted())) {
            throw new ApiException(ApiError.SHARE_NOT_EXISTED, "文件不能为空");
        }
        Conversation conversation = conversationMapper.getById(share.getConvId());
        if (conversation == null || Boolean.TRUE.equals(conversation.getIsDeleted())) {
            throw new ApiException(ApiError.CONVERSATION_NOT_EXISTED, "文件不能为空");
        }

        List<NodeVO> nodeVOS = convertToNodeVOs(nodeMapper.getByConvId(share.getConvId()));

        ConversationVO conversationVO = new ConversationVO();
        BeanUtils.copyProperties(conversation, conversationVO);

        ConversationShareVO shareVO = new ConversationShareVO();
        BeanUtils.copyProperties(share, shareVO);
        shareVO.setSharePath(buildSharePath(shareToken));

        conversationShareMapper.increaseViewCount(share.getId());

        return new ShareConversationVO(conversationVO, nodeVOS, shareVO);
    }

    @Transactional
    public ConversationForkVO forkSharedConversation(String shareToken) {
        Long userId = StpUtil.getLoginIdAsLong();
        ConversationShare share = conversationShareMapper.getByToken(shareToken);
        if (share == null || Boolean.TRUE.equals(share.getIsDeleted())) {
            throw new ApiException(ApiError.SHARE_NOT_EXISTED, "文件不能为空");
        }
        if (!Boolean.TRUE.equals(share.getAllowContinue())) {
            throw new ApiException(ApiError.SHARE_NOT_ALLOWED, "文件不能为空");
        }

        Conversation conversation = conversationMapper.getById(share.getConvId());
        if (conversation == null || Boolean.TRUE.equals(conversation.getIsDeleted())) {
            throw new ApiException(ApiError.CONVERSATION_NOT_EXISTED, "文件不能为空");
        }

        String title = conversation.getTitle();
        if (title == null || title.isBlank()) {
            title = "共享会话";
        }
        Conversation newConversation = new Conversation("共享副本 - " + title, userId);
        conversationMapper.insert(newConversation);

        List<Node> nodes = nodeMapper.getByConvId(conversation.getId());
        Map<Long, Long> nodeIdMap = new HashMap<>();
        List<Node> remaining = new ArrayList<>(nodes);
        boolean progress;
        do {
            progress = false;
            for (int i = 0; i < remaining.size(); i++) {
                Node node = remaining.get(i);
                Long parentId = node.getParentId();
                if (parentId == null || nodeIdMap.containsKey(parentId)) {
                    Long newParentId = parentId == null ? null : nodeIdMap.get(parentId);
                    Node newNode = new Node(
                            node.getType(),
                            node.getData(),
                            node.getPosition(),
                            newParentId,
                            userId,
                            newConversation.getId(),
                            false
                    );
                    nodeMapper.insert(newNode);
                    if (node.getHeight() != null) {
                        newNode.setHeight(node.getHeight());
                        nodeMapper.update(newNode);
                    }
                    nodeIdMap.put(node.getId(), newNode.getId());
                    remaining.remove(i);
                    i--;
                    progress = true;
                }
            }
        } while (progress);

        if (!remaining.isEmpty()) {
            log.warn("分享会话复制失败，存在无法映射的父节点: shareToken={}, convId={}", shareToken, conversation.getId());
            throw new ApiException(ApiError.COMMON_ERROR, "文件不能为空");
        }

        ConversationVO conversationVO = new ConversationVO();
        BeanUtils.copyProperties(newConversation, conversationVO);
        conversationVO.setCreatedAt(new Timestamp(System.currentTimeMillis()));
        conversationVO.setUpdatedAt(new Timestamp(System.currentTimeMillis()));

        return new ConversationForkVO(conversationVO);
    }

    public List<ConversationSharePostVO> listClassShares(Long classId) {
        Long userId = StpUtil.getLoginIdAsLong();
        String role = userRoleService.getCurrentUserRole();
        if (!UserRole.ADMIN.equals(role)) {
            Class clazz = classMapper.getById(classId);
            if (clazz == null || Boolean.TRUE.equals(clazz.getIsDeleted())) {
                throw new ApiException(ApiError.PARAM_ERROR, "文件不能为空");
            }
            if (!userId.equals(clazz.getTeacherId())) {
                throw new ApiException(ApiError.PERMISSION_DENIED, "文件不能为空");
            }
        }

        List<ConversationShare> shares = conversationShareMapper.listByClassId(classId);
        List<ConversationSharePostVO> posts = new ArrayList<>();
        for (ConversationShare share : shares) {
            Conversation conversation = conversationMapper.getById(share.getConvId());
            if (conversation == null || Boolean.TRUE.equals(conversation.getIsDeleted())) {
                continue;
            }
            User user = userMapper.getById(share.getUserId());
            ConversationSharePostVO post = new ConversationSharePostVO();
            post.setId(share.getId());
            post.setConvId(share.getConvId());
            post.setUserId(share.getUserId());
            post.setUsername(user != null ? user.getUsername() : null);
            post.setClassId(share.getClassId());
            post.setTitle(conversation.getTitle());
            post.setShareToken(share.getShareToken());
            post.setSharePath(buildSharePath(share.getShareToken()));
            post.setAllowContinue(share.getAllowContinue());
            post.setViewCount(share.getViewCount());
            post.setCreatedAt(share.getCreatedAt());
            post.setUpdatedAt(share.getUpdatedAt());
            posts.add(post);
        }
        return posts;
    }

    public List<ConversationSharePostVO> listClassSharesByCourseOrClass(String classIdOrCourseId) {
        if (classIdOrCourseId == null || classIdOrCourseId.isBlank()) {
            throw new ApiException(ApiError.PARAM_ERROR, "文件不能为空");
        }
        Long classId = resolveClassId(classIdOrCourseId);
        if (classId == null) {
            return new ArrayList<>();
        }
        List<ConversationSharePostVO> posts = listClassShares(classId);
        if (!posts.isEmpty()) {
            return posts;
        }

        // 兼容历史数据：分享记录未设置class_id，尝试按课程成员回填
        Class clazz = classMapper.getById(classId);
        if (clazz == null || clazz.getClassCode() == null) {
            return posts;
        }
        Course course = courseMapper.getByClassCode(clazz.getClassCode());
        if (course == null || course.getStudentIds() == null || course.getStudentIds().isEmpty()) {
            return posts;
        }

        List<Long> userIds = new ArrayList<>();
        for (Object obj : course.getStudentIds()) {
            if (obj == null) {
                continue;
            }
            try {
                userIds.add(Long.parseLong(String.valueOf(obj)));
            } catch (NumberFormatException ignore) {
                // skip invalid
            }
        }
        if (userIds.isEmpty()) {
            return posts;
        }

        List<ConversationShare> shares = conversationShareMapper.listByUserIdsWithoutClassId(userIds);
        for (ConversationShare share : shares) {
            share.setClassId(classId);
            conversationShareMapper.update(share);
        }
        return listClassShares(classId);
    }

    private Long resolveClassId(String classIdOrCourseId) {
        if (classIdOrCourseId == null || classIdOrCourseId.isBlank()) {
            return null;
        }
        try {
            return Long.parseLong(classIdOrCourseId);
        } catch (NumberFormatException ignore) {
            // fallthrough
        }

        Course course = courseMapper.getById(classIdOrCourseId);
        if (course == null || Boolean.TRUE.equals(course.getIsDeleted())) {
            return null;
        }
        String classCode = course.getClassCode();
        if (classCode == null || classCode.isBlank()) {
            return null;
        }
        Class clazz = classMapper.getByClassCode(classCode);
        if (clazz == null) {
            clazz = new Class(course.getName(), course.getDescription(), classCode, course.getCreatorId());
            classMapper.insert(clazz);
        }
        if (Boolean.TRUE.equals(clazz.getIsDeleted())) {
            return null;
        }
        return clazz.getId();
    }

    public List<ClassStudentVO> listClassStudentsByCourseOrClass(String classIdOrCourseId) {
        Long classId = resolveClassId(classIdOrCourseId);
        if (classId == null) {
            return new ArrayList<>();
        }
        Long userId = StpUtil.getLoginIdAsLong();
        String role = userRoleService.getCurrentUserRole();
        if (!UserRole.ADMIN.equals(role)) {
            Class clazz = classMapper.getById(classId);
            if (clazz == null || Boolean.TRUE.equals(clazz.getIsDeleted())) {
                throw new ApiException(ApiError.PARAM_ERROR, "文件不能为空");
            }
            if (!userId.equals(clazz.getTeacherId())) {
                throw new ApiException(ApiError.PERMISSION_DENIED, "文件不能为空");
            }
        }

        Class clazz = classMapper.getById(classId);
        List<ClassMember> members = classMemberMapper.getByClassId(classId);
        if ((members == null || members.isEmpty()) && clazz != null && clazz.getClassCode() != null) {
            Course course = courseMapper.getByClassCode(clazz.getClassCode());
            if (course != null && course.getStudentIds() != null) {
                for (Object studentIdObj : course.getStudentIds()) {
                    if (studentIdObj == null) {
                        continue;
                    }
                    Long studentId;
                    try {
                        studentId = Long.parseLong(String.valueOf(studentIdObj));
                    } catch (NumberFormatException ignore) {
                        continue;
                    }
                    ClassMember existing = classMemberMapper.getByClassIdAndUserId(classId, studentId);
                    if (existing == null) {
                        classMemberMapper.insert(new ClassMember(classId, studentId));
                    } else if (Boolean.TRUE.equals(existing.getIsDeleted())) {
                        existing.setIsDeleted(false);
                        classMemberMapper.update(existing);
                    }
                }
                members = classMemberMapper.getByClassId(classId);
            }
        }
        List<ClassStudentVO> students = new ArrayList<>();
        for (ClassMember member : members) {
            User user = userMapper.getById(member.getUserId());
            ClassStudentVO vo = new ClassStudentVO();
            vo.setUserId(member.getUserId());
            vo.setUsername(user != null ? user.getUsername() : null);
            vo.setJoinedAt(member.getJoinedAt());
            students.add(vo);
        }
        return students;
    }

    private void validateClassAccess(Long userId, Long classId) {
        Class clazz = classMapper.getById(classId);
        if (clazz == null || Boolean.TRUE.equals(clazz.getIsDeleted())) {
            throw new ApiException(ApiError.PARAM_ERROR, "文件不能为空");
        }
        String role = userRoleService.getCurrentUserRole();
        if (UserRole.ADMIN.equals(role)) {
            return;
        }
        if (userId.equals(clazz.getTeacherId())) {
            return;
        }
        ClassMember member = classMemberMapper.getByClassIdAndUserId(classId, userId);
        if (member == null) {
            throw new ApiException(ApiError.PERMISSION_DENIED, "文件不能为空");
        }
    }

    private String generateUniqueToken() {
        String token;
        do {
            token = UUIDUtils.generateUUID(16);
        } while (conversationShareMapper.getByToken(token) != null);
        return token;
    }

    private String buildSharePath(String token) {
        return "/share/" + token;
    }

    private List<NodeVO> convertToNodeVOs(List<Node> nodes) {
        List<NodeVO> nodeVOS = new ArrayList<>();
        for (Node node : nodes) {
            NodeVO nodeVO = new NodeVO();
            nodeVO.setId(node.getId());
            nodeVO.setType(node.getType());
            TypeReference<Map<String, Object>> dataTypeReference = new TypeReference<Map<String, Object>>() {};
            Map<String, Object> data = null;
            try {
                data = objectMapper.readValue(node.getData(), dataTypeReference);
            } catch (JsonProcessingException e) {
                throw new ApiException(ApiError.SYSTEM_ERROR, "文件不能为空");
            }
            XYPosition position = null;
            try {
                position = objectMapper.readValue(node.getPosition(), XYPosition.class);
            } catch (JsonProcessingException e) {
                throw new ApiException(ApiError.SYSTEM_ERROR, "文件不能为空");
            }
            nodeVO.setData(data);
            nodeVO.setPosition(position);
            nodeVO.setHeight(node.getHeight());
            nodeVO.setParentId(node.getParentId());
            nodeVO.setConvId(node.getConvId());
            nodeVO.setUserId(node.getUserId());
            nodeVO.setCreatedAt(node.getCreatedAt());
            nodeVO.setUpdatedAt(node.getUpdatedAt());
            nodeVOS.add(nodeVO);
        }
        return nodeVOS;
    }
}

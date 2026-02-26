package cn.yifan.drawsee.exception;

import lombok.Getter;

/**
 * @FileName ApiError
 * @Description
 * @Author yifan
 * @date 2025-01-28 17:48
 **/
@Getter
public enum ApiError {

    // admin
    NOT_ADMIN(401, "不是管理员"),
    ADMIN_HAD_EXISTED(409, "管理员已存在"),

    // invitation_code
    INVITATION_CODE_NOT_EXISTED(404, "邀请码不存在"),
    INVITATION_CODE_SEND_FAILED(500, "邀请码发送失败"),
    INVITATION_CODE_ALREADY_USED(409, "邀请码已使用"),

    // file
    FILE_TYPE_NOT_SUPPORTED(400, "文件类型不支持"),
    FILE_UPLOAD_FAILED(500, "文件上传失败"),
    IMAGE_RECOGNIZE_FAILED(500, "图片识别失败"),

    // node
    NODE_NOT_EXISTED(404, "节点不存在"),

    // ai_task
    AI_TASK_NOT_EXISTED(404, "AI任务不存在"),
    AI_TASK_IS_WAITING(409, "AI任务等待中"),
    AI_TASK_IS_FINISHED(409, "finished"),

    // 超过使用额度
    AI_TASK_EXCEED_LIMIT(429, "已达到每日AI任务次数限制"),

    // conversation
    CONVERSATION_NOT_EXISTED(404, "会话不存在"),
    
    // conversation share
    SHARE_NOT_EXISTED(404, "分享不存在"),
    SHARE_NOT_ALLOWED(403, "分享不允许继续"),

    // knowledge
    KNOWLEDGE_HAD_EXISTED(409, "同名称知识点已存在"),
    KNOWLEDGE_NOT_EXISTED(404, "知识点不存在"),
    KNOWLEDGE_PARENT_NOT_EXISTED(404, "父知识点不存在"),
    KNOWLEDGE_NOT_IN_BASE(403, "该知识点不属于指定的知识库"),

    // 参数
    PARAM_ERROR(400, "参数错误"),

    // 系统
    SYSTEM_ERROR(500, "系统错误"),
    COMMON_ERROR(500, "操作失败"),

    // 权限
    NOT_LOGIN(401, "未登录"),
    PERMISSION_DENIED(403, "权限不足"),

    // 登录
    USER_NOT_EXIST(404, "用户名不存在"),
    PASSWORD_ERROR(401, "密码错误"),

    // 注册
    USER_HAD_EXISTED(409, "用户名已存在"),

    // 教师相关错误
    NOT_TEACHER(405501, "用户不是教师"),
    TEACHER_HAD_EXISTED(405502, "教师已存在"),

    // 知识库相关错误
    KNOWLEDGE_BASE_NOT_EXISTED(405601, "知识库不存在"),
    KNOWLEDGE_BASE_HAD_EXISTED(405602, "知识库已存在"),
    KNOWLEDGE_BASE_ALREADY_JOINED(405603, "已加入该知识库"),
    KNOWLEDGE_BASE_JOIN_FAILED(405604, "加入知识库失败"),
    INVALID_INVITATION_CODE(405605, "邀请码无效"),
    KNOWLEDGE_NOT_IN_KNOWLEDGE_BASE(405606, "知识点不在知识库中"),
    
    // 上传相关错误
    UPLOAD_FAILED(405701, "上传失败"),
    INVALID_BILIBILI_URL(405702, "无效的B站链接"),
    RESOURCE_ADD_FAILED(405703, "资源添加失败"),
    RESOURCE_NOT_EXISTED(405704, "资源不存在"),
    RESOURCE_NOT_IN_KNOWLEDGE_BASE(405705, "资源不属于该知识库"),
    
    // 课程相关错误
    COURSE_NOT_EXISTED(405801, "课程不存在"),
    COURSE_HAD_EXISTED(405802, "课程已存在"),
    ALREADY_JOINED(405803, "已加入该课程"),
    INVALID_CLASS_CODE(405804, "班级码无效"),
    JOIN_COURSE_FAILED(405805, "加入课程失败"),

    // 每日限制已达到
    DAILY_LIMIT_EXCEEDED(429, "已达到每日对话次数限制"),

    // Course related errors
    COURSE_NOT_FOUND(4001, "课程不存在"),
    NO_PERMISSION(4002, "没有权限"),
    ALREADY_JOINED_COURSE(4003, "已经加入该课程"),
    
    // RAG相关错误
    RAG_NOT_ENABLED(5001, "RAG功能未启用"),
    RAG_CONVERSION_FAILED(5002, "转换RAG知识库失败"),
    RAG_KNOWLEDGE_CREATE_FAILED(5003, "创建RAG知识库失败"),

    // RAGFlow相关错误
    RAG_SERVICE_DISABLED(5101, "RAGFlow服务未启用"),
    RAG_SERVICE_ERROR(5102, "RAGFlow服务错误"),
    RAG_KNOWLEDGE_NOT_FOUND(5103, "RAGFlow知识库不存在"),
    RAG_DOCUMENT_NOT_FOUND(5104, "RAGFlow文档不存在"),
    RAG_UPLOAD_FAILED(5105, "RAGFlow文档上传失败"),
    RAG_FILE_TYPE_NOT_SUPPORTED(5106, "RAGFlow不支持的文件类型"),
    
    // 用户文档相关错误
    RESOURCE_NOT_FOUND(404, "资源不存在"),
    ACCESS_DENIED(403, "访问被拒绝"),

    // 内部服务认证错误
    UNAUTHORIZED(401, "未授权");

    // 枚举项的参数
    private final Integer code;
    private final String message;

    // 构造函数
    ApiError(Integer code, String message) {
        this.code = code;
        this.message = message;
    }

    // 获取枚举项的参数
    public Integer getCode() {
        return code;
    }

    public String getMessage() {
        return message;
    }

}

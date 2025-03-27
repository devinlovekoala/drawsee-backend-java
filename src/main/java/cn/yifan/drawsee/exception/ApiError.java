package cn.yifan.drawsee.exception;

/**
 * @FileName ApiError
 * @Description
 * @Author yifan
 * @date 2025-01-28 17:48
 **/
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
    AI_TASK_NOT_EXISTED(404, "not_exist"),
    AI_TASK_IS_WAITING(409, "waiting"),
    AI_TASK_IS_FINISHED(409, "finished"),

    // 超过使用额度
    AI_TASK_EXCEED_LIMIT(409, "24小时内您的对话次数已达上限，请明天再试"),

    // conversation
    CONVERSATION_NOT_EXISTED(404, "会话不存在"),

    // knowledge
    KNOWLEDGE_HAD_EXISTED(409, "同名称知识点已存在"),
    KNOWLEDGE_NOT_EXISTED(404, "知识点不存在"),
    KNOWLEDGE_PARENT_NOT_EXISTED(404, "父知识点不存在"),

    // 参数
    PARAM_ERROR(400, "参数错误"),

    // 系统
    SYSTEM_ERROR(500, "服务器内部错误"),

    // 权限
    NOT_LOGIN(401, "未登录"),

    // 登录
    USER_NOT_EXIST(404, "用户名不存在"),
    PASSWORD_ERROR(401, "密码错误"),

    // 注册
    USER_HAD_EXISTED(409, "用户名已存在");

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

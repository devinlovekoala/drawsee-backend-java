-- MySQL数据库初始化脚本
-- 基于当前Java工程所需的字段和逻辑

-- 设置编码和禁用外键检查
SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

-- 用户表
DROP TABLE IF EXISTS `user`;
CREATE TABLE `user` (
  `id` int UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '用户唯一ID',
  `username` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '用户名（唯一）',
  `password` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '密码哈希值',
  `created_at` datetime(0) NULL DEFAULT CURRENT_TIMESTAMP(0) COMMENT '创建时间',
  `updated_at` datetime(0) NULL DEFAULT CURRENT_TIMESTAMP(0) ON UPDATE CURRENT_TIMESTAMP(0) COMMENT '更新时间',
  `is_deleted` tinyint(1) NULL DEFAULT 0 COMMENT '逻辑删除标记（0-未删除，1-已删除）',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `username`(`username`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '用户表' ROW_FORMAT = Dynamic;

-- 会话表
DROP TABLE IF EXISTS `conversation`;
CREATE TABLE `conversation` (
  `id` int UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '会话唯一ID',
  `title` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '会话标题',
  `user_id` int UNSIGNED NOT NULL COMMENT '所属用户ID',
  `created_at` datetime(0) NULL DEFAULT CURRENT_TIMESTAMP(0) COMMENT '创建时间',
  `updated_at` datetime(0) NULL DEFAULT CURRENT_TIMESTAMP(0) ON UPDATE CURRENT_TIMESTAMP(0) COMMENT '更新时间',
  `is_deleted` tinyint(1) NULL DEFAULT 0 COMMENT '逻辑删除标记',
  PRIMARY KEY (`id`) USING BTREE,
  INDEX `idx_conversation_user_id`(`user_id`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '会话表' ROW_FORMAT = Dynamic;

-- 节点表
DROP TABLE IF EXISTS `node`;
CREATE TABLE `node` (
  `id` int UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '节点唯一ID',
  `type` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '节点类型（如文本、图片等）',
  `data` mediumtext CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '节点内容（长文本支持）',
  `position` text CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '节点位置坐标（如JSON格式：{\"x\":100, \"y\":200}）',
  `height` int UNSIGNED NULL DEFAULT NULL COMMENT '节点高度',
  `parent_id` int UNSIGNED NULL DEFAULT NULL COMMENT '父节点ID（自引用外键）',
  `conv_id` int UNSIGNED NOT NULL COMMENT '所属会话ID',
  `user_id` int UNSIGNED NOT NULL COMMENT '所属用户ID',
  `created_at` datetime(0) NULL DEFAULT CURRENT_TIMESTAMP(0) COMMENT '创建时间',
  `updated_at` datetime(0) NULL DEFAULT CURRENT_TIMESTAMP(0) ON UPDATE CURRENT_TIMESTAMP(0) COMMENT '更新时间',
  `is_deleted` tinyint(1) NULL DEFAULT 0 COMMENT '逻辑删除标记',
  PRIMARY KEY (`id`) USING BTREE,
  INDEX `idx_node_parent_id`(`parent_id`) USING BTREE,
  INDEX `idx_node_user_id`(`user_id`) USING BTREE,
  INDEX `idx_node_conv_id`(`conv_id`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '节点表' ROW_FORMAT = Dynamic;

-- AI任务表
DROP TABLE IF EXISTS `ai_task`;
CREATE TABLE `ai_task` (
  `id` int UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '任务唯一ID',
  `type` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '任务类型',
  `data` mediumtext CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '任务内容（长文本支持）',
  `result` mediumtext CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '任务结果（长文本支持）',
  `status` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '任务状态',
  `tokens` int UNSIGNED NULL DEFAULT NULL COMMENT '消耗的Token数',
  `user_id` int UNSIGNED NOT NULL COMMENT '任务所属用户ID',
  `conv_id` int UNSIGNED NOT NULL COMMENT '任务所属会话ID',
  `created_at` datetime(0) NULL DEFAULT CURRENT_TIMESTAMP(0) COMMENT '创建时间',
  `updated_at` datetime(0) NULL DEFAULT CURRENT_TIMESTAMP(0) ON UPDATE CURRENT_TIMESTAMP(0) COMMENT '更新时间',
  `is_deleted` tinyint(1) NULL DEFAULT 0 COMMENT '逻辑删除标记',
  PRIMARY KEY (`id`) USING BTREE,
  INDEX `idx_ai_task_user_id`(`user_id`) USING BTREE,
  INDEX `idx_ai_task_conv_id`(`conv_id`) USING BTREE,
  INDEX `idx_ai_task_user_id_conv_id_status`(`user_id`, `conv_id`, `status`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '任务表' ROW_FORMAT = Dynamic;

-- 管理员表
DROP TABLE IF EXISTS `admin`;
CREATE TABLE `admin` (
  `id` int NOT NULL AUTO_INCREMENT,
  `user_id` int UNSIGNED NOT NULL COMMENT '对应用户ID',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `user_id`(`user_id`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci ROW_FORMAT = Dynamic;

-- 邀请码表
DROP TABLE IF EXISTS `invitation_code`;
CREATE TABLE `invitation_code` (
  `id` int NOT NULL AUTO_INCREMENT,
  `code` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '邀请码（唯一）',
  `created_at` datetime(0) NULL DEFAULT CURRENT_TIMESTAMP(0),
  `used_by` int NULL DEFAULT NULL COMMENT '使用者的用户ID',
  `used_at` datetime(0) NULL DEFAULT NULL,
  `is_active` tinyint(1) NULL DEFAULT 1 COMMENT '是否可用（可做软删除）',
  `sent_user_name` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '发送对象的名字',
  `sent_email` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '发送对象的邮箱',
  `last_sent_at` datetime(0) NULL DEFAULT NULL COMMENT '上次发送的时间',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `code`(`code`) USING BTREE,
  INDEX `idx_invitation_code_used_by`(`used_by`) USING BTREE,
  INDEX `idx_invitation_code_created_at`(`created_at`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci ROW_FORMAT = Dynamic;

-- 教师表
DROP TABLE IF EXISTS `teacher`;
CREATE TABLE `teacher` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '主键',
  `user_id` int UNSIGNED NOT NULL COMMENT '用户ID',
  `title` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '职称',
  `organization` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '所属组织机构',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `idx_user_id`(`user_id`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '教师表' ROW_FORMAT = Dynamic;

-- 教师邀请码表
DROP TABLE IF EXISTS `teacher_invitation_code`;
CREATE TABLE `teacher_invitation_code` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '主键',
  `code` varchar(20) NOT NULL COMMENT '邀请码',
  `created_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `created_by` bigint(20) NOT NULL COMMENT '创建人ID',
  `used_by` bigint(20) DEFAULT NULL COMMENT '使用人ID',
  `used_at` timestamp NULL DEFAULT NULL COMMENT '使用时间',
  `is_active` tinyint(1) NOT NULL DEFAULT 1 COMMENT '是否有效',
  `remark` varchar(255) DEFAULT NULL COMMENT '备注',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_code` (`code`)
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '教师邀请码表' ROW_FORMAT = Dynamic;

-- 班级表
DROP TABLE IF EXISTS `class`;
CREATE TABLE `class` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '主键',
  `name` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '班级名称',
  `description` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '班级描述',
  `class_code` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '班级码',
  `teacher_id` bigint(20) NOT NULL COMMENT '教师ID',
  `created_at` datetime(0) NULL DEFAULT CURRENT_TIMESTAMP(0) COMMENT '创建时间',
  `updated_at` datetime(0) NULL DEFAULT CURRENT_TIMESTAMP(0) ON UPDATE CURRENT_TIMESTAMP(0) COMMENT '更新时间',
  `is_deleted` tinyint(1) NULL DEFAULT 0 COMMENT '逻辑删除标记',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `idx_class_code`(`class_code`) USING BTREE,
  INDEX `idx_teacher_id`(`teacher_id`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '班级表' ROW_FORMAT = Dynamic;

-- 班级成员表
DROP TABLE IF EXISTS `class_member`;
CREATE TABLE `class_member` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '主键',
  `class_id` bigint(20) NOT NULL COMMENT '班级ID',
  `user_id` int UNSIGNED NOT NULL COMMENT '用户ID',
  `joined_at` datetime(0) NULL DEFAULT CURRENT_TIMESTAMP(0) COMMENT '加入时间',
  `is_deleted` tinyint(1) NULL DEFAULT 0 COMMENT '逻辑删除标记',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `idx_class_id_user_id`(`class_id`, `user_id`) USING BTREE,
  INDEX `idx_user_id`(`user_id`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '班级成员表' ROW_FORMAT = Dynamic;

-- 插入一个管理员用户
INSERT INTO `user` (id, username, password, created_at, updated_at, is_deleted) 
VALUES (1, 'admin', 'admin123', NOW(), NOW(), 0);

-- 将该用户设置为管理员
INSERT INTO `admin` (user_id) VALUES (1);

-- 启用外键检查
SET FOREIGN_KEY_CHECKS = 1;
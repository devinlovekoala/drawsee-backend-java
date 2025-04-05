-- MySQL数据库初始化脚本
-- 基于当前Java工程所需的字段和逻辑

-- 设置编码和禁用外键检查
SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

-- 用户表
DROP TABLE IF EXISTS `user`;
CREATE TABLE `user` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '用户唯一ID',
  `username` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '用户名（唯一）',
  `password` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '密码哈希值',
  `role` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL DEFAULT 'USER' COMMENT '用户角色',
  `created_at` datetime(0) NULL DEFAULT CURRENT_TIMESTAMP(0) COMMENT '创建时间',
  `updated_at` datetime(0) NULL DEFAULT CURRENT_TIMESTAMP(0) ON UPDATE CURRENT_TIMESTAMP(0) COMMENT '更新时间',
  `is_deleted` tinyint(1) NULL DEFAULT 0 COMMENT '逻辑删除标记（0-未删除，1-已删除）',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `username`(`username`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '用户表' ROW_FORMAT = Dynamic;

-- 会话表
DROP TABLE IF EXISTS `conversation`;
CREATE TABLE `conversation` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '会话唯一ID',
  `title` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '会话标题',
  `user_id` bigint NOT NULL COMMENT '所属用户ID',
  `subject` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '学科',
  `created_at` datetime(0) NULL DEFAULT CURRENT_TIMESTAMP(0) COMMENT '创建时间',
  `updated_at` datetime(0) NULL DEFAULT CURRENT_TIMESTAMP(0) ON UPDATE CURRENT_TIMESTAMP(0) COMMENT '更新时间',
  `is_deleted` tinyint(1) NULL DEFAULT 0 COMMENT '逻辑删除标记',
  PRIMARY KEY (`id`) USING BTREE,
  INDEX `idx_conversation_user_id`(`user_id`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '会话表' ROW_FORMAT = Dynamic;

-- 节点表
DROP TABLE IF EXISTS `node`;
CREATE TABLE `node` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '节点唯一ID',
  `type` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '节点类型（如文本、图片等）',
  `data` mediumtext CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '节点内容（长文本支持）',
  `position` text CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '节点位置坐标（如JSON格式：{\"x\":100, \"y\":200}）',
  `height` int NULL DEFAULT NULL COMMENT '节点高度',
  `width` int NULL DEFAULT NULL COMMENT '节点宽度',
  `parent_id` bigint NULL DEFAULT NULL COMMENT '父节点ID（自引用外键）',
  `conv_id` bigint NOT NULL COMMENT '所属会话ID',
  `user_id` bigint NOT NULL COMMENT '所属用户ID',
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
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '任务唯一ID',
  `type` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '任务类型',
  `data` mediumtext CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '任务内容（长文本支持）',
  `result` mediumtext CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '任务结果（长文本支持）',
  `status` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '任务状态',
  `tokens` int NULL DEFAULT NULL COMMENT '消耗的Token数',
  `user_id` bigint NOT NULL COMMENT '任务所属用户ID',
  `conv_id` bigint NOT NULL COMMENT '任务所属会话ID',
  `prompt` text CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '提示词',
  `prompt_params` json NULL COMMENT '提示词参数',
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
  `id` bigint NOT NULL AUTO_INCREMENT,
  `user_id` bigint NOT NULL COMMENT '对应用户ID',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `user_id`(`user_id`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '管理员表' ROW_FORMAT = Dynamic;

-- 邀请码表
DROP TABLE IF EXISTS `invitation_code`;
CREATE TABLE `invitation_code` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `code` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '邀请码（唯一）',
  `created_at` datetime(0) NULL DEFAULT CURRENT_TIMESTAMP(0),
  `used_by` bigint NULL DEFAULT NULL COMMENT '使用者的用户ID',
  `used_at` datetime(0) NULL DEFAULT NULL,
  `is_active` tinyint(1) NULL DEFAULT 1 COMMENT '是否可用（可做软删除）',
  `sent_user_name` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '发送对象的名字',
  `sent_email` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '发送对象的邮箱',
  `last_sent_at` datetime(0) NULL DEFAULT NULL COMMENT '上次发送的时间',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `code`(`code`) USING BTREE,
  INDEX `idx_invitation_code_used_by`(`used_by`) USING BTREE,
  INDEX `idx_invitation_code_created_at`(`created_at`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '邀请码表' ROW_FORMAT = Dynamic;

-- 教师表
DROP TABLE IF EXISTS `teacher`;
CREATE TABLE `teacher` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `user_id` bigint NOT NULL COMMENT '用户ID',
  `title` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '职称',
  `organization` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '所属组织机构',
  `created_at` datetime(0) NULL DEFAULT CURRENT_TIMESTAMP(0) COMMENT '创建时间',
  `updated_at` datetime(0) NULL DEFAULT CURRENT_TIMESTAMP(0) ON UPDATE CURRENT_TIMESTAMP(0) COMMENT '更新时间',
  `is_deleted` tinyint(1) NULL DEFAULT 0 COMMENT '逻辑删除标记',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `idx_user_id`(`user_id`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '教师表' ROW_FORMAT = Dynamic;

-- 教师邀请码表
DROP TABLE IF EXISTS `teacher_invitation_code`;
CREATE TABLE `teacher_invitation_code` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `code` varchar(20) NOT NULL COMMENT '邀请码',
  `course_id` varchar(36) NOT NULL COMMENT '课程ID',
  `created_by` bigint NOT NULL COMMENT '创建人ID',
  `used_by` bigint NULL DEFAULT NULL COMMENT '使用人ID',
  `created_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `used_at` timestamp NULL DEFAULT NULL COMMENT '使用时间',
  `is_active` tinyint(1) NOT NULL DEFAULT 1 COMMENT '是否有效',
  `remark` varchar(255) DEFAULT NULL COMMENT '备注',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_code` (`code`)
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '教师邀请码表' ROW_FORMAT = Dynamic;

-- 班级表
DROP TABLE IF EXISTS `class`;
CREATE TABLE `class` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `name` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '班级名称',
  `description` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '班级描述',
  `class_code` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '班级码',
  `teacher_id` bigint NOT NULL COMMENT '教师ID',
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
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `class_id` bigint NOT NULL COMMENT '班级ID',
  `user_id` bigint NOT NULL COMMENT '用户ID',
  `joined_at` datetime(0) NULL DEFAULT CURRENT_TIMESTAMP(0) COMMENT '加入时间',
  `is_deleted` tinyint(1) NULL DEFAULT 0 COMMENT '逻辑删除标记',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `idx_class_id_user_id`(`class_id`, `user_id`) USING BTREE,
  INDEX `idx_user_id`(`user_id`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '班级成员表' ROW_FORMAT = Dynamic;

-- 插入初始用户数据
-- 插入管理员用户（密码为：funstack20250328）
INSERT INTO `user` (id, username, password, role, created_at, updated_at, is_deleted) 
VALUES (1, 'drawsee-admin', '$2a$10$1/JxJxkxGUvE8zQh2XsGz.4QJj0xh.QMLk1QYn.rKGmGhxKgPXkPi', 'ADMIN', NOW(), NOW(), 0);

-- 插入测试教师用户（密码为：teacher123）
INSERT INTO `user` (id, username, password, role, created_at, updated_at, is_deleted)
VALUES (2, 'teacher', '$2a$10$YDwJj1gk.V6nRwZG5bRLB.P.RFrG5z9RnC/FmZJ8x0PQXN5hNJ7Uy', 'TEACHER', NOW(), NOW(), 0);

-- 插入测试学生用户（密码为：student123）
INSERT INTO `user` (id, username, password, role, created_at, updated_at, is_deleted)
VALUES (3, 'student', '$2a$10$YDwJj1gk.V6nRwZG5bRLB.P.RFrG5z9RnC/FmZJ8x0PQXN5hNJ7Uy', 'USER', NOW(), NOW(), 0);

-- 将用户设置为管理员
INSERT INTO `admin` (user_id) VALUES (1);

-- 初始化教师信息
INSERT INTO `teacher` (user_id, title, organization, created_at, updated_at, is_deleted)
VALUES (2, '副教授', '北京邮电大学', NOW(), NOW(), 0);

-- 初始化测试班级
INSERT INTO `class` (name, description, class_code, teacher_id, created_at, updated_at, is_deleted)
VALUES ('测试班级', '用于系统测试的示例班级', '123456', 2, NOW(), NOW(), 0);

-- 初始化班级成员（将学生加入测试班级）
INSERT INTO `class_member` (class_id, user_id, joined_at, is_deleted)
VALUES (1, 3, NOW(), 0);

-- 启用外键检查
SET FOREIGN_KEY_CHECKS = 1;
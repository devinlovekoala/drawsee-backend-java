/*
 Navicat MySQL Data Transfer

 Source Server         : main
 Source Server Type    : MySQL
 Source Server Version : 80404
 Source Host           : 117.72.46.175:3268
 Source Schema         : drawsee

 Target Server Type    : MySQL
 Target Server Version : 80404
 File Encoding         : 65001

 Date: 26/03/2025 22:37:16
*/

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

-- ----------------------------
-- Table structure for admin
-- ----------------------------
DROP TABLE IF EXISTS `admin`;
CREATE TABLE `admin`  (
  `id` int NOT NULL AUTO_INCREMENT,
  `user_id` int UNSIGNED NOT NULL COMMENT '对应用户ID',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `user_id`(`user_id`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci ROW_FORMAT = Dynamic;

-- ----------------------------
-- Table structure for ai_task
-- ----------------------------
DROP TABLE IF EXISTS `ai_task`;
CREATE TABLE `ai_task`  (
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
) ENGINE = InnoDB AUTO_INCREMENT = 383 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '任务表' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Table structure for conversation
-- ----------------------------
DROP TABLE IF EXISTS `conversation`;
CREATE TABLE `conversation`  (
  `id` int UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '会话唯一ID',
  `title` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '会话标题',
  `user_id` int UNSIGNED NOT NULL COMMENT '所属用户ID',
  `created_at` datetime(0) NULL DEFAULT CURRENT_TIMESTAMP(0) COMMENT '创建时间',
  `updated_at` datetime(0) NULL DEFAULT CURRENT_TIMESTAMP(0) ON UPDATE CURRENT_TIMESTAMP(0) COMMENT '更新时间',
  `is_deleted` tinyint(1) NULL DEFAULT 0 COMMENT '逻辑删除标记',
  PRIMARY KEY (`id`) USING BTREE,
  INDEX `idx_conversation_user_id`(`user_id`) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 132 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '会话表' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Table structure for conversation_share
-- ----------------------------
DROP TABLE IF EXISTS `conversation_share`;
CREATE TABLE `conversation_share`  (
  `id` int UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '分享ID',
  `conv_id` int UNSIGNED NOT NULL COMMENT '会话ID',
  `user_id` int UNSIGNED NOT NULL COMMENT '分享创建者用户ID',
  `class_id` int UNSIGNED NULL DEFAULT NULL COMMENT '班级ID（可选）',
  `share_token` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '分享token',
  `allow_continue` tinyint(1) NULL DEFAULT 1 COMMENT '是否允许继续会话',
  `view_count` int UNSIGNED NULL DEFAULT 0 COMMENT '浏览次数',
  `created_at` datetime(0) NULL DEFAULT CURRENT_TIMESTAMP(0) COMMENT '创建时间',
  `updated_at` datetime(0) NULL DEFAULT CURRENT_TIMESTAMP(0) ON UPDATE CURRENT_TIMESTAMP(0) COMMENT '更新时间',
  `is_deleted` tinyint(1) NULL DEFAULT 0 COMMENT '逻辑删除标记',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `uk_conversation_share_token`(`share_token`) USING BTREE,
  INDEX `idx_conversation_share_conv_id`(`conv_id`) USING BTREE,
  INDEX `idx_conversation_share_user_id`(`user_id`) USING BTREE,
  INDEX `idx_conversation_share_class_id`(`class_id`) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 1 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '会话分享表' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Table structure for invitation_code
-- ----------------------------
DROP TABLE IF EXISTS `invitation_code`;
CREATE TABLE `invitation_code`  (
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
) ENGINE = InnoDB AUTO_INCREMENT = 2 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci ROW_FORMAT = Dynamic;

-- ----------------------------
-- Table structure for node
-- ----------------------------
DROP TABLE IF EXISTS `node`;
CREATE TABLE `node`  (
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
) ENGINE = InnoDB AUTO_INCREMENT = 1078 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '节点表' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Table structure for user
-- ----------------------------
DROP TABLE IF EXISTS `user`;
CREATE TABLE `user`  (
  `id` int UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '用户唯一ID',
  `username` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '用户名（唯一）',
  `password` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '密码哈希值',
  `created_at` datetime(0) NULL DEFAULT CURRENT_TIMESTAMP(0) COMMENT '创建时间',
  `updated_at` datetime(0) NULL DEFAULT CURRENT_TIMESTAMP(0) ON UPDATE CURRENT_TIMESTAMP(0) COMMENT '更新时间',
  `is_deleted` tinyint(1) NULL DEFAULT 0 COMMENT '逻辑删除标记（0-未删除，1-已删除）',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `username`(`username`) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 14 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '用户表' ROW_FORMAT = Dynamic;

-- ----------------------------n-- Table structure for knowledgen-- ----------------------------nDROP TABLE IF EXISTS `knowledge`;nCREATE TABLE `knowledge`  (n  `id` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '知识点唯一ID',n  `name` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '知识点名称',n  `subject` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '学科',n  `aliases` json NULL COMMENT '别名列表（JSON存储）',n  `level` int NULL DEFAULT NULL COMMENT '难度级别',n  `parent_id` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '父知识点ID',n  `children_ids` json NULL COMMENT '子知识点ID列表（JSON存储）',n  `knowledge_base_id` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '所属知识库ID',n  `creator_id` bigint UNSIGNED NOT NULL COMMENT '创建者ID',n  `created_at` datetime(0) NULL DEFAULT CURRENT_TIMESTAMP(0) COMMENT '创建时间',n  `updated_at` datetime(0) NULL DEFAULT CURRENT_TIMESTAMP(0) ON UPDATE CURRENT_TIMESTAMP(0) COMMENT '更新时间',n  `is_deleted` tinyint(1) NULL DEFAULT 0 COMMENT '是否已删除',n  PRIMARY KEY (`id`) USING BTREE,n  INDEX `idx_knowledge_subject`(`subject`) USING BTREE,n  INDEX `idx_knowledge_parent_id`(`parent_id`) USING BTREE,n  INDEX `idx_knowledge_knowledge_base_id`(`knowledge_base_id`) USING BTREE,n  INDEX `idx_knowledge_creator_id`(`creator_id`) USING BTREE,n  INDEX `idx_knowledge_name`(`name`) USING BTREEn) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '知识点表' ROW_FORMAT = Dynamic;nn-- ----------------------------n-- Table structure for knowledge_basen-- ----------------------------nDROP TABLE IF EXISTS `knowledge_base`;nCREATE TABLE `knowledge_base`  (n  `id` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '知识库唯一ID',n  `name` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '知识库名称',n  `description` text CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '知识库描述',n  `subject` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '学科',n  `invitation_code` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '邀请码',n  `creator_id` bigint UNSIGNED NOT NULL COMMENT '创建者ID',n  `class_ids` json NULL COMMENT '关联班级ID列表（JSON存储）',n  `created_at` datetime(0) NULL DEFAULT CURRENT_TIMESTAMP(0) COMMENT '创建时间',n  `updated_at` datetime(0) NULL DEFAULT CURRENT_TIMESTAMP(0) ON UPDATE CURRENT_TIMESTAMP(0) COMMENT '更新时间',n  `members` json NULL COMMENT '成员列表（JSON存储）',n  `is_deleted` tinyint(1) NULL DEFAULT 0 COMMENT '是否已删除',n  `is_published` tinyint(1) NULL DEFAULT 0 COMMENT '是否已发布',n  `rag_enabled` tinyint(1) NULL DEFAULT 0 COMMENT '是否启用RAG',n  `rag_knowledge_id` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT 'RAG知识库ID',n  `rag_document_count` int NULL DEFAULT 0 COMMENT 'RAG文档数量',n  `rag_dataset_id` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT 'RAG数据集ID',n  `sync_to_rag_flow` tinyint(1) NULL DEFAULT 0 COMMENT '是否同步到RAG Flow',n  `rag_sync_status` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT 'pending' COMMENT 'RAG同步状态',n  `last_sync_time` datetime(0) NULL DEFAULT NULL COMMENT '最后同步时间',n  PRIMARY KEY (`id`) USING BTREE,n  INDEX `idx_knowledge_base_subject`(`subject`) USING BTREE,n  INDEX `idx_knowledge_base_creator_id`(`creator_id`) USING BTREE,n  INDEX `idx_knowledge_base_is_published`(`is_published`) USING BTREE,n  INDEX `idx_knowledge_base_name`(`name`) USING BTREEn) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '知识库表' ROW_FORMAT = Dynamic;nn-- ----------------------------n-- Table structure for knowledge_resourcen-- ----------------------------nDROP TABLE IF EXISTS `knowledge_resource`;nCREATE TABLE `knowledge_resource`  (n  `id` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '资源唯一ID',n  `name` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '资源名称',n  `type` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '资源类型',n  `url` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '资源URL',n  `file_path` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '文件路径',n  `file_size` bigint NULL DEFAULT NULL COMMENT '文件大小（字节）',n  `knowledge_base_id` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '所属知识库ID',n  `creator_id` bigint UNSIGNED NOT NULL COMMENT '创建者ID',n  `created_at` datetime(0) NULL DEFAULT CURRENT_TIMESTAMP(0) COMMENT '创建时间',n  `updated_at` datetime(0) NULL DEFAULT CURRENT_TIMESTAMP(0) ON UPDATE CURRENT_TIMESTAMP(0) COMMENT '更新时间',n  `is_deleted` tinyint(1) NULL DEFAULT 0 COMMENT '是否已删除',n  `metadata` json NULL COMMENT '元数据（JSON存储）',n  PRIMARY KEY (`id`) USING BTREE,n  INDEX `idx_knowledge_resource_knowledge_base_id`(`knowledge_base_id`) USING BTREE,n  INDEX `idx_knowledge_resource_creator_id`(`creator_id`) USING BTREE,n  INDEX `idx_knowledge_resource_type`(`type`) USING BTREE,n  INDEX `idx_knowledge_resource_name`(`name`) USING BTREEn) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '知识资源表' ROW_FORMAT = Dynamic;nn-- ----------------------------n-- Table structure for coursen-- ----------------------------nDROP TABLE IF EXISTS `course`;nCREATE TABLE `course`  (n  `id` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '课程唯一ID',n  `name` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '课程名称',n  `code` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '课程代码',n  `class_code` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '班级代码',n  `description` text CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '课程描述',n  `subject` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '学科',n  `topics` json NULL COMMENT '主题列表（JSON存储）',n  `creator_id` bigint UNSIGNED NOT NULL COMMENT '创建者ID',n  `creator_role` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '创建者角色',n  `student_ids` json NULL COMMENT '学生ID列表（JSON存储）',n  `knowledge_base_ids` json NULL COMMENT '知识库ID列表（JSON存储）',n  `created_at` datetime(0) NULL DEFAULT CURRENT_TIMESTAMP(0) COMMENT '创建时间',n  `updated_at` datetime(0) NULL DEFAULT CURRENT_TIMESTAMP(0) ON UPDATE CURRENT_TIMESTAMP(0) COMMENT '更新时间',n  `is_deleted` tinyint(1) NULL DEFAULT 0 COMMENT '是否已删除',n  PRIMARY KEY (`id`) USING BTREE,n  UNIQUE INDEX `code`(`code`) USING BTREE,n  UNIQUE INDEX `class_code`(`class_code`) USING BTREE,n  INDEX `idx_course_subject`(`subject`) USING BTREE,n  INDEX `idx_course_creator_id`(`creator_id`) USING BTREE,n  INDEX `idx_course_creator_role`(`creator_role`) USING BTREE,n  INDEX `idx_course_name`(`name`) USING BTREEn) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '课程表' ROW_FORMAT = Dynamic;nn-- ----------------------------n-- Table structure for classn-- ----------------------------nDROP TABLE IF EXISTS `class`;nCREATE TABLE `class`  (n  `id` bigint UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '班级唯一ID',n  `name` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '班级名称',n  `class_code` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '班级代码（唯一）',n  `description` text CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '班级描述',n  `creator_id` bigint UNSIGNED NOT NULL COMMENT '创建者ID',n  `created_at` datetime(0) NULL DEFAULT CURRENT_TIMESTAMP(0) COMMENT '创建时间',n  `updated_at` datetime(0) NULL DEFAULT CURRENT_TIMESTAMP(0) ON UPDATE CURRENT_TIMESTAMP(0) COMMENT '更新时间',n  `is_deleted` tinyint(1) NULL DEFAULT 0 COMMENT '是否已删除',n  PRIMARY KEY (`id`) USING BTREE,n  UNIQUE INDEX `class_code`(`class_code`) USING BTREE,n  INDEX `idx_class_creator_id`(`creator_id`) USING BTREE,n  INDEX `idx_class_name`(`name`) USING BTREEn) ENGINE = InnoDB AUTO_INCREMENT = 1 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '班级表' ROW_FORMAT = Dynamic;nn-- ----------------------------n-- Table structure for class_membern-- ----------------------------nDROP TABLE IF EXISTS `class_member`;nCREATE TABLE `class_member`  (n  `id` bigint UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '成员关系唯一ID',n  `class_id` bigint UNSIGNED NOT NULL COMMENT '班级ID',n  `user_id` bigint UNSIGNED NOT NULL COMMENT '用户ID',n  `role` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL DEFAULT 'student' COMMENT '角色（student/teacher）',n  `joined_at` datetime(0) NULL DEFAULT CURRENT_TIMESTAMP(0) COMMENT '加入时间',n  `is_deleted` tinyint(1) NULL DEFAULT 0 COMMENT '是否已删除',n  PRIMARY KEY (`id`) USING BTREE,n  UNIQUE INDEX `class_user_unique`(`class_id`, `user_id`) USING BTREE,n  INDEX `idx_class_member_class_id`(`class_id`) USING BTREE,n  INDEX `idx_class_member_user_id`(`user_id`) USING BTREE,n  INDEX `idx_class_member_role`(`role`) USING BTREEn) ENGINE = InnoDB AUTO_INCREMENT = 1 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '班级成员表' ROW_FORMAT = Dynamic;nn-- ----------------------------n-- Table structure for teachern-- ----------------------------nDROP TABLE IF EXISTS `teacher`;nCREATE TABLE `teacher`  (n  `id` bigint UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '教师唯一ID',n  `user_id` bigint UNSIGNED NOT NULL COMMENT '对应用户ID',n  `name` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '教师姓名',n  `email` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '邮箱',n  `phone` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '电话',n  `subject` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '教授学科',n  `created_at` datetime(0) NULL DEFAULT CURRENT_TIMESTAMP(0) COMMENT '创建时间',n  `updated_at` datetime(0) NULL DEFAULT CURRENT_TIMESTAMP(0) ON UPDATE CURRENT_TIMESTAMP(0) COMMENT '更新时间',n  `is_deleted` tinyint(1) NULL DEFAULT 0 COMMENT '是否已删除',n  PRIMARY KEY (`id`) USING BTREE,n  UNIQUE INDEX `user_id`(`user_id`) USING BTREE,n  INDEX `idx_teacher_subject`(`subject`) USING BTREE,n  INDEX `idx_teacher_name`(`name`) USING BTREEn) ENGINE = InnoDB AUTO_INCREMENT = 1 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '教师表' ROW_FORMAT = Dynamic;nn-- ----------------------------n-- Table structure for teacher_invitation_coden-- ----------------------------nDROP TABLE IF EXISTS `teacher_invitation_code`;nCREATE TABLE `teacher_invitation_code`  (n  `id` bigint UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '邀请码唯一ID',n  `code` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '邀请码（唯一）',n  `teacher_id` bigint UNSIGNED NOT NULL COMMENT '邀请教师ID',n  `created_at` datetime(0) NULL DEFAULT CURRENT_TIMESTAMP(0) COMMENT '创建时间',n  `used_by` bigint NULL DEFAULT NULL COMMENT '使用者的用户ID',n  `used_at` datetime(0) NULL DEFAULT NULL COMMENT '使用时间',n  `is_active` tinyint(1) NULL DEFAULT 1 COMMENT '是否可用',n  `sent_user_name` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '发送对象的名字',n  `sent_email` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '发送对象的邮箱',n  `last_sent_at` datetime(0) NULL DEFAULT NULL COMMENT '上次发送的时间',n  PRIMARY KEY (`id`) USING BTREE,n  UNIQUE INDEX `code`(`code`) USING BTREE,n  INDEX `idx_teacher_invitation_code_teacher_id`(`teacher_id`) USING BTREE,n  INDEX `idx_teacher_invitation_code_used_by`(`used_by`) USING BTREEn) ENGINE = InnoDB AUTO_INCREMENT = 1 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '教师邀请码表' ROW_FORMAT = Dynamic;nn-- ----------------------------n-- Table structure for user_documentn-- ----------------------------nDROP TABLE IF EXISTS `user_document`;nCREATE TABLE `user_document`  (n  `id` bigint UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '文档唯一ID',n  `user_id` bigint UNSIGNED NOT NULL COMMENT '用户ID',n  `name` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '文档名称',n  `type` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '文档类型',n  `url` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '文档URL',n  `file_path` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '文件路径',n  `file_size` bigint NULL DEFAULT NULL COMMENT '文件大小（字节）',n  `created_at` datetime(0) NULL DEFAULT CURRENT_TIMESTAMP(0) COMMENT '创建时间',n  `updated_at` datetime(0) NULL DEFAULT CURRENT_TIMESTAMP(0) ON UPDATE CURRENT_TIMESTAMP(0) COMMENT '更新时间',n  `is_deleted` tinyint(1) NULL DEFAULT 0 COMMENT '是否已删除',n  PRIMARY KEY (`id`) USING BTREE,n  INDEX `idx_user_document_user_id`(`user_id`) USING BTREE,n  INDEX `idx_user_document_type`(`type`) USING BTREE,n  INDEX `idx_user_document_name`(`name`) USING BTREEn) ENGINE = InnoDB AUTO_INCREMENT = 1 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '用户文档表' ROW_FORMAT = Dynamic;nn-- ----------------------------n-- Table structure for user_rolen-- ----------------------------nDROP TABLE IF EXISTS `user_role`;nCREATE TABLE `user_role`  (n  `id` bigint UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '角色关系唯一ID',n  `user_id` bigint UNSIGNED NOT NULL COMMENT '用户ID',n  `role` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '角色',n  `created_at` datetime(0) NULL DEFAULT CURRENT_TIMESTAMP(0) COMMENT '创建时间',n  `is_deleted` tinyint(1) NULL DEFAULT 0 COMMENT '是否已删除',n  PRIMARY KEY (`id`) USING BTREE,n  UNIQUE INDEX `user_role_unique`(`user_id`, `role`) USING BTREE,n  INDEX `idx_user_role_user_id`(`user_id`) USING BTREE,n  INDEX `idx_user_role_role`(`role`) USING BTREEn) ENGINE = InnoDB AUTO_INCREMENT = 1 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '用户角色表' ROW_FORMAT = Dynamic;nn

-- ----------------------------
-- Table structure for course_resource
-- ----------------------------
DROP TABLE IF EXISTS `course_resource`;
CREATE TABLE `course_resource`  (
  `id` bigint UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '资源ID',
  `course_id` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '课程ID',
  `type` varchar(30) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '资源类型',
  `title` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '标题',
  `description` text CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '描述',
  `content` text CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '任务内容/说明',
  `file_url` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '文件链接',
  `file_name` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '文件名',
  `file_size` bigint NULL DEFAULT NULL COMMENT '文件大小（字节）',
  `cover_url` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '封面/预览图',
  `due_at` datetime(0) NULL DEFAULT NULL COMMENT '截止时间',
  `created_by` bigint UNSIGNED NOT NULL COMMENT '创建者',
  `created_at` datetime(0) NULL DEFAULT CURRENT_TIMESTAMP(0) COMMENT '创建时间',
  `updated_at` datetime(0) NULL DEFAULT CURRENT_TIMESTAMP(0) ON UPDATE CURRENT_TIMESTAMP(0) COMMENT '更新时间',
  `is_deleted` tinyint(1) NULL DEFAULT 0 COMMENT '是否已删除',
  PRIMARY KEY (`id`) USING BTREE,
  INDEX `idx_course_resource_course_id`(`course_id`) USING BTREE,
  INDEX `idx_course_resource_type`(`type`) USING BTREE,
  INDEX `idx_course_resource_created_by`(`created_by`) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 1 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '课程资源表' ROW_FORMAT = Dynamic;

SET FOREIGN_KEY_CHECKS = 1;

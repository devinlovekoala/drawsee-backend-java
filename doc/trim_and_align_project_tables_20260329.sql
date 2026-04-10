-- Drawsee schema trim/alignment script
-- Date: 2026-03-29
-- Purpose:
-- 1) Keep both drawsee-test and drawsee aligned with current project-used tables.
-- 2) Create missing in-use tables: conversation_share, course_resource.
-- 3) Drop deprecated/experimental tables no longer used by current Java project.
--
-- Usage:
--   1. Set @target_schema to the schema you want to fix.
--   2. Execute the whole script.
--   3. Repeat for the other schema.
--
-- Example:
--   SET @target_schema = 'drawsee-test';
--   (run all)
--   SET @target_schema = 'drawsee';
--   (run all)

-- =============================
-- A) Create missing in-use tables
-- =============================

CREATE TABLE IF NOT EXISTS `conversation_share` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '分享ID',
  `conv_id` bigint NOT NULL COMMENT '会话ID',
  `user_id` bigint NOT NULL COMMENT '分享者用户ID',
  `class_id` bigint DEFAULT NULL COMMENT '班级ID（可选）',
  `share_token` varchar(64) NOT NULL COMMENT '分享token',
  `allow_continue` tinyint(1) DEFAULT 1 COMMENT '是否允许继续会话',
  `view_count` bigint DEFAULT 0 COMMENT '浏览次数',
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `is_deleted` tinyint(1) DEFAULT 0 COMMENT '逻辑删除',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_conversation_share_token` (`share_token`),
  KEY `idx_conversation_share_conv_id` (`conv_id`),
  KEY `idx_conversation_share_user_id` (`user_id`),
  KEY `idx_conversation_share_class_id` (`class_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='会话分享表';

CREATE TABLE IF NOT EXISTS `course_resource` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '资源ID',
  `course_id` varchar(255) NOT NULL COMMENT '课程ID',
  `type` varchar(30) NOT NULL COMMENT '资源类型',
  `title` varchar(255) NOT NULL COMMENT '标题',
  `description` text COMMENT '描述',
  `content` text COMMENT '任务内容/说明',
  `file_url` varchar(500) DEFAULT NULL COMMENT '文件链接',
  `file_name` varchar(255) DEFAULT NULL COMMENT '文件名',
  `file_size` bigint DEFAULT NULL COMMENT '文件大小（字节）',
  `cover_url` varchar(500) DEFAULT NULL COMMENT '封面/预览图',
  `due_at` datetime DEFAULT NULL COMMENT '截止时间',
  `created_by` bigint NOT NULL COMMENT '创建者',
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `is_deleted` tinyint(1) DEFAULT 0 COMMENT '逻辑删除',
  PRIMARY KEY (`id`),
  KEY `idx_course_resource_course_id` (`course_id`),
  KEY `idx_course_resource_type` (`type`),
  KEY `idx_course_resource_created_by` (`created_by`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='课程资源表';

-- =============================
-- B) Drop deprecated tables
-- =============================
-- These tables are not referenced by current mappers/services and are from old/experimental phases.

SET @old_fk_checks = @@FOREIGN_KEY_CHECKS;
SET FOREIGN_KEY_CHECKS = 0;

DROP TABLE IF EXISTS `circuit_structure`;
DROP TABLE IF EXISTS `circuit_metadata`;
DROP TABLE IF EXISTS `document_table_content`;
DROP TABLE IF EXISTS `document_text_chunk`;
DROP TABLE IF EXISTS `etl_task`;
DROP TABLE IF EXISTS `knowledge_base_member`;

SET FOREIGN_KEY_CHECKS = @old_fk_checks;

-- =============================
-- C) Quick verification
-- =============================
SELECT TABLE_NAME
FROM INFORMATION_SCHEMA.TABLES
WHERE TABLE_SCHEMA = DATABASE()
ORDER BY TABLE_NAME;

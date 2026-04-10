-- Align schema with current application models (safe, no drops)
-- This script only creates missing tables and adds missing columns/indexes.

SET NAMES utf8mb4;

-- Core tables
CREATE TABLE IF NOT EXISTS `user` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '用户唯一ID',
  `username` varchar(50) NOT NULL COMMENT '用户名（唯一）',
  `password` varchar(255) NOT NULL COMMENT 'BCrypt哈希密码',
  `created_at` datetime NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` datetime NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `is_deleted` tinyint(1) NULL DEFAULT 0 COMMENT '逻辑删除标记',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE KEY `uk_user_username` (`username`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '用户表';

CREATE TABLE IF NOT EXISTS `admin` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `user_id` bigint NOT NULL COMMENT '对应用户ID',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE KEY `uk_admin_user` (`user_id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '管理员表';

CREATE TABLE IF NOT EXISTS `teacher` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `user_id` bigint NOT NULL COMMENT '用户ID',
  `title` varchar(100) DEFAULT NULL COMMENT '职称',
  `organization` varchar(255) DEFAULT NULL COMMENT '所属组织机构',
  `created_at` datetime NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` datetime NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `is_deleted` tinyint(1) NULL DEFAULT 0 COMMENT '逻辑删除标记',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE KEY `uk_teacher_user` (`user_id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '教师表';

CREATE TABLE IF NOT EXISTS `conversation` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '会话唯一ID',
  `title` varchar(255) NOT NULL COMMENT '会话标题',
  `user_id` bigint NOT NULL COMMENT '所属用户ID',
  `subject` varchar(50) DEFAULT NULL COMMENT '学科',
  `created_at` datetime NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` datetime NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `is_deleted` tinyint(1) NULL DEFAULT 0 COMMENT '逻辑删除标记',
  PRIMARY KEY (`id`) USING BTREE,
  KEY `idx_conversation_user_id` (`user_id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '会话表';

CREATE TABLE IF NOT EXISTS `node` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '节点唯一ID',
  `type` varchar(50) NOT NULL COMMENT '节点类型',
  `data` mediumtext NOT NULL COMMENT '节点内容',
  `position` text NOT NULL COMMENT '节点位置JSON',
  `height` int DEFAULT NULL COMMENT '节点高度',
  `width` int DEFAULT NULL COMMENT '节点宽度',
  `parent_id` bigint DEFAULT NULL COMMENT '父节点ID',
  `conv_id` bigint NOT NULL COMMENT '所属会话ID',
  `user_id` bigint NOT NULL COMMENT '所属用户ID',
  `created_at` datetime NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` datetime NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `is_deleted` tinyint(1) NULL DEFAULT 0 COMMENT '逻辑删除标记',
  PRIMARY KEY (`id`) USING BTREE,
  KEY `idx_node_parent_id` (`parent_id`),
  KEY `idx_node_conv_id` (`conv_id`),
  KEY `idx_node_user_id` (`user_id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '节点表';

CREATE TABLE IF NOT EXISTS `ai_task` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '任务唯一ID',
  `type` varchar(50) NOT NULL COMMENT '任务类型',
  `data` mediumtext NOT NULL COMMENT '任务内容',
  `result` mediumtext DEFAULT NULL COMMENT '任务结果',
  `status` varchar(50) NOT NULL COMMENT '任务状态',
  `tokens` int DEFAULT NULL COMMENT 'Token数',
  `user_id` bigint NOT NULL COMMENT '所属用户ID',
  `conv_id` bigint NOT NULL COMMENT '所属会话ID',
  `prompt` text DEFAULT NULL COMMENT '提示词原文',
  `prompt_params` json DEFAULT NULL COMMENT '提示词参数',
  `created_at` datetime NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` datetime NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `is_deleted` tinyint(1) NULL DEFAULT 0 COMMENT '逻辑删除标记',
  PRIMARY KEY (`id`) USING BTREE,
  KEY `idx_ai_task_user_id` (`user_id`),
  KEY `idx_ai_task_conv_id` (`conv_id`),
  KEY `idx_ai_task_user_conv_status` (`user_id`,`conv_id`,`status`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = 'AI任务表';

CREATE TABLE IF NOT EXISTS `invitation_code` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `code` varchar(255) NOT NULL COMMENT '邀请码（唯一）',
  `created_at` datetime NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `used_by` bigint DEFAULT NULL COMMENT '使用者ID',
  `used_at` datetime DEFAULT NULL COMMENT '使用时间',
  `is_active` tinyint(1) NULL DEFAULT 1 COMMENT '是否可用',
  `sent_user_name` varchar(255) DEFAULT NULL COMMENT '发送对象名称',
  `sent_email` varchar(255) DEFAULT NULL COMMENT '发送对象邮箱',
  `last_sent_at` datetime DEFAULT NULL COMMENT '上次发送时间',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE KEY `uk_invitation_code` (`code`),
  KEY `idx_invitation_used_by` (`used_by`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '邀请码表';

CREATE TABLE IF NOT EXISTS `teacher_invitation_code` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `code` varchar(255) NOT NULL COMMENT '邀请码',
  `course_id` varchar(100) DEFAULT NULL COMMENT '关联课程ID',
  `created_by` bigint DEFAULT NULL COMMENT '创建人ID',
  `used_by` bigint DEFAULT NULL COMMENT '使用人ID',
  `created_at` datetime NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `used_at` datetime DEFAULT NULL COMMENT '使用时间',
  `is_active` tinyint(1) NULL DEFAULT 1 COMMENT '是否有效',
  `remark` varchar(255) DEFAULT NULL COMMENT '备注',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE KEY `uk_teacher_invite_code` (`code`),
  KEY `idx_teacher_invite_used_by` (`used_by`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '教师邀请码表';

CREATE TABLE IF NOT EXISTS `class` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `name` varchar(100) NOT NULL COMMENT '班级名称',
  `description` varchar(255) DEFAULT NULL COMMENT '班级描述',
  `class_code` varchar(20) NOT NULL COMMENT '班级码',
  `teacher_id` bigint NOT NULL COMMENT '教师用户ID',
  `created_at` datetime NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` datetime NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `is_deleted` tinyint(1) NULL DEFAULT 0 COMMENT '逻辑删除标记',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE KEY `uk_class_code` (`class_code`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '班级表';

CREATE TABLE IF NOT EXISTS `class_member` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `class_id` bigint NOT NULL COMMENT '班级ID',
  `user_id` bigint NOT NULL COMMENT '用户ID',
  `joined_at` datetime NULL DEFAULT CURRENT_TIMESTAMP COMMENT '加入时间',
  `is_deleted` tinyint(1) NULL DEFAULT 0 COMMENT '逻辑删除标记',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE KEY `uk_class_member` (`class_id`,`user_id`),
  KEY `idx_class_member_user` (`user_id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '班级成员表';

-- Knowledge & RAG tables
CREATE TABLE IF NOT EXISTS `knowledge_base` (
  `id` varchar(255) NOT NULL COMMENT '知识库ID',
  `name` varchar(255) NOT NULL COMMENT '知识库名称',
  `description` text DEFAULT NULL COMMENT '描述',
  `subject` varchar(100) NOT NULL COMMENT '学科',
  `invitation_code` varchar(255) DEFAULT NULL COMMENT '邀请码',
  `creator_id` bigint NOT NULL COMMENT '创建者ID',
  `class_ids` json DEFAULT NULL COMMENT '关联班级ID列表',
  `created_at` datetime NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` datetime NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `members` json DEFAULT NULL COMMENT '成员列表',
  `is_deleted` tinyint(1) NULL DEFAULT 0 COMMENT '是否删除',
  `is_published` tinyint(1) NULL DEFAULT 0 COMMENT '是否发布',
  `rag_enabled` tinyint(1) NULL DEFAULT 0 COMMENT '是否启用RAG',
  `rag_knowledge_id` varchar(255) DEFAULT NULL COMMENT 'RAG知识库ID',
  `rag_document_count` int DEFAULT 0 COMMENT 'RAG文档数量',
  `rag_dataset_id` varchar(255) DEFAULT NULL COMMENT 'RAG数据集ID',
  `sync_to_rag_flow` tinyint(1) NULL DEFAULT 0 COMMENT '是否同步到RAG Flow',
  `rag_sync_status` varchar(50) DEFAULT 'pending' COMMENT 'RAG同步状态',
  `last_sync_time` datetime DEFAULT NULL COMMENT '最后同步时间',
  PRIMARY KEY (`id`) USING BTREE,
  KEY `idx_knowledge_base_subject` (`subject`),
  KEY `idx_knowledge_base_creator` (`creator_id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '知识库表';

CREATE TABLE IF NOT EXISTS `knowledge` (
  `id` varchar(255) NOT NULL COMMENT '知识点ID',
  `name` varchar(255) NOT NULL COMMENT '名称',
  `subject` varchar(100) NOT NULL COMMENT '学科',
  `aliases` json DEFAULT NULL COMMENT '别名',
  `level` int DEFAULT NULL COMMENT '难度级别',
  `parent_id` varchar(255) DEFAULT NULL COMMENT '父级ID',
  `children_ids` json DEFAULT NULL COMMENT '子节点ID',
  `knowledge_base_id` varchar(255) NOT NULL COMMENT '所属知识库',
  `creator_id` bigint NOT NULL COMMENT '创建者ID',
  `created_at` datetime NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` datetime NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `is_deleted` tinyint(1) NULL DEFAULT 0 COMMENT '是否删除',
  PRIMARY KEY (`id`) USING BTREE,
  KEY `idx_knowledge_subject` (`subject`),
  KEY `idx_knowledge_kb` (`knowledge_base_id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '知识点表';

CREATE TABLE IF NOT EXISTS `knowledge_resource` (
  `id` varchar(255) NOT NULL COMMENT '资源ID',
  `knowledge_id` varchar(255) DEFAULT NULL COMMENT '知识点ID',
  `knowledge_base_id` varchar(255) NOT NULL COMMENT '知识库ID',
  `resource_type` varchar(50) NOT NULL COMMENT '资源类型',
  `title` varchar(255) DEFAULT NULL COMMENT '标题',
  `description` text DEFAULT NULL COMMENT '描述',
  `url` varchar(500) DEFAULT NULL COMMENT 'URL',
  `local_path` varchar(500) DEFAULT NULL COMMENT '本地路径',
  `cover_url` varchar(500) DEFAULT NULL COMMENT '封面',
  `size` bigint DEFAULT NULL COMMENT '大小',
  `duration` int DEFAULT NULL COMMENT '时长',
  `creator_id` bigint NOT NULL COMMENT '创建者ID',
  `created_at` datetime NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` datetime NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `is_deleted` tinyint(1) NULL DEFAULT 0 COMMENT '是否删除',
  `metadata` json DEFAULT NULL COMMENT '元数据',
  PRIMARY KEY (`id`) USING BTREE,
  KEY `idx_knowledge_resource_kb` (`knowledge_base_id`),
  KEY `idx_knowledge_resource_knowledge` (`knowledge_id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '知识点资源表';

CREATE TABLE IF NOT EXISTS `course` (
  `id` varchar(255) NOT NULL COMMENT '课程ID',
  `name` varchar(255) NOT NULL COMMENT '课程名称',
  `code` varchar(100) NOT NULL COMMENT '课程代码',
  `class_code` varchar(100) NOT NULL COMMENT '班级代码',
  `description` text DEFAULT NULL COMMENT '课程描述',
  `subject` varchar(100) NOT NULL COMMENT '学科',
  `topics` json DEFAULT NULL COMMENT '主题',
  `creator_id` bigint NOT NULL COMMENT '创建者ID',
  `creator_role` varchar(50) NOT NULL COMMENT '创建者角色',
  `student_ids` json DEFAULT NULL COMMENT '学生ID列表',
  `knowledge_base_ids` json DEFAULT NULL COMMENT '知识库ID列表',
  `created_at` datetime NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` datetime NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `is_deleted` tinyint(1) NULL DEFAULT 0 COMMENT '是否删除',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE KEY `uk_course_code` (`code`),
  UNIQUE KEY `uk_course_class_code` (`class_code`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '课程表';

CREATE TABLE IF NOT EXISTS `knowledge_document` (
  `id` varchar(64) NOT NULL COMMENT '文档ID',
  `knowledge_base_id` varchar(255) NOT NULL COMMENT '知识库ID',
  `title` varchar(255) NOT NULL COMMENT '标题',
  `original_file_name` varchar(255) DEFAULT NULL COMMENT '原始文件名',
  `file_type` varchar(50) DEFAULT NULL COMMENT '文件类型',
  `file_size` bigint DEFAULT NULL COMMENT '文件大小',
  `page_count` int DEFAULT NULL COMMENT '页数/片段数',
  `status` varchar(50) NOT NULL COMMENT '处理状态',
  `chunk_count` int DEFAULT NULL COMMENT '分块数量',
  `storage_url` varchar(500) DEFAULT NULL COMMENT '存储地址',
  `storage_object` varchar(255) DEFAULT NULL COMMENT '存储对象名',
  `uploader_id` bigint DEFAULT NULL COMMENT '上传用户ID',
  `uploaded_at` datetime DEFAULT NULL COMMENT '上传时间',
  `processed_at` datetime DEFAULT NULL COMMENT '处理完成时间',
  `failure_reason` text DEFAULT NULL COMMENT '失败原因',
  `created_at` datetime NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` datetime NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `is_deleted` tinyint(1) NULL DEFAULT 0 COMMENT '是否删除',
  PRIMARY KEY (`id`) USING BTREE,
  KEY `idx_knowledge_document_kb` (`knowledge_base_id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '知识库文档表';

CREATE TABLE IF NOT EXISTS `knowledge_document_chunk` (
  `id` varchar(64) NOT NULL COMMENT '分块ID',
  `document_id` varchar(64) NOT NULL COMMENT '文档ID',
  `knowledge_base_id` varchar(255) NOT NULL COMMENT '知识库ID',
  `chunk_index` int NOT NULL COMMENT '分块序号',
  `content` longtext NOT NULL COMMENT '分块内容',
  `token_count` int DEFAULT NULL COMMENT 'Token数量',
  `vector_id` varchar(128) DEFAULT NULL COMMENT '向量ID',
  `vector_dimension` int DEFAULT NULL COMMENT '向量维度',
  `created_at` datetime NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` datetime NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`) USING BTREE,
  KEY `idx_kd_chunk_document` (`document_id`),
  KEY `idx_kd_chunk_kb` (`knowledge_base_id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '知识库文档分块表';

CREATE TABLE IF NOT EXISTS `rag_ingestion_task` (
  `id` varchar(64) NOT NULL COMMENT '任务ID',
  `knowledge_base_id` varchar(255) NOT NULL COMMENT '知识库ID',
  `document_id` varchar(64) NOT NULL COMMENT '文档ID',
  `stage` varchar(50) NOT NULL COMMENT '处理阶段',
  `status` varchar(50) NOT NULL COMMENT '任务状态',
  `progress` int DEFAULT NULL COMMENT '进度',
  `error_message` text DEFAULT NULL COMMENT '错误信息',
  `duration_ms` bigint DEFAULT NULL COMMENT '耗时(毫秒)',
  `created_at` datetime NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` datetime NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `completed_at` datetime DEFAULT NULL COMMENT '完成时间',
  PRIMARY KEY (`id`) USING BTREE,
  KEY `idx_rag_task_document` (`document_id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = 'RAG入库任务表';

CREATE TABLE IF NOT EXISTS `user_document` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `uuid` varchar(64) NOT NULL COMMENT '文档UUID',
  `user_id` bigint NOT NULL COMMENT '用户ID',
  `document_type` varchar(50) NOT NULL COMMENT '文档类型',
  `title` varchar(255) NOT NULL COMMENT '标题',
  `description` text DEFAULT NULL COMMENT '描述',
  `file_url` varchar(500) DEFAULT NULL COMMENT '文件URL',
  `object_path` varchar(500) DEFAULT NULL COMMENT '对象存储路径',
  `file_size` bigint DEFAULT NULL COMMENT '文件大小',
  `tags` varchar(255) DEFAULT NULL COMMENT '标签',
  `created_at` datetime NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` datetime NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `is_deleted` tinyint(1) NULL DEFAULT 0 COMMENT '逻辑删除标记',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE KEY `uk_user_document_uuid` (`uuid`),
  KEY `idx_user_document_user` (`user_id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '用户文档表';

CREATE TABLE IF NOT EXISTS `circuit_design` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '电路设计ID',
  `user_id` bigint NOT NULL COMMENT '用户ID',
  `title` varchar(255) NOT NULL COMMENT '标题',
  `description` text DEFAULT NULL COMMENT '描述',
  `data` longtext NOT NULL COMMENT '电路JSON数据',
  `created_at` datetime NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` datetime NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `is_deleted` tinyint(1) NULL DEFAULT 0 COMMENT '逻辑删除标记',
  PRIMARY KEY (`id`) USING BTREE,
  KEY `idx_circuit_design_user` (`user_id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '电路设计表';

-- Add missing columns safely
SET @col_exists := (
    SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'conversation' AND COLUMN_NAME = 'subject'
);
SET @sql := IF(@col_exists = 0,
    'ALTER TABLE `conversation` ADD COLUMN `subject` varchar(50) DEFAULT NULL COMMENT ''学科''',
    'SELECT 1'
);
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @col_exists := (
    SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'node' AND COLUMN_NAME = 'width'
);
SET @sql := IF(@col_exists = 0,
    'ALTER TABLE `node` ADD COLUMN `width` int DEFAULT NULL COMMENT ''节点宽度''',
    'SELECT 1'
);
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @col_exists := (
    SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'ai_task' AND COLUMN_NAME = 'prompt'
);
SET @sql := IF(@col_exists = 0,
    'ALTER TABLE `ai_task` ADD COLUMN `prompt` text DEFAULT NULL COMMENT ''提示词原文''',
    'SELECT 1'
);
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @col_exists := (
    SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'ai_task' AND COLUMN_NAME = 'prompt_params'
);
SET @sql := IF(@col_exists = 0,
    'ALTER TABLE `ai_task` ADD COLUMN `prompt_params` json DEFAULT NULL COMMENT ''提示词参数''',
    'SELECT 1'
);
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @col_exists := (
    SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'teacher_invitation_code' AND COLUMN_NAME = 'created_by'
);
SET @sql := IF(@col_exists = 0,
    'ALTER TABLE `teacher_invitation_code` ADD COLUMN `created_by` bigint DEFAULT NULL COMMENT ''创建人ID''',
    'SELECT 1'
);
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @col_exists := (
    SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'teacher_invitation_code' AND COLUMN_NAME = 'used_by'
);
SET @sql := IF(@col_exists = 0,
    'ALTER TABLE `teacher_invitation_code` ADD COLUMN `used_by` bigint DEFAULT NULL COMMENT ''使用人ID''',
    'SELECT 1'
);
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @col_exists := (
    SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'knowledge_base' AND COLUMN_NAME = 'class_ids'
);
SET @sql := IF(@col_exists = 0,
    'ALTER TABLE `knowledge_base` ADD COLUMN `class_ids` json DEFAULT NULL COMMENT ''关联班级ID列表''',
    'SELECT 1'
);
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @col_exists := (
    SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'knowledge_resource' AND COLUMN_NAME = 'metadata'
);
SET @sql := IF(@col_exists = 0,
    'ALTER TABLE `knowledge_resource` ADD COLUMN `metadata` json DEFAULT NULL COMMENT ''元数据''',
    'SELECT 1'
);
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @col_exists := (
    SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'course' AND COLUMN_NAME = 'topics'
);
SET @sql := IF(@col_exists = 0,
    'ALTER TABLE `course` ADD COLUMN `topics` json DEFAULT NULL COMMENT ''主题''',
    'SELECT 1'
);
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @col_exists := (
    SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'course' AND COLUMN_NAME = 'student_ids'
);
SET @sql := IF(@col_exists = 0,
    'ALTER TABLE `course` ADD COLUMN `student_ids` json DEFAULT NULL COMMENT ''学生ID列表''',
    'SELECT 1'
);
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @col_exists := (
    SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'course' AND COLUMN_NAME = 'knowledge_base_ids'
);
SET @sql := IF(@col_exists = 0,
    'ALTER TABLE `course` ADD COLUMN `knowledge_base_ids` json DEFAULT NULL COMMENT ''知识库ID列表''',
    'SELECT 1'
);
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @col_exists := (
    SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'knowledge_document' AND COLUMN_NAME = 'original_file_name'
);
SET @sql := IF(@col_exists = 0,
    'ALTER TABLE `knowledge_document` ADD COLUMN `original_file_name` varchar(255) DEFAULT NULL COMMENT ''原始文件名''',
    'SELECT 1'
);
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @col_exists := (
    SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'knowledge_document' AND COLUMN_NAME = 'file_type'
);
SET @sql := IF(@col_exists = 0,
    'ALTER TABLE `knowledge_document` ADD COLUMN `file_type` varchar(50) DEFAULT NULL COMMENT ''文件类型''',
    'SELECT 1'
);
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @col_exists := (
    SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'knowledge_document' AND COLUMN_NAME = 'file_size'
);
SET @sql := IF(@col_exists = 0,
    'ALTER TABLE `knowledge_document` ADD COLUMN `file_size` bigint DEFAULT NULL COMMENT ''文件大小''',
    'SELECT 1'
);
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @col_exists := (
    SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'knowledge_document' AND COLUMN_NAME = 'page_count'
);
SET @sql := IF(@col_exists = 0,
    'ALTER TABLE `knowledge_document` ADD COLUMN `page_count` int DEFAULT NULL COMMENT ''页数/片段数''',
    'SELECT 1'
);
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @col_exists := (
    SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'knowledge_document' AND COLUMN_NAME = 'status'
);
SET @sql := IF(@col_exists = 0,
    'ALTER TABLE `knowledge_document` ADD COLUMN `status` varchar(50) DEFAULT NULL COMMENT ''处理状态''',
    'SELECT 1'
);
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @col_exists := (
    SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'knowledge_document' AND COLUMN_NAME = 'chunk_count'
);
SET @sql := IF(@col_exists = 0,
    'ALTER TABLE `knowledge_document` ADD COLUMN `chunk_count` int DEFAULT NULL COMMENT ''分块数量''',
    'SELECT 1'
);
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @col_exists := (
    SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'knowledge_document' AND COLUMN_NAME = 'storage_url'
);
SET @sql := IF(@col_exists = 0,
    'ALTER TABLE `knowledge_document` ADD COLUMN `storage_url` varchar(500) DEFAULT NULL COMMENT ''存储地址''',
    'SELECT 1'
);
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @col_exists := (
    SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'knowledge_document' AND COLUMN_NAME = 'storage_object'
);
SET @sql := IF(@col_exists = 0,
    'ALTER TABLE `knowledge_document` ADD COLUMN `storage_object` varchar(255) DEFAULT NULL COMMENT ''存储对象名''',
    'SELECT 1'
);
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @col_exists := (
    SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'knowledge_document' AND COLUMN_NAME = 'uploader_id'
);
SET @sql := IF(@col_exists = 0,
    'ALTER TABLE `knowledge_document` ADD COLUMN `uploader_id` bigint DEFAULT NULL COMMENT ''上传用户ID''',
    'SELECT 1'
);
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @col_exists := (
    SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'knowledge_document' AND COLUMN_NAME = 'uploaded_at'
);
SET @sql := IF(@col_exists = 0,
    'ALTER TABLE `knowledge_document` ADD COLUMN `uploaded_at` datetime DEFAULT NULL COMMENT ''上传时间''',
    'SELECT 1'
);
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @col_exists := (
    SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'knowledge_document' AND COLUMN_NAME = 'processed_at'
);
SET @sql := IF(@col_exists = 0,
    'ALTER TABLE `knowledge_document` ADD COLUMN `processed_at` datetime DEFAULT NULL COMMENT ''处理完成时间''',
    'SELECT 1'
);
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @col_exists := (
    SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'knowledge_document' AND COLUMN_NAME = 'failure_reason'
);
SET @sql := IF(@col_exists = 0,
    'ALTER TABLE `knowledge_document` ADD COLUMN `failure_reason` text DEFAULT NULL COMMENT ''失败原因''',
    'SELECT 1'
);
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @col_exists := (
    SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'knowledge_document' AND COLUMN_NAME = 'is_deleted'
);
SET @sql := IF(@col_exists = 0,
    'ALTER TABLE `knowledge_document` ADD COLUMN `is_deleted` tinyint(1) DEFAULT 0 COMMENT ''是否删除''',
    'SELECT 1'
);
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @col_exists := (
    SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'knowledge_document_chunk' AND COLUMN_NAME = 'token_count'
);
SET @sql := IF(@col_exists = 0,
    'ALTER TABLE `knowledge_document_chunk` ADD COLUMN `token_count` int DEFAULT NULL COMMENT ''Token数量''',
    'SELECT 1'
);
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @col_exists := (
    SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'knowledge_document_chunk' AND COLUMN_NAME = 'vector_id'
);
SET @sql := IF(@col_exists = 0,
    'ALTER TABLE `knowledge_document_chunk` ADD COLUMN `vector_id` varchar(128) DEFAULT NULL COMMENT ''向量ID''',
    'SELECT 1'
);
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @col_exists := (
    SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'knowledge_document_chunk' AND COLUMN_NAME = 'vector_dimension'
);
SET @sql := IF(@col_exists = 0,
    'ALTER TABLE `knowledge_document_chunk` ADD COLUMN `vector_dimension` int DEFAULT NULL COMMENT ''向量维度''',
    'SELECT 1'
);
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @col_exists := (
    SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'rag_ingestion_task' AND COLUMN_NAME = 'stage'
);
SET @sql := IF(@col_exists = 0,
    'ALTER TABLE `rag_ingestion_task` ADD COLUMN `stage` varchar(50) DEFAULT NULL COMMENT ''处理阶段''',
    'SELECT 1'
);
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @col_exists := (
    SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'rag_ingestion_task' AND COLUMN_NAME = 'status'
);
SET @sql := IF(@col_exists = 0,
    'ALTER TABLE `rag_ingestion_task` ADD COLUMN `status` varchar(50) DEFAULT NULL COMMENT ''任务状态''',
    'SELECT 1'
);
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @col_exists := (
    SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'rag_ingestion_task' AND COLUMN_NAME = 'progress'
);
SET @sql := IF(@col_exists = 0,
    'ALTER TABLE `rag_ingestion_task` ADD COLUMN `progress` int DEFAULT NULL COMMENT ''进度''',
    'SELECT 1'
);
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @col_exists := (
    SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'rag_ingestion_task' AND COLUMN_NAME = 'error_message'
);
SET @sql := IF(@col_exists = 0,
    'ALTER TABLE `rag_ingestion_task` ADD COLUMN `error_message` text DEFAULT NULL COMMENT ''错误信息''',
    'SELECT 1'
);
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @col_exists := (
    SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'rag_ingestion_task' AND COLUMN_NAME = 'duration_ms'
);
SET @sql := IF(@col_exists = 0,
    'ALTER TABLE `rag_ingestion_task` ADD COLUMN `duration_ms` bigint DEFAULT NULL COMMENT ''耗时(毫秒)''',
    'SELECT 1'
);
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @col_exists := (
    SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'rag_ingestion_task' AND COLUMN_NAME = 'completed_at'
);
SET @sql := IF(@col_exists = 0,
    'ALTER TABLE `rag_ingestion_task` ADD COLUMN `completed_at` datetime DEFAULT NULL COMMENT ''完成时间''',
    'SELECT 1'
);
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @col_exists := (
    SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'user_document' AND COLUMN_NAME = 'object_path'
);
SET @sql := IF(@col_exists = 0,
    'ALTER TABLE `user_document` ADD COLUMN `object_path` varchar(500) DEFAULT NULL COMMENT ''对象存储路径''',
    'SELECT 1'
);
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @col_exists := (
    SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'user_document' AND COLUMN_NAME = 'tags'
);
SET @sql := IF(@col_exists = 0,
    'ALTER TABLE `user_document` ADD COLUMN `tags` varchar(255) DEFAULT NULL COMMENT ''标签''',
    'SELECT 1'
);
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @col_exists := (
    SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'circuit_design' AND COLUMN_NAME = 'description'
);
SET @sql := IF(@col_exists = 0,
    'ALTER TABLE `circuit_design` ADD COLUMN `description` text DEFAULT NULL COMMENT ''描述''',
    'SELECT 1'
);
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @col_exists := (
    SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'circuit_design' AND COLUMN_NAME = 'data'
);
SET @sql := IF(@col_exists = 0,
    'ALTER TABLE `circuit_design` ADD COLUMN `data` longtext DEFAULT NULL COMMENT ''电路JSON数据''',
    'SELECT 1'
);
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @col_exists := (
    SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'circuit_design' AND COLUMN_NAME = 'is_deleted'
);
SET @sql := IF(@col_exists = 0,
    'ALTER TABLE `circuit_design` ADD COLUMN `is_deleted` tinyint(1) DEFAULT 0 COMMENT ''逻辑删除标记''',
    'SELECT 1'
);
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- Optional indexes for RAG workload
SET @idx_exists := (
    SELECT COUNT(*) FROM INFORMATION_SCHEMA.STATISTICS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'knowledge_document' AND INDEX_NAME = 'idx_kd_kb_status'
);
SET @sql := IF(@idx_exists = 0,
    'CREATE INDEX idx_kd_kb_status ON knowledge_document (knowledge_base_id, status)',
    'SELECT 1'
);
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @idx_exists := (
    SELECT COUNT(*) FROM INFORMATION_SCHEMA.STATISTICS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'knowledge_document' AND INDEX_NAME = 'idx_kd_created_at'
);
SET @sql := IF(@idx_exists = 0,
    'CREATE INDEX idx_kd_created_at ON knowledge_document (created_at)',
    'SELECT 1'
);
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @idx_exists := (
    SELECT COUNT(*) FROM INFORMATION_SCHEMA.STATISTICS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'knowledge_document_chunk' AND INDEX_NAME = 'idx_kdc_doc_idx'
);
SET @sql := IF(@idx_exists = 0,
    'CREATE INDEX idx_kdc_doc_idx ON knowledge_document_chunk (document_id, chunk_index)',
    'SELECT 1'
);
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @idx_exists := (
    SELECT COUNT(*) FROM INFORMATION_SCHEMA.STATISTICS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'rag_ingestion_task' AND INDEX_NAME = 'idx_rit_status'
);
SET @sql := IF(@idx_exists = 0,
    'CREATE INDEX idx_rit_status ON rag_ingestion_task (status, created_at)',
    'SELECT 1'
);
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

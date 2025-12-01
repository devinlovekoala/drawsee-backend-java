-- MySQL数据库初始化脚本（2025-05-01）
-- 包含结构定义与基础测试数据，密码使用BCrypt加密

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

DROP TABLE IF EXISTS `admin`;
DROP TABLE IF EXISTS `ai_task`;
DROP TABLE IF EXISTS `circuit_design`;
DROP TABLE IF EXISTS `class_member`;
DROP TABLE IF EXISTS `class`;
DROP TABLE IF EXISTS `conversation`;
DROP TABLE IF EXISTS `course`;
DROP TABLE IF EXISTS `invitation_code`;
DROP TABLE IF EXISTS `knowledge_document_chunk`;
DROP TABLE IF EXISTS `knowledge_document`;
DROP TABLE IF EXISTS `knowledge_resource`;
DROP TABLE IF EXISTS `knowledge`;
DROP TABLE IF EXISTS `knowledge_base`;
DROP TABLE IF EXISTS `node`;
DROP TABLE IF EXISTS `rag_ingestion_task`;
DROP TABLE IF EXISTS `teacher_invitation_code`;
DROP TABLE IF EXISTS `teacher`;
DROP TABLE IF EXISTS `user_document`;
DROP TABLE IF EXISTS `user`;

-- 用户表
CREATE TABLE `user` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '用户唯一ID',
  `username` varchar(50) NOT NULL COMMENT '用户名（唯一）',
  `password` varchar(255) NOT NULL COMMENT 'BCrypt哈希密码',
  `created_at` datetime NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` datetime NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `is_deleted` tinyint(1) NULL DEFAULT 0 COMMENT '逻辑删除标记',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE KEY `uk_user_username` (`username`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '用户表';

-- 管理员表
CREATE TABLE `admin` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `user_id` bigint NOT NULL COMMENT '对应用户ID',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE KEY `uk_admin_user` (`user_id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '管理员表';

-- 教师表
CREATE TABLE `teacher` (
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

-- 会话表
CREATE TABLE `conversation` (
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

-- 节点表
CREATE TABLE `node` (
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

-- AI任务表
CREATE TABLE `ai_task` (
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

-- 邀请码表
CREATE TABLE `invitation_code` (
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

-- 教师邀请码表
CREATE TABLE `teacher_invitation_code` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `code` varchar(255) NOT NULL COMMENT '邀请码',
  `course_id` varchar(100) DEFAULT NULL COMMENT '关联课程ID',
  `created_by` bigint NOT NULL COMMENT '创建人ID',
  `used_by` bigint DEFAULT NULL COMMENT '使用人ID',
  `created_at` datetime NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `used_at` datetime DEFAULT NULL COMMENT '使用时间',
  `is_active` tinyint(1) NULL DEFAULT 1 COMMENT '是否有效',
  `remark` varchar(255) DEFAULT NULL COMMENT '备注',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE KEY `uk_teacher_invite_code` (`code`),
  KEY `idx_teacher_invite_used_by` (`used_by`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '教师邀请码表';

-- 班级表
CREATE TABLE `class` (
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

-- 班级成员表
CREATE TABLE `class_member` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `class_id` bigint NOT NULL COMMENT '班级ID',
  `user_id` bigint NOT NULL COMMENT '用户ID',
  `joined_at` datetime NULL DEFAULT CURRENT_TIMESTAMP COMMENT '加入时间',
  `is_deleted` tinyint(1) NULL DEFAULT 0 COMMENT '逻辑删除标记',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE KEY `uk_class_member` (`class_id`,`user_id`),
  KEY `idx_class_member_user` (`user_id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '班级成员表';

-- 知识库表
CREATE TABLE `knowledge_base` (
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

-- 知识点表
CREATE TABLE `knowledge` (
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

-- 知识点资源表
CREATE TABLE `knowledge_resource` (
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

-- 课程表
CREATE TABLE `course` (
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

-- 知识库文档表
CREATE TABLE `knowledge_document` (
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

-- 知识库文档分块表
CREATE TABLE `knowledge_document_chunk` (
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

-- RAG入库任务表
CREATE TABLE `rag_ingestion_task` (
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

-- 用户文档表
CREATE TABLE `user_document` (
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

-- 电路设计表
CREATE TABLE `circuit_design` (
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

-- 基础数据预热
INSERT INTO `user` (id, username, password, created_at, updated_at, is_deleted) VALUES
  (1, 'drawsee-admin', '$2b$12$56GjAb084tcc89Wnmrw1Se6VJhJuXMiaeedJAHONiQLWrnQdbHlra', NOW(), NOW(), 0),
  (2, 'teacher', '$2b$12$6Ar5rQ8rAUkqiDsyUZGTte20ciZNw4ldBsWoWb4DnPbi0TTbLJBiq', NOW(), NOW(), 0),
  (3, 'student', '$2b$12$PSS5PUc12kipI4qc.4ioU.7YSdyJsH6IUhzq42wqWSFWElu/pLcnO', NOW(), NOW(), 0);

INSERT INTO `admin` (id, user_id) VALUES (1, 1);

INSERT INTO `teacher` (id, user_id, title, organization, created_at, updated_at, is_deleted) VALUES
  (1, 2, '副教授', '北京邮电大学', NOW(), NOW(), 0);

INSERT INTO `class` (id, name, description, class_code, teacher_id, created_at, updated_at, is_deleted) VALUES
  (1, '测试班级', '用于系统测试的示例班级', '123456', 2, NOW(), NOW(), 0);

INSERT INTO `class_member` (id, class_id, user_id, joined_at, is_deleted) VALUES
  (1, 1, 3, NOW(), 0);

INSERT INTO `knowledge_base` (
  id, name, description, subject, invitation_code, creator_id, class_ids,
  created_at, updated_at, members, is_deleted, is_published, rag_enabled,
  rag_knowledge_id, rag_document_count, rag_dataset_id, sync_to_rag_flow,
  rag_sync_status, last_sync_time
) VALUES (
  'kb-test-001',
  '物理基础知识库',
  '包含高中物理基础知识',
  '物理',
  'ABCDEF',
  2,
  NULL,
  NOW(),
  NOW(),
  JSON_ARRAY(2),
  0,
  1,
  0,
  NULL,
  0,
  NULL,
  0,
  'pending',
  NULL
);

INSERT INTO `course` (
  id, name, code, class_code, description, subject, topics, creator_id,
  creator_role, student_ids, knowledge_base_ids, created_at, updated_at, is_deleted
) VALUES (
  'course-test-001',
  '高中物理基础',
  'PHY101',
  'XYZ123',
  '高中物理基础课程',
  '物理',
  NULL,
  2,
  'TEACHER',
  JSON_ARRAY(3),
  JSON_ARRAY('kb-test-001'),
  NOW(),
  NOW(),
  0
);

SET FOREIGN_KEY_CHECKS = 1;

-- 知识库相关表补丁脚本
-- 用于添加缺失的知识库、知识点、知识点资源、课程等表

-- 知识库表
DROP TABLE IF EXISTS `knowledge_base`;
CREATE TABLE `knowledge_base` (
  `id` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '知识库唯一ID',
  `name` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '知识库名称',
  `description` text CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '知识库描述',
  `subject` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '学科',
  `invitation_code` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '邀请码',
  `creator_id` bigint NOT NULL COMMENT '创建者ID',
  `created_at` datetime(0) NULL DEFAULT CURRENT_TIMESTAMP(0) COMMENT '创建时间',
  `updated_at` datetime(0) NULL DEFAULT CURRENT_TIMESTAMP(0) ON UPDATE CURRENT_TIMESTAMP(0) COMMENT '更新时间',
  `members` json NULL COMMENT '成员列表（JSON存储）',
  `is_deleted` tinyint(1) NULL DEFAULT 0 COMMENT '是否已删除',
  `is_published` tinyint(1) NULL DEFAULT 0 COMMENT '是否已发布',
  `rag_enabled` tinyint(1) NULL DEFAULT 0 COMMENT '是否启用RAG',
  `rag_knowledge_id` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT 'RAG知识库ID',
  `rag_document_count` int NULL DEFAULT 0 COMMENT 'RAG文档数量',
  `rag_dataset_id` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT 'RAG数据集ID',
  `sync_to_rag_flow` tinyint(1) NULL DEFAULT 0 COMMENT '是否同步到RAG Flow',
  `rag_sync_status` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT 'pending' COMMENT 'RAG同步状态',
  `last_sync_time` datetime(0) NULL DEFAULT NULL COMMENT '最后同步时间',
  PRIMARY KEY (`id`) USING BTREE,
  INDEX `idx_knowledge_base_subject`(`subject`) USING BTREE,
  INDEX `idx_knowledge_base_creator_id`(`creator_id`) USING BTREE,
  INDEX `idx_knowledge_base_is_published`(`is_published`) USING BTREE,
  INDEX `idx_knowledge_base_name`(`name`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '知识库表' ROW_FORMAT = Dynamic;

-- 知识点表
DROP TABLE IF EXISTS `knowledge`;
CREATE TABLE `knowledge` (
  `id` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '知识点唯一ID',
  `name` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '知识点名称',
  `subject` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '学科',
  `aliases` json NULL COMMENT '别名列表（JSON存储）',
  `level` int NULL DEFAULT NULL COMMENT '难度级别',
  `parent_id` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '父知识点ID',
  `children_ids` json NULL COMMENT '子知识点ID列表（JSON存储）',
  `knowledge_base_id` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '所属知识库ID',
  `creator_id` bigint NOT NULL COMMENT '创建者ID',
  `created_at` datetime(0) NULL DEFAULT CURRENT_TIMESTAMP(0) COMMENT '创建时间',
  `updated_at` datetime(0) NULL DEFAULT CURRENT_TIMESTAMP(0) ON UPDATE CURRENT_TIMESTAMP(0) COMMENT '更新时间',
  `is_deleted` tinyint(1) NULL DEFAULT 0 COMMENT '是否已删除',
  PRIMARY KEY (`id`) USING BTREE,
  INDEX `idx_knowledge_subject`(`subject`) USING BTREE,
  INDEX `idx_knowledge_parent_id`(`parent_id`) USING BTREE,
  INDEX `idx_knowledge_knowledge_base_id`(`knowledge_base_id`) USING BTREE,
  INDEX `idx_knowledge_creator_id`(`creator_id`) USING BTREE,
  INDEX `idx_knowledge_name`(`name`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '知识点表' ROW_FORMAT = Dynamic;

-- 知识点资源表
DROP TABLE IF EXISTS `knowledge_resource`;
CREATE TABLE `knowledge_resource` (
  `id` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '资源唯一ID',
  `knowledge_id` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '所属知识点ID',
  `knowledge_base_id` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '所属知识库ID',
  `resource_type` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '资源类型',
  `title` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '资源标题',
  `description` text CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '资源描述',
  `url` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '资源URL',
  `local_path` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '本地资源路径',
  `cover_url` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '资源封面图URL',
  `size` bigint NULL DEFAULT NULL COMMENT '资源大小（字节）',
  `duration` int NULL DEFAULT NULL COMMENT '资源持续时长（秒）',
  `creator_id` bigint NOT NULL COMMENT '创建者ID',
  `created_at` datetime(0) NULL DEFAULT CURRENT_TIMESTAMP(0) COMMENT '创建时间',
  `updated_at` datetime(0) NULL DEFAULT CURRENT_TIMESTAMP(0) ON UPDATE CURRENT_TIMESTAMP(0) COMMENT '更新时间',
  `is_deleted` tinyint(1) NULL DEFAULT 0 COMMENT '是否已删除',
  `metadata` json NULL COMMENT '资源元数据（JSON存储）',
  PRIMARY KEY (`id`) USING BTREE,
  INDEX `idx_knowledge_resource_knowledge_id`(`knowledge_id`) USING BTREE,
  INDEX `idx_knowledge_resource_knowledge_base_id`(`knowledge_base_id`) USING BTREE,
  INDEX `idx_knowledge_resource_creator_id`(`creator_id`) USING BTREE,
  INDEX `idx_knowledge_resource_type`(`resource_type`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '知识点资源表' ROW_FORMAT = Dynamic;

-- 课程表
DROP TABLE IF EXISTS `course`;
CREATE TABLE `course` (
  `id` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '课程唯一ID',
  `name` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '课程名称',
  `code` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '课程代码',
  `class_code` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '班级代码',
  `description` text CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '课程描述',
  `subject` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '学科',
  `topics` json NULL COMMENT '主题列表（JSON存储）',
  `creator_id` bigint NOT NULL COMMENT '创建者ID',
  `creator_role` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '创建者角色',
  `student_ids` json NULL COMMENT '学生ID列表（JSON存储）',
  `knowledge_base_ids` json NULL COMMENT '知识库ID列表（JSON存储）',
  `created_at` datetime(0) NULL DEFAULT CURRENT_TIMESTAMP(0) COMMENT '创建时间',
  `updated_at` datetime(0) NULL DEFAULT CURRENT_TIMESTAMP(0) ON UPDATE CURRENT_TIMESTAMP(0) COMMENT '更新时间',
  `is_deleted` tinyint(1) NULL DEFAULT 0 COMMENT '是否已删除',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `code`(`code`) USING BTREE,
  UNIQUE INDEX `class_code`(`class_code`) USING BTREE,
  INDEX `idx_course_subject`(`subject`) USING BTREE,
  INDEX `idx_course_creator_id`(`creator_id`) USING BTREE,
  INDEX `idx_course_creator_role`(`creator_role`) USING BTREE,
  INDEX `idx_course_name`(`name`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '课程表' ROW_FORMAT = Dynamic;

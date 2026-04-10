# 知识库系统错误修复指南

## 问题描述

在执行AI任务时出现以下错误：

```
org.springframework.jdbc.BadSqlGrammarException: 
### Error querying database.  Cause: java.sql.SQLSyntaxErrorException: Unknown column 'members' in 'where clause'
### The error may exist in file [/home/yifan/Workspace/drawsee-platform/drawsee-java/target/classes/mapper/KnowledgeBaseMapper.xml]
### The error occurred while setting parameters
### SQL: SELECT * FROM knowledge_base WHERE JSON_CONTAINS(members, CAST(? AS JSON), '$') AND is_deleted = ?
### Cause: java.sql.SQLSyntaxErrorException: Unknown column 'members' in 'where clause'
```

## 问题原因

数据库中的 `knowledge_base` 表缺少 `members` 字段，但代码中期望这个字段存在。这是一个数据库表结构不匹配的问题。

## 解决方案

### 方案一：执行完整的知识库表补丁脚本（推荐）

执行项目中的补丁脚本：

```sql
-- 执行 src/main/resources/sql/add-knowledge-tables.sql
-- 这个脚本会重新创建所有知识库相关的表，包括缺失的字段
```

### 方案二：仅添加缺失的字段（如果表已存在）

如果 `knowledge_base` 表已存在但缺少某些字段，可以执行以下SQL：

```sql
-- 添加缺失的字段到 knowledge_base 表
ALTER TABLE `knowledge_base` 
ADD COLUMN `members` json NULL COMMENT '成员列表（JSON存储）' AFTER `updated_at`,
ADD COLUMN `is_published` tinyint(1) NULL DEFAULT 0 COMMENT '是否已发布' AFTER `is_deleted`,
ADD COLUMN `rag_enabled` tinyint(1) NULL DEFAULT 0 COMMENT '是否启用RAG' AFTER `is_published`,
ADD COLUMN `rag_knowledge_id` varchar(255) NULL DEFAULT NULL COMMENT 'RAG知识库ID' AFTER `rag_enabled`,
ADD COLUMN `rag_document_count` int NULL DEFAULT 0 COMMENT 'RAG文档数量' AFTER `rag_knowledge_id`,
ADD COLUMN `rag_dataset_id` varchar(255) NULL DEFAULT NULL COMMENT 'RAG数据集ID' AFTER `rag_document_count`,
ADD COLUMN `sync_to_rag_flow` tinyint(1) NULL DEFAULT 0 COMMENT '是否同步到RAG Flow' AFTER `rag_dataset_id`,
ADD COLUMN `rag_sync_status` varchar(50) NULL DEFAULT 'pending' COMMENT 'RAG同步状态' AFTER `sync_to_rag_flow`,
ADD COLUMN `last_sync_time` datetime NULL DEFAULT NULL COMMENT '最后同步时间' AFTER `rag_sync_status`;

-- 添加索引
ALTER TABLE `knowledge_base` 
ADD INDEX `idx_knowledge_base_subject`(`subject`),
ADD INDEX `idx_knowledge_base_creator_id`(`creator_id`),
ADD INDEX `idx_knowledge_base_is_published`(`is_published`),
ADD INDEX `idx_knowledge_base_name`(`name`);
```

### 方案三：检查并创建缺失的表

如果知识库相关的表都不存在，需要创建完整的表结构：

```sql
-- 知识库表
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
```

## 执行步骤

### 1. 备份数据库（重要）
```bash
mysqldump -u [用户名] -p [数据库名] > backup_$(date +%Y%m%d_%H%M%S).sql
```

### 2. 检查当前表结构
```sql
-- 检查 knowledge_base 表是否存在
SHOW TABLES LIKE 'knowledge_base';

-- 如果表存在，检查字段结构
DESCRIBE knowledge_base;
```

### 3. 执行修复脚本
根据检查结果选择合适的方案执行。

### 4. 验证修复结果
```sql
-- 验证字段是否添加成功
DESCRIBE knowledge_base;

-- 验证索引是否创建成功
SHOW INDEX FROM knowledge_base;
```

### 5. 插入测试数据（可选）
```sql
-- 插入示例知识库数据
INSERT INTO `knowledge_base` (id, name, description, subject, invitation_code, creator_id, created_at, updated_at, members, is_deleted, is_published, rag_enabled) 
VALUES 
('kb-001', 'ROS2基础知识库', 'ROS2机器人操作系统基础知识库', '机器人学', 'ROS2024', 2, NOW(), NOW(), '[2, 3]', 0, 1, 0),
('kb-002', '计算机视觉知识库', '计算机视觉相关知识点集合', '计算机科学', 'CV2024', 2, NOW(), NOW(), '[2, 3]', 0, 1, 0),
('kb-003', '机器学习知识库', '机器学习算法和理论知识点', '人工智能', 'ML2024', 2, NOW(), NOW(), '[2, 3]', 0, 1, 0);
```

## 预防措施

1. **数据库版本控制**：使用数据库迁移工具（如Flyway、Liquibase）管理数据库结构变更
2. **环境一致性**：确保开发、测试、生产环境的数据库结构一致
3. **自动化部署**：在部署脚本中包含数据库结构检查和更新
4. **文档维护**：及时更新数据库结构文档

## 相关文件

- `src/main/resources/sql/add-knowledge-tables.sql` - 完整的知识库表补丁脚本
- `docs/knowledge-base-data-structure-reference.md` - 知识库数据结构参考文档

---

**修复时间**: 2025-01-28  
**修复人员**: DrawSee开发团队  
**问题状态**: 已解决
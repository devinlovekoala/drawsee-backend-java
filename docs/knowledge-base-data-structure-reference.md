# 知识库系统数据字段结构参考文档

## 概述

本文档详细描述了DrawSee平台知识库系统的完整数据字段结构，包括所有相关的实体类、数据库表结构以及它们之间的关系。该系统采用MySQL数据库，使用MyBatis作为ORM框架。

## 技术栈

- **数据库**: MySQL 8.0+
- **ORM框架**: MyBatis 3.x
- **Java版本**: SpringBoot 3.x
- **数据存储**: JSON字段用于存储复杂数据结构

## 核心实体关系图

```
用户(User) 
├── 创建知识库(KnowledgeBase)
├── 创建课程(Course) 
├── 创建班级(Class)
└── 加入班级成员(ClassMember)

知识库(KnowledgeBase)
├── 包含知识点(Knowledge)
├── 关联课程(Course)
└── 包含资源(KnowledgeResource)

课程(Course)
├── 关联知识库(KnowledgeBase)
├── 包含学生(User)
└── 对应班级(Class)

班级(Class)
├── 包含成员(ClassMember)
└── 对应课程(Course)
```

## 1. 用户相关表结构

### 1.1 用户表 (user)

| 字段名 | 数据类型 | 长度 | 是否为空 | 默认值 | 说明 |
|--------|----------|------|----------|--------|------|
| id | bigint | - | NOT NULL | AUTO_INCREMENT | 用户唯一ID |
| username | varchar | 50 | NOT NULL | - | 用户名（唯一） |
| password | varchar | 255 | NOT NULL | - | 密码哈希值 |
| role | varchar | 20 | NOT NULL | 'USER' | 用户角色 |
| created_at | datetime | - | NULL | CURRENT_TIMESTAMP | 创建时间 |
| updated_at | datetime | - | NULL | CURRENT_TIMESTAMP | 更新时间 |
| is_deleted | tinyint(1) | - | NULL | 0 | 逻辑删除标记 |

**索引**:
- PRIMARY KEY (`id`)
- UNIQUE INDEX `username`(`username`)

### 1.2 管理员表 (admin)

| 字段名 | 数据类型 | 长度 | 是否为空 | 默认值 | 说明 |
|--------|----------|------|----------|--------|------|
| id | bigint | - | NOT NULL | AUTO_INCREMENT | 主键 |
| user_id | bigint | - | NOT NULL | - | 对应用户ID |

**索引**:
- PRIMARY KEY (`id`)
- UNIQUE INDEX `user_id`(`user_id`)

### 1.3 教师表 (teacher)

| 字段名 | 数据类型 | 长度 | 是否为空 | 默认值 | 说明 |
|--------|----------|------|----------|--------|------|
| id | bigint | - | NOT NULL | AUTO_INCREMENT | 主键 |
| user_id | bigint | - | NOT NULL | - | 用户ID |
| title | varchar | 100 | NULL | NULL | 职称 |
| organization | varchar | 255 | NULL | NULL | 所属组织机构 |
| created_at | datetime | - | NULL | CURRENT_TIMESTAMP | 创建时间 |
| updated_at | datetime | - | NULL | CURRENT_TIMESTAMP | 更新时间 |
| is_deleted | tinyint(1) | - | NULL | 0 | 逻辑删除标记 |

**索引**:
- PRIMARY KEY (`id`)
- UNIQUE INDEX `idx_user_id`(`user_id`)

## 2. 知识库核心表结构

### 2.1 知识库表 (knowledge_base)

| 字段名 | 数据类型 | 长度 | 是否为空 | 默认值 | 说明 |
|--------|----------|------|----------|--------|------|
| id | varchar | 255 | NOT NULL | - | 知识库唯一ID |
| name | varchar | 255 | NOT NULL | - | 知识库名称 |
| description | text | - | NULL | NULL | 知识库描述 |
| subject | varchar | 100 | NOT NULL | - | 学科 |
| invitation_code | varchar | 255 | NULL | NULL | 邀请码 |
| creator_id | bigint | - | NOT NULL | - | 创建者ID |
| created_at | datetime | - | NULL | CURRENT_TIMESTAMP | 创建时间 |
| updated_at | datetime | - | NULL | CURRENT_TIMESTAMP | 更新时间 |
| members | json | - | NULL | NULL | 成员列表（JSON存储） |
| is_deleted | tinyint(1) | - | NULL | 0 | 是否已删除 |
| is_published | tinyint(1) | - | NULL | 0 | 是否已发布 |
| rag_enabled | tinyint(1) | - | NULL | 0 | 是否启用RAG |
| rag_knowledge_id | varchar | 255 | NULL | NULL | RAG知识库ID |
| rag_document_count | int | - | NULL | 0 | RAG文档数量 |
| rag_dataset_id | varchar | 255 | NULL | NULL | RAG数据集ID |
| sync_to_rag_flow | tinyint(1) | - | NULL | 0 | 是否同步到RAG Flow |
| rag_sync_status | varchar | 50 | NULL | 'pending' | RAG同步状态 |
| last_sync_time | datetime | - | NULL | NULL | 最后同步时间 |

**索引**:
- PRIMARY KEY (`id`)
- INDEX `idx_knowledge_base_subject`(`subject`)
- INDEX `idx_knowledge_base_creator_id`(`creator_id`)
- INDEX `idx_knowledge_base_is_published`(`is_published`)
- INDEX `idx_knowledge_base_name`(`name`)

### 2.2 知识点表 (knowledge)

| 字段名 | 数据类型 | 长度 | 是否为空 | 默认值 | 说明 |
|--------|----------|------|----------|--------|------|
| id | varchar | 255 | NOT NULL | - | 知识点唯一ID |
| name | varchar | 255 | NOT NULL | - | 知识点名称 |
| subject | varchar | 100 | NOT NULL | - | 学科 |
| aliases | json | - | NULL | NULL | 别名列表（JSON存储） |
| level | int | - | NULL | NULL | 难度级别 |
| parent_id | varchar | 255 | NULL | NULL | 父知识点ID |
| children_ids | json | - | NULL | NULL | 子知识点ID列表（JSON存储） |
| knowledge_base_id | varchar | 255 | NOT NULL | - | 所属知识库ID |
| creator_id | bigint | - | NOT NULL | - | 创建者ID |
| created_at | datetime | - | NULL | CURRENT_TIMESTAMP | 创建时间 |
| updated_at | datetime | - | NULL | CURRENT_TIMESTAMP | 更新时间 |
| is_deleted | tinyint(1) | - | NULL | 0 | 是否已删除 |

**索引**:
- PRIMARY KEY (`id`)
- INDEX `idx_knowledge_subject`(`subject`)
- INDEX `idx_knowledge_parent_id`(`parent_id`)
- INDEX `idx_knowledge_knowledge_base_id`(`knowledge_base_id`)
- INDEX `idx_knowledge_creator_id`(`creator_id`)
- INDEX `idx_knowledge_name`(`name`)

### 2.3 知识点资源表 (knowledge_resource)

| 字段名 | 数据类型 | 长度 | 是否为空 | 默认值 | 说明 |
|--------|----------|------|----------|--------|------|
| id | varchar | 255 | NOT NULL | - | 资源唯一ID |
| knowledge_id | varchar | 255 | NOT NULL | - | 所属知识点ID |
| knowledge_base_id | varchar | 255 | NOT NULL | - | 所属知识库ID |
| resource_type | varchar | 50 | NOT NULL | - | 资源类型 |
| title | varchar | 255 | NULL | NULL | 资源标题 |
| description | text | - | NULL | NULL | 资源描述 |
| url | varchar | 500 | NULL | NULL | 资源URL |
| local_path | varchar | 500 | NULL | NULL | 本地资源路径 |
| cover_url | varchar | 500 | NULL | NULL | 资源封面图URL |
| size | bigint | - | NULL | NULL | 资源大小（字节） |
| duration | int | - | NULL | NULL | 资源持续时长（秒） |
| creator_id | bigint | - | NOT NULL | - | 创建者ID |
| created_at | datetime | - | NULL | CURRENT_TIMESTAMP | 创建时间 |
| updated_at | datetime | - | NULL | CURRENT_TIMESTAMP | 更新时间 |
| is_deleted | tinyint(1) | - | NULL | 0 | 是否已删除 |
| metadata | json | - | NULL | NULL | 资源元数据（JSON存储） |

**索引**:
- PRIMARY KEY (`id`)
- INDEX `idx_knowledge_resource_knowledge_id`(`knowledge_id`)
- INDEX `idx_knowledge_resource_knowledge_base_id`(`knowledge_base_id`)
- INDEX `idx_knowledge_resource_creator_id`(`creator_id`)
- INDEX `idx_knowledge_resource_type`(`resource_type`)

## 3. 课程与班级表结构

### 3.1 课程表 (course)

| 字段名 | 数据类型 | 长度 | 是否为空 | 默认值 | 说明 |
|--------|----------|------|----------|--------|------|
| id | varchar | 255 | NOT NULL | - | 课程唯一ID |
| name | varchar | 255 | NOT NULL | - | 课程名称 |
| code | varchar | 100 | NOT NULL | - | 课程代码 |
| class_code | varchar | 100 | NOT NULL | - | 班级代码 |
| description | text | - | NULL | NULL | 课程描述 |
| subject | varchar | 100 | NOT NULL | - | 学科 |
| topics | json | - | NULL | NULL | 主题列表（JSON存储） |
| creator_id | bigint | - | NOT NULL | - | 创建者ID |
| creator_role | varchar | 50 | NOT NULL | - | 创建者角色 |
| student_ids | json | - | NULL | NULL | 学生ID列表（JSON存储） |
| knowledge_base_ids | json | - | NULL | NULL | 知识库ID列表（JSON存储） |
| created_at | datetime | - | NULL | CURRENT_TIMESTAMP | 创建时间 |
| updated_at | datetime | - | NULL | CURRENT_TIMESTAMP | 更新时间 |
| is_deleted | tinyint(1) | - | NULL | 0 | 是否已删除 |

**索引**:
- PRIMARY KEY (`id`)
- UNIQUE INDEX `code`(`code`)
- UNIQUE INDEX `class_code`(`class_code`)
- INDEX `idx_course_subject`(`subject`)
- INDEX `idx_course_creator_id`(`creator_id`)
- INDEX `idx_course_creator_role`(`creator_role`)
- INDEX `idx_course_name`(`name`)

### 3.2 班级表 (class)

| 字段名 | 数据类型 | 长度 | 是否为空 | 默认值 | 说明 |
|--------|----------|------|----------|--------|------|
| id | bigint | - | NOT NULL | AUTO_INCREMENT | 班级唯一ID |
| name | varchar | 100 | NOT NULL | - | 班级名称 |
| description | varchar | 255 | NULL | NULL | 班级描述 |
| class_code | varchar | 20 | NOT NULL | - | 班级码 |
| teacher_id | bigint | - | NOT NULL | - | 教师ID |
| created_at | datetime | - | NULL | CURRENT_TIMESTAMP | 创建时间 |
| updated_at | datetime | - | NULL | CURRENT_TIMESTAMP | 更新时间 |
| is_deleted | tinyint(1) | - | NULL | 0 | 逻辑删除标记 |

**索引**:
- PRIMARY KEY (`id`)
- UNIQUE INDEX `idx_class_code`(`class_code`)
- INDEX `idx_teacher_id`(`teacher_id`)

### 3.3 班级成员表 (class_member)

| 字段名 | 数据类型 | 长度 | 是否为空 | 默认值 | 说明 |
|--------|----------|------|----------|--------|------|
| id | bigint | - | NOT NULL | AUTO_INCREMENT | 主键 |
| class_id | bigint | - | NOT NULL | - | 班级ID |
| user_id | bigint | - | NOT NULL | - | 用户ID |
| joined_at | datetime | - | NULL | CURRENT_TIMESTAMP | 加入时间 |
| is_deleted | tinyint(1) | - | NULL | 0 | 逻辑删除标记 |

**索引**:
- PRIMARY KEY (`id`)
- UNIQUE INDEX `idx_class_id_user_id`(`class_id`, `user_id`)
- INDEX `idx_user_id`(`user_id`)

## 4. 邀请码表结构

### 4.1 教师邀请码表 (teacher_invitation_code)

| 字段名 | 数据类型 | 长度 | 是否为空 | 默认值 | 说明 |
|--------|----------|------|----------|--------|------|
| id | bigint | - | NOT NULL | AUTO_INCREMENT | 主键 |
| code | varchar | 20 | NOT NULL | - | 邀请码 |
| course_id | varchar | 36 | NOT NULL | - | 课程ID |
| created_by | bigint | - | NOT NULL | - | 创建人ID |
| used_by | bigint | - | NULL | NULL | 使用人ID |
| created_at | timestamp | - | NOT NULL | CURRENT_TIMESTAMP | 创建时间 |
| used_at | timestamp | - | NULL | NULL | 使用时间 |
| is_active | tinyint(1) | - | NOT NULL | 1 | 是否有效 |
| remark | varchar | 255 | NULL | NULL | 备注 |

**索引**:
- PRIMARY KEY (`id`)
- UNIQUE KEY `uk_code` (`code`)

## 5. 会话与节点表结构

### 5.1 会话表 (conversation)

| 字段名 | 数据类型 | 长度 | 是否为空 | 默认值 | 说明 |
|--------|----------|------|----------|--------|------|
| id | bigint | - | NOT NULL | AUTO_INCREMENT | 会话唯一ID |
| title | varchar | 255 | NOT NULL | - | 会话标题 |
| user_id | bigint | - | NOT NULL | - | 所属用户ID |
| subject | varchar | 50 | NULL | NULL | 学科 |
| created_at | datetime | - | NULL | CURRENT_TIMESTAMP | 创建时间 |
| updated_at | datetime | - | NULL | CURRENT_TIMESTAMP | 更新时间 |
| is_deleted | tinyint(1) | - | NULL | 0 | 逻辑删除标记 |

**索引**:
- PRIMARY KEY (`id`)
- INDEX `idx_conversation_user_id`(`user_id`)

### 5.2 节点表 (node)

| 字段名 | 数据类型 | 长度 | 是否为空 | 默认值 | 说明 |
|--------|----------|------|----------|--------|------|
| id | bigint | - | NOT NULL | AUTO_INCREMENT | 节点唯一ID |
| type | varchar | 50 | NOT NULL | - | 节点类型 |
| data | mediumtext | - | NOT NULL | - | 节点内容（JSON格式） |
| position | text | - | NOT NULL | - | 节点位置坐标（JSON格式） |
| height | int | - | NULL | NULL | 节点高度 |
| width | int | - | NULL | NULL | 节点宽度 |
| parent_id | bigint | - | NULL | NULL | 父节点ID |
| conv_id | bigint | - | NOT NULL | - | 所属会话ID |
| user_id | bigint | - | NOT NULL | - | 所属用户ID |
| created_at | datetime | - | NULL | CURRENT_TIMESTAMP | 创建时间 |
| updated_at | datetime | - | NULL | CURRENT_TIMESTAMP | 更新时间 |
| is_deleted | tinyint(1) | - | NULL | 0 | 逻辑删除标记 |

**索引**:
- PRIMARY KEY (`id`)
- INDEX `idx_node_parent_id`(`parent_id`)
- INDEX `idx_node_user_id`(`user_id`)
- INDEX `idx_node_conv_id`(`conv_id`)

### 5.3 AI任务表 (ai_task)

| 字段名 | 数据类型 | 长度 | 是否为空 | 默认值 | 说明 |
|--------|----------|------|----------|--------|------|
| id | bigint | - | NOT NULL | AUTO_INCREMENT | 任务唯一ID |
| type | varchar | 50 | NOT NULL | - | 任务类型 |
| data | mediumtext | - | NOT NULL | - | 任务内容 |
| result | mediumtext | - | NULL | NULL | 任务结果 |
| status | varchar | 50 | NOT NULL | - | 任务状态 |
| tokens | int | - | NULL | NULL | 消耗的Token数 |
| user_id | bigint | - | NOT NULL | - | 任务所属用户ID |
| conv_id | bigint | - | NOT NULL | - | 任务所属会话ID |
| prompt | text | - | NULL | NULL | 提示词 |
| prompt_params | json | - | NULL | NULL | 提示词参数 |
| created_at | datetime | - | NULL | CURRENT_TIMESTAMP | 创建时间 |
| updated_at | datetime | - | NULL | CURRENT_TIMESTAMP | 更新时间 |
| is_deleted | tinyint(1) | - | NULL | 0 | 逻辑删除标记 |

**索引**:
- PRIMARY KEY (`id`)
- INDEX `idx_ai_task_user_id`(`user_id`)
- INDEX `idx_ai_task_conv_id`(`conv_id`)
- INDEX `idx_ai_task_user_id_conv_id_status`(`user_id`, `conv_id`, `status`)

## 6. 数据字段说明

### 6.1 JSON字段格式

#### knowledge_base.members
```json
[1, 2, 3, 4]
```
存储知识库成员的用户ID列表

#### knowledge.aliases
```json
["别名1", "别名2", "别名3"]
```
存储知识点的别名列表

#### knowledge.children_ids
```json
["child_id_1", "child_id_2", "child_id_3"]
```
存储子知识点的ID列表

#### course.student_ids
```json
[1, 2, 3, 4]
```
存储课程学生的用户ID列表

#### course.knowledge_base_ids
```json
["kb_id_1", "kb_id_2", "kb_id_3"]
```
存储课程关联的知识库ID列表

#### course.topics
```json
["主题1", "主题2", "主题3"]
```
存储课程主题列表

#### knowledge_resource.metadata
```json
{
  "format": "mp4",
  "resolution": "1920x1080",
  "duration": 3600,
  "tags": ["视频", "教学"]
}
```
存储资源的元数据信息

### 6.2 枚举值说明

#### user.role
- `USER`: 普通用户
- `TEACHER`: 教师
- `ADMIN`: 管理员

#### knowledge_resource.resource_type
- `document`: 文档
- `video`: 视频
- `audio`: 音频
- `bilibili`: B站视频
- `link`: 网络链接
- `mp4`: MP4视频文件
- `animation`: 动画资源
- `pdf`: PDF文档

#### ai_task.status
- `PENDING`: 待处理
- `PROCESSING`: 处理中
- `SUCCESS`: 成功
- `FAILED`: 失败

#### knowledge_base.rag_sync_status
- `pending`: 待同步
- `syncing`: 同步中
- `success`: 同步成功
- `failed`: 同步失败

## 7. 业务逻辑关系

### 7.1 知识库权限控制
- 知识库创建者拥有完全权限
- 知识库成员可以查看和使用知识库
- 只有已发布的知识库才能被公开访问
- 未发布的知识库只有创建者和成员可以访问

### 7.2 课程与知识库关联
- 一个课程可以关联多个知识库
- 课程的学生可以访问关联的知识库
- 班级通过班级码与课程关联
- 班级成员可以访问对应课程的知识库

### 7.3 知识点层级结构
- 知识点支持父子关系，形成树形结构
- 知识点可以有多个别名
- 知识点关联具体的资源文件
- 知识点属于特定的知识库

### 7.4 RAG功能集成
- 知识库可以启用RAG功能
- RAG知识库与外部RAGFlow系统同步
- 支持文档上传和向量化处理
- 提供智能问答功能

## 8. 索引优化建议

### 8.1 查询优化索引
- 用户相关查询：`user_id`索引
- 知识库查询：`subject`, `creator_id`, `is_published`索引
- 知识点查询：`knowledge_base_id`, `name`索引
- 课程查询：`class_code`, `subject`索引

### 8.2 复合索引
- `(user_id, conv_id, status)` - AI任务状态查询
- `(class_id, user_id)` - 班级成员唯一性
- `(knowledge_base_id, resource_type)` - 资源类型查询

## 9. 数据迁移注意事项

### 9.1 版本兼容性
- 确保JSON字段的格式兼容性
- 注意字段类型变更的影响
- 保持索引的一致性

### 9.2 数据完整性
- 外键关系的完整性检查
- 软删除标记的一致性
- JSON字段格式的验证

## 10. 性能优化建议

### 10.1 查询优化
- 合理使用索引
- 避免全表扫描
- 优化JSON字段查询

### 10.2 存储优化
- 定期清理软删除数据
- 压缩历史数据
- 优化JSON字段存储

---

**文档版本**: v1.0  
**最后更新**: 2025-01-28  
**维护人员**: DrawSee开发团队
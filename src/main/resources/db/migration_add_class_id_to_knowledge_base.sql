-- 为知识库表添加班级ID字段
-- 该脚本用于支持班级与知识库的关联，实现基于班级的RAG检索

ALTER TABLE knowledge_base ADD COLUMN class_id BIGINT NULL COMMENT '关联班级ID（可选）';

-- 添加索引以提高查询性能
CREATE INDEX idx_knowledge_base_class_id ON knowledge_base(class_id);

-- 添加外键约束（如果class表存在的话）
-- ALTER TABLE knowledge_base ADD CONSTRAINT fk_knowledge_base_class_id 
-- FOREIGN KEY (class_id) REFERENCES class(id) ON DELETE SET NULL;

-- 更新现有的复合索引，包含class_id字段
CREATE INDEX idx_knowledge_base_rag_status ON knowledge_base(rag_enabled, is_deleted, class_id);
# Weaviate向量库集成与班级RAG增强功能

## 完成的工作

### 1. Weaviate向量库配置完善
- ✅ 补充了`WeaviateConfig.java`配置类，支持Weaviate客户端Bean的创建
- ✅ 在`application.yaml`中完善了Weaviate配置项
- ✅ 在`pom.xml`中验证了Weaviate客户端依赖

### 2. RAG相关服务实现
- ✅ 完善了`WeaviateVectorStore.java`向量存储服务
- ✅ 实现了`RagQueryService.java`RAG检索服务，支持：
  - 向量相似性检索
  - 检索结果重排序
  - LLM增强回答生成
- ✅ 验证了`EmbeddingService.java`嵌入向量生成服务

### 3. 班级ID锁定知识库功能
- ✅ 为`KnowledgeBase`实体添加了`classId`字段
- ✅ 创建了`ClassKnowledgeService.java`服务，支持：
  - 根据班级ID获取可用知识库列表
  - 根据班级ID和用户ID获取可访问的知识库
  - 知识库与班级的关联管理
- ✅ 扩展了`KnowledgeBaseMapper`，添加了根据班级ID查询的方法
- ✅ 更新了`KnowledgeBaseMapper.xml`映射文件，支持class_id字段
- ✅ 创建了数据库迁移脚本`migration_add_class_id_to_knowledge_base.sql`

### 4. AI任务生成时的RAG集成
- ✅ 在`KnowledgeWorkFlow.java`中集成了RAG功能：
  - 添加了RAG增强方法`tryEnhanceWithRag()`
  - 在知识点生成前先进行RAG检索增强
  - 支持基于班级ID的知识库过滤
  - 记录RAG增强相关的元数据

## 核心功能特性

### 班级定向RAG检索
1. **班级知识库隔离**：每个班级可以关联特定的知识库，实现知识内容的定向检索
2. **权限控制**：用户只能检索其所在班级关联的知识库内容
3. **智能回退**：当班级没有关联知识库时，自动回退到用户个人知识库

### RAG增强流程
1. **向量检索**：使用Weaviate进行高效的向量相似性搜索
2. **多知识库支持**：同时检索多个知识库中的相关内容
3. **上下文增强**：将检索到的知识块作为上下文增强AI回答
4. **元数据记录**：记录RAG使用情况，便于调试和优化

### 数据库设计
- `knowledge_base`表新增`class_id`字段，支持班级关联
- 添加了相应的索引以优化查询性能
- 支持外键约束（可选）

## 配置说明

### Weaviate配置
```yaml
rag:
  weaviate:
    endpoint: ${WEAVIATE_ENDPOINT:http://127.0.0.1:8080}
    api-key: ${WEAVIATE_API_KEY:}
    class-prefix: ${WEAVIATE_CLASS_PREFIX:KB}
    chunk-size: ${WEAVIATE_CHUNK_SIZE:800}
    chunk-overlap: ${WEAVIATE_CHUNK_OVERLAP:200}
```

### 嵌入服务配置
```yaml
ragflow:
  embedding:
    api-url: ${RAGFLOW_EMBEDDING_API_URL:http://localhost:8081/v1/embeddings}
    api-key: ${RAGFLOW_EMBEDDING_API_KEY:}
    model: ${RAGFLOW_EMBEDDING_MODEL:text-embedding-3-small}
    dimension: ${RAGFLOW_EMBEDDING_DIMENSION:1536}
    api-provider: ${RAGFLOW_EMBEDDING_PROVIDER:openai}
```

## 使用场景

### 1. 班级教学场景
- 教师为班级关联特定的知识库（如课程材料、习题解答等）
- 学生在AI任务中自动获得班级相关的知识增强
- 保证学习内容的准确性和相关性

### 2. 个性化学习
- 用户可以访问个人知识库和班级知识库
- 系统根据用户权限动态筛选可访问的知识内容
- 支持知识库的灵活分配和管理

### 3. 知识质量控制
- 通过班级ID限制知识库范围，避免无关内容干扰
- 支持启用/禁用RAG功能的灵活控制
- 记录RAG使用情况，便于教师了解学生学习情况

## 技术架构

```
用户请求 → AI任务 → 班级ID验证 → 知识库权限筛选 → 向量检索 → 内容增强 → AI回答生成
                ↓
           Weaviate向量库 ← 嵌入服务 ← 知识文档
```

## 后续优化建议

1. **性能优化**：添加向量检索结果缓存
2. **智能重排序**：基于用户行为的检索结果重排序
3. **多模态支持**：支持图像、音频等多模态内容检索
4. **分析统计**：添加RAG使用情况的统计和分析功能
5. **批量操作**：支持知识库的批量分配和管理

## 测试建议

1. **单元测试**：对各个Service类进行单元测试
2. **集成测试**：测试整个RAG流程的端到端功能
3. **性能测试**：测试大规模知识库的检索性能
4. **边界测试**：测试异常情况和边界条件的处理
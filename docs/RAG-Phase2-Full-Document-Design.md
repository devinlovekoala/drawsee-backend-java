# RAG Phase 2: 完整文档RAG系统设计

**日期**: 2025-12-15
**版本**: v1.0
**状态**: 🎯 设计中

---

## 📋 Phase 1 回顾

### 已完成功能

✅ **电路图处理Pipeline**:
- Extract: PDF → 电路图切片 → MinIO存储
- Transform: VLM解析 → BOM + Topology + Caption
- Load: Caption向量化 → Qdrant存储

✅ **并发优化**:
- 使用`asyncio.to_thread()`实现真正并发
- 性能提升78%（32分钟 → 7分钟）

### 当前局限

❌ **只处理电路图**:
- 文本内容（理论说明、定义、公式）被忽略
- 表格数据未提取
- 纯文本页面无法检索

❌ **检索单一**:
- 只能通过电路图Caption检索
- 无法回答理论问题

---

## 🎯 Phase 2 目标

### 核心目标

实现**完整文档RAG**，支持：
1. ✅ 电路图内容（已完成）
2. 🆕 文本内容（段落、公式、定义）
3. 🆕 表格内容（参数表、对比表）
4. 🆕 混合检索（文本 + 电路图）

### 用户场景

**场景1**: 理论问题
```
用户: "什么是基尔霍夫电流定律？"
系统: [从文本chunks检索] → 返回定义和公式
```

**场景2**: 电路问题
```
用户: "请给我一个三极管放大电路"
系统: [从电路图caption检索] → 返回电路图 + BOM + 拓扑
```

**场景3**: 混合问题
```
用户: "BJT三极管的β值通常是多少？电路图怎么设计？"
系统: [混合检索] → 返回表格参数 + 相关电路图
```

---

## 🏗️ 架构设计

### 整体架构

```
┌─────────────────────────────────────────────────────────────┐
│                        PDF 文档                              │
└───────────────┬─────────────────────────────────────────────┘
                │
                ▼
┌───────────────────────────────────────────────────────────────┐
│                    Extract 阶段                                │
│  ┌──────────────────┐     ┌──────────────────┐               │
│  │  电路图提取      │     │  文本内容提取    │               │
│  │  (已实现)        │     │  (Phase 2新增)   │               │
│  │  ↓               │     │  ↓               │               │
│  │  CircuitMetadata │     │  TextChunk       │               │
│  └──────────────────┘     │  TableContent    │               │
│                            └──────────────────┘               │
└───────────────────────────────────────────────────────────────┘
                │
                ▼
┌───────────────────────────────────────────────────────────────┐
│                   Transform 阶段                               │
│  ┌──────────────────┐     ┌──────────────────┐               │
│  │  VLM解析电路图   │     │  文本语义增强    │               │
│  │  (已实现)        │     │  (Phase 2新增)   │               │
│  │  ↓               │     │  ↓               │               │
│  │  CircuitStructure│     │  EnrichedChunk   │               │
│  └──────────────────┘     └──────────────────┘               │
└───────────────────────────────────────────────────────────────┘
                │
                ▼
┌───────────────────────────────────────────────────────────────┐
│                     Load 阶段                                  │
│  ┌──────────────────┐     ┌──────────────────┐               │
│  │  电路图向量化    │     │  文本向量化      │               │
│  │  (已实现)        │     │  (Phase 2新增)   │               │
│  │  ↓               │     │  ↓               │               │
│  │  Qdrant          │     │  Qdrant          │               │
│  │  (circuits)      │     │  (text_chunks)   │               │
│  └──────────────────┘     └──────────────────┘               │
└───────────────────────────────────────────────────────────────┘
                │
                ▼
┌───────────────────────────────────────────────────────────────┐
│                   混合检索 (Hybrid RAG)                        │
│                                                                │
│  用户查询 →  向量化  →  并行检索:                             │
│                         - Qdrant (circuits)                    │
│                         - Qdrant (text_chunks)                 │
│                         ↓                                      │
│                      结果合并 + 重排序                         │
│                         ↓                                      │
│                      返回 Top-K 结果                           │
└───────────────────────────────────────────────────────────────┘
```

---

## 📊 数据库设计

### 新增表1: `document_text_chunk`

**用途**: 存储文档文本分块（用于文本检索）

```sql
CREATE TABLE `document_text_chunk` (
  `id` VARCHAR(64) PRIMARY KEY COMMENT 'Chunk ID (UUID)',
  `document_id` VARCHAR(64) NOT NULL COMMENT '关联knowledge_document.id',
  `knowledge_base_id` VARCHAR(64) NOT NULL COMMENT '知识库ID',
  `page_number` INT NOT NULL COMMENT '页码',

  -- 分块内容
  `chunk_text` TEXT NOT NULL COMMENT '分块文本内容',
  `chunk_type` VARCHAR(20) NOT NULL COMMENT 'paragraph/formula/definition/list',
  `chunk_index` INT NOT NULL COMMENT '文档内的chunk序号（全局排序）',

  -- 位置信息
  `bbox` JSON COMMENT '文本区域位置 {x, y, width, height}',

  -- 语义增强（可选）
  `semantic_summary` TEXT COMMENT 'LLM生成的chunk摘要（可选优化）',

  -- 向量化信息
  `embedding_id` VARCHAR(64) COMMENT 'Qdrant向量ID',
  `embedding_model` VARCHAR(128) COMMENT 'Embedding模型名称',

  -- 状态追踪
  `extraction_status` VARCHAR(20) DEFAULT 'pending' COMMENT 'pending/completed/failed',
  `failure_reason` TEXT,

  -- 时间戳
  `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP,
  `updated_at` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,

  INDEX `idx_document` (`document_id`),
  INDEX `idx_kb` (`knowledge_base_id`),
  INDEX `idx_page` (`page_number`),
  INDEX `idx_embedding` (`embedding_id`),
  INDEX `idx_status` (`extraction_status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='文档文本分块表';
```

### 新增表2: `document_table_content`

**用途**: 存储表格结构化数据

```sql
CREATE TABLE `document_table_content` (
  `id` VARCHAR(64) PRIMARY KEY COMMENT 'Table ID (UUID)',
  `document_id` VARCHAR(64) NOT NULL COMMENT '关联knowledge_document.id',
  `knowledge_base_id` VARCHAR(64) NOT NULL COMMENT '知识库ID',
  `page_number` INT NOT NULL COMMENT '页码',

  -- 表格数据
  `table_data` JSON NOT NULL COMMENT '表格内容 (rows + columns)',
  `table_caption` TEXT COMMENT '表格标题或描述',
  `table_index` INT NOT NULL COMMENT '文档内的表格序号',

  -- 位置信息
  `bbox` JSON COMMENT '表格区域位置',

  -- 向量化信息（表格caption或summary）
  `embedding_id` VARCHAR(64) COMMENT 'Qdrant向量ID',
  `embedding_model` VARCHAR(128),

  -- 状态追踪
  `extraction_status` VARCHAR(20) DEFAULT 'pending',
  `failure_reason` TEXT,

  -- 时间戳
  `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP,
  `updated_at` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,

  INDEX `idx_document` (`document_id`),
  INDEX `idx_kb` (`knowledge_base_id`),
  INDEX `idx_page` (`page_number`),
  INDEX `idx_embedding` (`embedding_id`),
  INDEX `idx_status` (`extraction_status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='文档表格内容表';
```

### 修改表: `etl_task`

**新增字段**:
```sql
ALTER TABLE `etl_task`
ADD COLUMN `total_text_chunks` INT DEFAULT 0 COMMENT '提取的文本块总数',
ADD COLUMN `processed_text_chunks` INT DEFAULT 0 COMMENT '已处理的文本块数',
ADD COLUMN `total_tables` INT DEFAULT 0 COMMENT '提取的表格总数',
ADD COLUMN `processed_tables` INT DEFAULT 0 COMMENT '已处理的表格数';
```

---

## 🔧 技术实现

### 1. Extract 阶段扩展

**新增文件**: `app/services/etl/text_extract.py`

```python
"""文本内容提取服务"""
import fitz  # PyMuPDF
from typing import List, Dict, Any

class TextChunkExtractor:
    """文档文本分块提取器"""

    def __init__(self):
        self.chunk_size = 500  # 字符数
        self.chunk_overlap = 50  # 重叠字符数

    async def extract_text_from_pdf(
        self,
        pdf_path: str,
        document_id: str,
        knowledge_base_id: str
    ) -> List[TextChunk]:
        """
        从PDF提取文本内容并分块

        策略:
        1. 遍历每一页，提取文本blocks
        2. 过滤电路图区域（避免重复）
        3. 文本分块（Recursive Character Splitter）
        4. 识别特殊类型（公式、定义、列表）
        5. 返回TextChunk列表
        """
        results = []

        pdf_document = fitz.open(pdf_path)

        for page_num in range(pdf_document.page_count):
            page = pdf_document[page_num]

            # 获取文本blocks
            text_blocks = page.get_text("blocks")

            # 获取该页的电路图bbox（避免重复提取）
            circuit_bboxes = await self._get_circuit_bboxes(page_num, document_id)

            # 过滤掉电路图区域的文本
            filtered_blocks = self._filter_circuit_areas(text_blocks, circuit_bboxes)

            # 文本分块
            chunks = self._create_chunks(
                filtered_blocks,
                page_num + 1,
                document_id,
                knowledge_base_id
            )

            results.extend(chunks)

        pdf_document.close()
        return results

    def _create_chunks(
        self,
        text_blocks: List[Dict],
        page_number: int,
        document_id: str,
        knowledge_base_id: str
    ) -> List[TextChunk]:
        """
        创建文本分块

        使用Recursive Character Splitter策略：
        - 按段落分隔符分割
        - 控制chunk大小（500字符）
        - 保留chunk重叠（50字符）
        """
        chunks = []

        # 合并同页文本
        full_text = "\n\n".join([block[4] for block in text_blocks])

        # 分块算法
        chunk_texts = self._recursive_split(full_text)

        for idx, chunk_text in enumerate(chunk_texts):
            chunk = TextChunk(
                id=str(uuid.uuid4()),
                document_id=document_id,
                knowledge_base_id=knowledge_base_id,
                page_number=page_number,
                chunk_text=chunk_text,
                chunk_type=self._detect_chunk_type(chunk_text),
                chunk_index=len(chunks) + idx
            )
            chunks.append(chunk)

        return chunks

    def _detect_chunk_type(self, text: str) -> str:
        """
        检测chunk类型

        - formula: 包含LaTeX公式或数学符号
        - definition: "定义:"、"Definition:"开头
        - list: 包含编号列表（1. 2. 3.）
        - paragraph: 默认段落
        """
        if "$$" in text or "\\frac" in text or "\\int" in text:
            return "formula"

        if text.startswith("定义:") or text.startswith("Definition:"):
            return "definition"

        if any(text.startswith(f"{i}.") for i in range(1, 10)):
            return "list"

        return "paragraph"
```

### 2. Transform 阶段扩展

**可选优化**: 使用LLM对chunk生成语义摘要

```python
class TextChunkTransformer:
    """文本块语义增强（可选）"""

    async def enhance_chunk(self, chunk: TextChunk) -> TextChunk:
        """
        为chunk生成语义摘要

        使用场景：
        - 长文本chunk（>300字符）
        - 技术性强的内容

        提示词示例：
        "请用1-2句话总结以下电路理论内容的核心要点：\n{chunk_text}"
        """
        if len(chunk.chunk_text) > 300:
            summary = await self.llm_summarize(chunk.chunk_text)
            chunk.semantic_summary = summary

        return chunk
```

### 3. Load 阶段扩展

**修改文件**: `app/services/etl/load.py`

```python
class DocumentLoadService:
    """扩展Load服务，支持文本chunks"""

    async def load_text_chunk(self, chunk: TextChunk) -> bool:
        """
        加载文本chunk到向量存储

        流程：
        1. 生成embedding（使用chunk_text或semantic_summary）
        2. 上传到Qdrant (collection: text_chunks)
        3. 更新MySQL的embedding_id
        """
        # 选择向量化文本
        text_to_embed = chunk.semantic_summary if chunk.semantic_summary else chunk.chunk_text

        # 生成向量
        embedding = await self.embedding_service.generate_embedding(text_to_embed)

        # 构造元数据
        metadata = {
            "chunk_id": chunk.id,
            "document_id": chunk.document_id,
            "knowledge_base_id": chunk.knowledge_base_id,
            "page_number": chunk.page_number,
            "chunk_type": chunk.chunk_type,
            "chunk_text": chunk.chunk_text,  # 保存原文
        }

        # 上传到Qdrant
        success = await qdrant_store.upsert_text_chunk(
            chunk_id=chunk.id,
            embedding=embedding,
            metadata=metadata
        )

        return success

    async def load_batch_text_chunks(self, chunks: List[TextChunk]) -> Dict[str, Any]:
        """批量加载文本chunks（并发处理）"""
        semaphore = asyncio.Semaphore(settings.MAX_CONCURRENT_TASKS)

        async def load_with_semaphore(chunk: TextChunk) -> bool:
            async with semaphore:
                return await self.load_text_chunk(chunk)

        tasks = [load_with_semaphore(chunk) for chunk in chunks]
        results = await asyncio.gather(*tasks, return_exceptions=True)

        success_count = sum(1 for r in results if r is True)
        failed_count = len(results) - success_count

        return {
            "total": len(chunks),
            "success": success_count,
            "failed": failed_count
        }
```

### 4. Qdrant Collection设计

**Collection 1**: `circuit_embeddings` (已存在)
- 存储电路图caption向量
- Payload: circuit_id, caption, bom, topology, image_url

**Collection 2**: `text_chunk_embeddings` (新增)
- 存储文本chunk向量
- Payload: chunk_id, document_id, chunk_text, chunk_type, page_number

**Collection 3**: `table_embeddings` (新增，可选)
- 存储表格caption/summary向量
- Payload: table_id, table_caption, table_data

```python
# 创建text_chunk_embeddings collection
await qdrant_client.create_collection(
    collection_name="text_chunk_embeddings",
    vectors_config=VectorParams(
        size=4096,  # Qwen3-Embedding-8B维度
        distance=Distance.COSINE
    )
)
```

### 5. 混合检索实现

**新增文件**: `app/services/rag/hybrid_search.py`

```python
"""混合检索服务"""

class HybridSearchService:
    """混合RAG检索"""

    async def hybrid_query(
        self,
        query: str,
        knowledge_base_ids: List[str],
        class_id: str,
        top_k: int = 5
    ) -> Dict[str, Any]:
        """
        混合检索：文本 + 电路图

        策略：
        1. 查询向量化
        2. 并行检索：
           - Qdrant (text_chunk_embeddings) → Top-10 text chunks
           - Qdrant (circuit_embeddings) → Top-10 circuits
        3. 结果融合：
           - RRF (Reciprocal Rank Fusion) 重排序
           - 或按相似度分数归一化合并
        4. 返回Top-K结果
        """
        # 1. 向量化查询
        query_embedding = await self.embedding_service.generate_embedding(query)

        # 2. 并行检索
        text_results, circuit_results = await asyncio.gather(
            self._search_text_chunks(query_embedding, knowledge_base_ids, top_k=10),
            self._search_circuits(query_embedding, knowledge_base_ids, top_k=10)
        )

        # 3. 结果融合（RRF）
        merged_results = self._reciprocal_rank_fusion(
            text_results,
            circuit_results
        )

        # 4. 返回Top-K
        return {
            "success": True,
            "query": query,
            "results": merged_results[:top_k],
            "total": len(merged_results)
        }

    def _reciprocal_rank_fusion(
        self,
        text_results: List[Dict],
        circuit_results: List[Dict],
        k: int = 60  # RRF constant
    ) -> List[Dict]:
        """
        RRF重排序算法

        公式: score = sum(1 / (k + rank))

        优点: 不依赖原始分数的尺度
        """
        scores = {}

        # Text chunks
        for rank, result in enumerate(text_results):
            item_id = result["chunk_id"]
            scores[item_id] = scores.get(item_id, 0) + 1 / (k + rank + 1)

        # Circuits
        for rank, result in enumerate(circuit_results):
            item_id = result["circuit_id"]
            scores[item_id] = scores.get(item_id, 0) + 1 / (k + rank + 1)

        # 排序
        sorted_items = sorted(scores.items(), key=lambda x: x[1], reverse=True)

        # 构造最终结果
        merged = []
        for item_id, score in sorted_items:
            # 查找原始数据
            item = self._find_item(item_id, text_results, circuit_results)
            if item:
                item["rrf_score"] = score
                merged.append(item)

        return merged
```

---

## 🚀 实施计划

### Sprint 1: 文本提取与分块 (Week 1-2)

**任务**:
- [x] 设计数据库schema
- [ ] 实现`TextChunkExtractor`
- [ ] 实现文本分块算法
- [ ] 测试chunk质量和大小分布
- [ ] MySQL迁移脚本

**验收标准**:
- ✅ 可从PDF提取文本并过滤电路图区域
- ✅ Chunk大小符合预期（400-600字符）
- ✅ Chunk类型检测准确（formula/definition/paragraph）

### Sprint 2: 向量化与存储 (Week 3-4)

**任务**:
- [ ] 创建Qdrant collection: `text_chunk_embeddings`
- [ ] 实现`load_text_chunk()`方法
- [ ] 实现并发批量加载
- [ ] 测试向量化性能

**验收标准**:
- ✅ 文本chunks成功向量化并存储到Qdrant
- ✅ 并发处理效率达标（5并发）
- ✅ MySQL embedding_id正确更新

### Sprint 3: 混合检索 (Week 5-6)

**任务**:
- [ ] 实现`HybridSearchService`
- [ ] 实现RRF重排序算法
- [ ] 测试检索相关性
- [ ] API端点开发

**验收标准**:
- ✅ 可同时检索文本和电路图
- ✅ RRF重排序合理
- ✅ 检索响应时间 < 2秒

### Sprint 4: 表格处理 (Week 7-8，可选)

**任务**:
- [ ] 实现表格提取（PyMuPDF tables）
- [ ] 表格向量化
- [ ] 集成到混合检索

---

## 📊 性能指标

### Extract阶段预期

| 文档 | 电路图数 | 文本Chunks | 表格数 | 预计耗时 |
|------|----------|------------|--------|----------|
| 10页课件 | 5 | 50 | 2 | 40秒 |
| 50页课件 | 20 | 200 | 10 | 3分钟 |
| 100页课件 | 40 | 400 | 20 | 6分钟 |

### Load阶段预期

| Chunks | 并发数 | 单个耗时 | 总耗时 | 提升 |
|--------|--------|----------|--------|------|
| 50 | 1 | 1秒 | 50秒 | - |
| 50 | 5 | 1秒 | 10秒 | 80%↓ |
| 200 | 5 | 1秒 | 40秒 | 80%↓ |

### 检索性能

| 检索类型 | 预期响应时间 | 结果质量 |
|----------|--------------|----------|
| 纯文本检索 | < 500ms | ⭐⭐⭐⭐⭐ |
| 纯电路图检索 | < 800ms | ⭐⭐⭐⭐ |
| 混合检索 | < 1500ms | ⭐⭐⭐⭐⭐ |

---

## 🔒 数据隔离与安全

### 多租户隔离

**Qdrant Payload Filter**:
```python
# 检索时添加过滤器
filter_conditions = {
    "must": [
        {"key": "knowledge_base_id", "match": {"value": knowledge_base_id}},
        {"key": "class_id", "match": {"value": class_id}}
    ]
}

results = await qdrant_client.search(
    collection_name="text_chunk_embeddings",
    query_vector=query_embedding,
    query_filter=filter_conditions,
    limit=top_k
)
```

---

## 🎯 成功标准

### 功能完整性

- [ ] 可提取文档中的文本内容（覆盖率 > 90%）
- [ ] 可提取表格内容（覆盖率 > 80%）
- [ ] 混合检索返回相关结果（MRR > 0.7）

### 性能指标

- [ ] 完整文档处理时间 < 10分钟（100页文档）
- [ ] 混合检索响应时间 < 2秒
- [ ] 并发效率 > 90%

### 用户体验

- [ ] 可回答理论问题（文本检索）
- [ ] 可回答电路问题（电路图检索）
- [ ] 可回答混合问题（混合检索）

---

**文档版本**: v1.0
**创建时间**: 2025-12-15
**最后更新**: 2025-12-15
**状态**: 🎯 **设计完成，待实施**

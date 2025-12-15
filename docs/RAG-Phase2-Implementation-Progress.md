# RAG Phase 2: 实施进度报告

**日期**: 2025-12-15
**版本**: Sprint 1 Progress
**状态**: 🚧 **开发中**

---

## 📊 Phase 2 目标回顾

实现**完整文档RAG**，支持：
- ✅ 电路图内容（Phase 1已完成）
- 🚧 文本内容（段落、公式、定义） ← **当前Sprint**
- ⏳ 表格内容（参数表、对比表）
- ⏳ 混合检索（文本 + 电路图）

---

## ✅ 已完成工作 (Sprint 1 - Day 1)

### 1. 架构设计 ✅

**文档**: [`RAG-Phase2-Full-Document-Design.md`](./RAG-Phase2-Full-Document-Design.md)

**内容**:
- 完整的系统架构图
- Extract-Transform-Load流程扩展
- 数据库schema设计
- Qdrant collection设计
- 混合检索RRF算法设计
- 性能指标预期

**技术亮点**:
```
PDF → Extract → (电路图 + 文本chunks + 表格)
             ↓
      Transform → (VLM解析 + 语义增强)
             ↓
          Load → (向量化 + Qdrant存储)
             ↓
   混合检索 → (RRF重排序 + Top-K)
```

### 2. 数据库扩展 ✅

**新增表**:

#### `document_text_chunk` - 文本分块表
```sql
CREATE TABLE `document_text_chunk` (
  `id` VARCHAR(64) PRIMARY KEY,
  `document_id` VARCHAR(64) NOT NULL,
  `knowledge_base_id` VARCHAR(64) NOT NULL,
  `page_number` INT NOT NULL,
  `chunk_text` TEXT NOT NULL,
  `chunk_type` VARCHAR(20) NOT NULL,  -- paragraph/formula/definition/list
  `chunk_index` INT NOT NULL,
  `bbox` JSON,
  `semantic_summary` TEXT,
  `embedding_id` VARCHAR(64),
  `embedding_model` VARCHAR(128),
  `extraction_status` VARCHAR(20) DEFAULT 'pending',
  ...
);
```

#### `document_table_content` - 表格内容表
```sql
CREATE TABLE `document_table_content` (
  `id` VARCHAR(64) PRIMARY KEY,
  `document_id` VARCHAR(64) NOT NULL,
  `table_data` JSON NOT NULL,
  `table_caption` TEXT,
  `table_index` INT NOT NULL,
  `embedding_id` VARCHAR(64),
  ...
);
```

#### 修改 `etl_task` 表
```sql
ALTER TABLE `etl_task`
ADD COLUMN `total_text_chunks` INT DEFAULT 0,
ADD COLUMN `processed_text_chunks` INT DEFAULT 0,
ADD COLUMN `total_tables` INT DEFAULT 0,
ADD COLUMN `processed_tables` INT DEFAULT 0;
```

**ORM模型**: ✅ 已添加到 `app/models/circuit.py`

**迁移脚本**: ✅ `migrations/phase2_migration.sql`

### 3. 文本提取服务 ✅

**文件**: `app/services/etl/text_extract.py`

**核心功能**:

#### `TextChunkExtractor` 类

**特性**:
- ✅ 从PDF提取文本并分块
- ✅ 过滤电路图区域（避免重复）
- ✅ Recursive Character Splitter算法
- ✅ 智能chunk类型检测

**关键方法**:

```python
class TextChunkExtractor:
    """文档文本分块提取器"""

    async def extract_text_from_pdf(
        self,
        pdf_path: str,
        document_id: str,
        knowledge_base_id: str,
        circuit_bboxes: Dict[int, List[Dict]] = None
    ) -> List[TextChunkData]:
        """
        从PDF提取文本内容并分块

        策略:
        1. 遍历每一页，提取文本blocks
        2. 过滤电路图区域
        3. 递归文本分块
        4. 类型检测（paragraph/formula/definition/list）
        """
```

**分块算法**:
```python
def _recursive_split(self, text: str) -> List[str]:
    """
    递归文本分割算法

    分隔符优先级: \n\n > \n > 。！？ > 字符
    目标大小: 500字符
    重叠: 50字符
    最小chunk: 50字符
    """
```

**类型检测**:
```python
def _detect_chunk_type(self, text: str) -> str:
    """
    - formula: 包含 $$, \frac, ∫∑∏√±
    - definition: "定义:"、"Definition:"开头
    - list: 编号列表（1. 2. 一、（1））
    - paragraph: 默认
    """
```

**过滤算法**:
```python
def _filter_circuit_areas(
    self,
    text_blocks: List[Tuple],
    circuit_bboxes: List[Dict]
) -> List[Tuple]:
    """
    过滤电路图区域的文本（IoU阈值=0.5）
    避免重复提取电路图附近的文字
    """
```

---

## 🚧 进行中工作

### Sprint 1: 文本提取与分块 (Week 1-2)

**进度**: 40% ✅✅⬜⬜⬜

| 任务 | 状态 | 完成度 |
|------|------|--------|
| 设计数据库schema | ✅ | 100% |
| 创建ORM模型 | ✅ | 100% |
| 实现TextChunkExtractor | ✅ | 100% |
| 集成到Extract服务 | 🚧 | 0% |
| 测试chunk质量 | ⏳ | 0% |
| MySQL迁移执行 | ⏳ | 0% |

---

## ⏳ 待完成工作

### 1. 集成到Extract服务

**修改文件**: `app/services/etl/extract.py`

**任务**:
- 在`PDFCircuitExtractor.extract_circuits_from_pdf()`中调用`text_chunk_extractor`
- 返回电路图列表 + 文本chunk列表
- 存储text chunks到MySQL

**伪代码**:
```python
# extract.py
async def extract_and_store(pdf_path, document_id, knowledge_base_id):
    # 1. 提取电路图（已有）
    circuit_results = await circuit_extractor.extract_circuits_from_pdf(...)

    # 2. 提取文本chunks（新增）
    circuit_bboxes = self._build_circuit_bbox_map(circuit_results)
    text_chunks = await text_chunk_extractor.extract_text_from_pdf(
        pdf_path=pdf_path,
        document_id=document_id,
        knowledge_base_id=knowledge_base_id,
        circuit_bboxes=circuit_bboxes  # 传入电路图位置，用于过滤
    )

    # 3. 存储电路图（已有）
    circuit_ids = await self._store_circuits(circuit_results)

    # 4. 存储文本chunks（新增）
    chunk_ids = await self._store_text_chunks(text_chunks)

    return {
        "circuit_ids": circuit_ids,
        "chunk_ids": chunk_ids,
        "total_circuits": len(circuit_ids),
        "total_text_chunks": len(chunk_ids)
    }
```

### 2. 扩展Load服务

**修改文件**: `app/services/etl/load.py`

**任务**:
- 实现`load_text_chunk()`方法
- 实现`load_batch_text_chunks()`方法（并发处理）
- 向量化text chunks并存储到Qdrant

**伪代码**:
```python
# load.py
class DocumentLoadService:
    async def load_text_chunk(self, chunk_id: str) -> bool:
        """加载单个文本chunk到向量存储"""
        # 1. 从MySQL查询chunk
        chunk = await text_chunk_repo.get_chunk(chunk_id)

        # 2. 向量化（使用chunk_text或semantic_summary）
        embedding = await self.embedding_service.generate_embedding(chunk.chunk_text)

        # 3. 上传到Qdrant (collection: text_chunk_embeddings)
        success = await qdrant_store.upsert_text_chunk(
            chunk_id=chunk_id,
            embedding=embedding,
            metadata={
                "chunk_id": chunk_id,
                "document_id": chunk.document_id,
                "chunk_text": chunk.chunk_text,
                "chunk_type": chunk.chunk_type,
                "page_number": chunk.page_number
            }
        )

        # 4. 更新MySQL的embedding_id
        await text_chunk_repo.update_embedding_id(chunk_id, chunk_id)

        return success

    async def load_batch_text_chunks(self, chunk_ids: List[str]) -> Dict[str, Any]:
        """批量加载（并发处理，复用Phase 1的asyncio.to_thread()模式）"""
        semaphore = asyncio.Semaphore(settings.MAX_CONCURRENT_TASKS)

        async def load_with_semaphore(chunk_id: str) -> bool:
            async with semaphore:
                return await self.load_text_chunk(chunk_id)

        tasks = [load_with_semaphore(cid) for cid in chunk_ids]
        results = await asyncio.gather(*tasks, return_exceptions=True)

        return {
            "total": len(chunk_ids),
            "success": sum(1 for r in results if r is True),
            "failed": sum(1 for r in results if r is not True)
        }
```

### 3. 创建Qdrant Collection

**文件**: `app/services/storage/qdrant_client.py`

**任务**:
- 创建`text_chunk_embeddings` collection
- 实现`upsert_text_chunk()`方法
- 实现`search_text_chunks()`方法

**伪代码**:
```python
# qdrant_client.py
async def create_text_chunk_collection(self):
    """创建文本chunk向量collection"""
    await self.client.create_collection(
        collection_name="text_chunk_embeddings",
        vectors_config=VectorParams(
            size=4096,  # Qwen3-Embedding-8B
            distance=Distance.COSINE
        )
    )

async def upsert_text_chunk(
    self,
    chunk_id: str,
    embedding: List[float],
    metadata: Dict[str, Any]
) -> bool:
    """上传文本chunk向量"""
    await self.client.upsert(
        collection_name="text_chunk_embeddings",
        points=[
            PointStruct(
                id=chunk_id,
                vector=embedding,
                payload=metadata
            )
        ]
    )
    return True

async def search_text_chunks(
    self,
    query_embedding: List[float],
    knowledge_base_ids: List[str],
    top_k: int = 10
) -> List[Dict]:
    """检索文本chunks"""
    results = await self.client.search(
        collection_name="text_chunk_embeddings",
        query_vector=query_embedding,
        query_filter=Filter(
            must=[
                FieldCondition(
                    key="knowledge_base_id",
                    match=MatchAny(any=knowledge_base_ids)
                )
            ]
        ),
        limit=top_k
    )
    return [{"chunk_id": r.id, "score": r.score, **r.payload} for r in results]
```

### 4. 混合检索服务

**文件**: `app/services/rag/hybrid_search.py`

**任务**:
- 实现`HybridSearchService`类
- 实现RRF (Reciprocal Rank Fusion)重排序
- 同时检索text chunks和circuits

**伪代码**:
```python
# hybrid_search.py
class HybridSearchService:
    async def hybrid_query(
        self,
        query: str,
        knowledge_base_ids: List[str],
        class_id: str,
        top_k: int = 5
    ) -> Dict[str, Any]:
        """混合检索"""
        # 1. 查询向量化
        query_embedding = await self.embedding_service.generate_embedding(query)

        # 2. 并行检索
        text_results, circuit_results = await asyncio.gather(
            qdrant_store.search_text_chunks(query_embedding, knowledge_base_ids, top_k=10),
            qdrant_store.search_circuits(query_embedding, knowledge_base_ids, top_k=10)
        )

        # 3. RRF重排序
        merged_results = self._reciprocal_rank_fusion(text_results, circuit_results)

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
        k: int = 60
    ) -> List[Dict]:
        """RRF重排序: score = sum(1 / (k + rank))"""
        scores = {}

        # Text chunks
        for rank, result in enumerate(text_results):
            item_id = ("text", result["chunk_id"])
            scores[item_id] = 1 / (k + rank + 1)

        # Circuits
        for rank, result in enumerate(circuit_results):
            item_id = ("circuit", result["circuit_id"])
            scores[item_id] = scores.get(item_id, 0) + 1 / (k + rank + 1)

        # 排序
        sorted_items = sorted(scores.items(), key=lambda x: x[1], reverse=True)

        # 构造最终结果
        merged = []
        for (item_type, item_id), score in sorted_items:
            if item_type == "text":
                item = next((r for r in text_results if r["chunk_id"] == item_id), None)
            else:
                item = next((r for r in circuit_results if r["circuit_id"] == item_id), None)

            if item:
                item["rrf_score"] = score
                item["item_type"] = item_type
                merged.append(item)

        return merged
```

---

## 📋 接下来的步骤

### 本周内完成 (Sprint 1)

1. **集成到Extract服务** (2天)
   - [ ] 修改`extract.py`调用`text_chunk_extractor`
   - [ ] 实现`_store_text_chunks()`方法
   - [ ] 测试文本提取效果

2. **扩展Load服务** (2天)
   - [ ] 实现`load_text_chunk()`
   - [ ] 实现`load_batch_text_chunks()`
   - [ ] 测试向量化性能

3. **Qdrant集成** (1天)
   - [ ] 创建`text_chunk_embeddings` collection
   - [ ] 实现相关方法

4. **端到端测试** (2天)
   - [ ] 上传测试文档
   - [ ] 验证text chunks提取质量
   - [ ] 检查chunk大小分布
   - [ ] 验证向量存储成功

---

## 📊 预期效果

### Sprint 1完成后

**功能**:
- ✅ 可从PDF提取文本内容
- ✅ 文本智能分块（500字符，50字符overlap）
- ✅ 类型检测（paragraph/formula/definition/list）
- ✅ 向量化并存储到Qdrant
- ✅ 过滤电路图区域（避免重复）

**性能**:
- 10页文档 → 提取50个chunks → 耗时30秒（Extract）
- 50个chunks → 向量化 → 耗时10秒（Load，5并发）
- **总计**: ~40秒

**数据示例**:
```json
{
  "chunk_id": "abc-123",
  "document_id": "doc-456",
  "page_number": 3,
  "chunk_text": "基尔霍夫电流定律（KCL）指出：在任意节点处，流入的电流总和等于流出的电流总和。数学表达式为：∑I_in = ∑I_out",
  "chunk_type": "definition",
  "chunk_index": 12,
  "embedding_id": "abc-123"
}
```

---

## 🎯 Phase 2 完整里程碑

### Sprint 1: 文本提取 (Week 1-2) ← **当前**
- [x] 架构设计
- [x] 数据库设计
- [x] 文本提取服务
- [ ] 集成测试

### Sprint 2: 向量化与存储 (Week 3-4)
- [ ] Qdrant collection创建
- [ ] Load服务扩展
- [ ] 性能测试

### Sprint 3: 混合检索 (Week 5-6)
- [ ] HybridSearchService实现
- [ ] RRF重排序
- [ ] API端点

### Sprint 4: 表格处理 (Week 7-8，可选)
- [ ] 表格提取
- [ ] 表格向量化
- [ ] 集成到混合检索

---

**文档版本**: Sprint 1 Progress Report
**创建时间**: 2025-12-15
**最后更新**: 2025-12-15
**当前状态**: 🚧 **Sprint 1进行中 (40%完成)**

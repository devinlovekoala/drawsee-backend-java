# Java侧RAG多模态机制总结

## 1. 写作目的

本文不再以工程功能清单为中心，而是从更偏学术的 RAG 机理视角，对当前 `drawsee-java` 中已经形成的知识增强链路进行归纳。重点关注以下问题：

- 文档如何被解析、切分、表征与索引
- 查询阶段如何进行预算控制、检索增强与上下文构造
- PDF 中图文混合内容，尤其是电路图，如何被转化为可检索、可推理的结构化证据
- 相比传统文本 RAG，我们的实现思路有哪些面向电路教学场景的创新点

## 2. 方法论总览

从机制上看，当前系统并不是“先检索几段文本，再让大模型回答”这样单层的 Vanilla RAG，而是一个面向电路教学场景的分层增强框架：

1. 文档表征层  
   将课件、实验任务书、知识文档解析成文本表征与图像表征。
2. 结构化恢复层  
   将 PDF 中的电路图进一步恢复为 `CircuitDesign`、netlist、元件连接等结构化对象。
3. 检索增强层  
   在查询时按 token 预算动态控制检索深度和上下文拼接量。
4. 生成编排层  
   将检索结果、结构化电路信息、历史上下文联合输入模型，形成面向分析任务的回答。

如果用更抽象的角度描述，当前方案更接近：

`Document RAG + Multimodal Evidence Extraction + Structured Circuit Reasoning`

而不是单纯的文本相似度问答系统。

## 3. 入库阶段：从原始文档到可检索语义单元

## 3.1 文档解析

Java 侧入库链路在 `DocumentIngestionService` 中定义。若启用 Java 模式，首先使用 `DocumentParser` 基于 Apache Tika 对文档进行统一解析：

- 输入：`InputStream + fileName + contentType`
- 输出：
  - `content`：抽取后的连续文本
  - `pageCount`：文档页数元数据

这一步的目标不是做复杂版面理解，而是获得一个跨 PDF / Office 文档统一的“基础文本流”，为后续 chunking 提供标准输入。

## 3.2 Chunk 切分机制

当前 Java 侧 chunk 切分由 `TextChunker` 完成。它采用的是一种轻量、可控、适合工程落地的 **字符窗口 + 重叠滑动 + 语义断点回退** 策略。

### 切分参数

- `chunkSize`：默认 `800`
- `chunkOverlap`：默认 `200`

配置来源：

- `src/main/java/cn/yifan/drawsee/service/business/parser/TextChunker.java`
- `src/main/resources/application.yaml`

### 核心过程

对于输入文本 `T`：

1. 设有效窗口大小 `effectiveChunkSize = max(chunkSize, 200)`
2. 设重叠区 `effectiveOverlap = min(chunkOverlap, effectiveChunkSize / 2)`
3. 以 `[start, start + effectiveChunkSize]` 为初始窗口
4. 若未到文末，则从窗口尾部向前寻找较优切分点：
   - 换行
   - 句号
   - 感叹号
   - 问号
5. 若找到断点，则将 chunk 截断到该位置
6. 下一窗口从 `end - overlap` 开始，形成相邻 chunk 间的语义重叠

### 机制意义

这类 chunking 并非最复杂的语义切分算法，但有三个重要特性：

- 保证相邻 chunk 之间存在局部语义连续性
- 避免在完全随机字符边界切断句子
- 复杂度低、可预测、适合入库批处理

从学术角度说，它属于典型的 **overlapping fixed-size chunking with weak semantic boundary correction**。

### 当前局限

它仍然是“文本流切分”，而不是：

- 基于章节标题的层次切分
- 基于版面区域的多粒度切分
- 基于图文对齐的结构化 chunking

因此它更适合作为当前 Java 侧的稳健基线，而不是最终形态。

## 3.3 向量化与索引写入

切分完成后，`DocumentIngestionService` 对每个 chunk 执行：

1. `EmbeddingService.generateEmbedding(chunkText)`
2. 生成向量 ID
3. 写入向量库 `Qdrant`
4. 同时写入 `KnowledgeDocumentChunk` 元数据表

每个 chunk 至少保留以下信息：

- `documentId`
- `knowledgeBaseId`
- `chunkIndex`
- `content`
- `vectorId`
- `vectorDimension`

因此系统实际采用的是一种 **向量索引 + 关系型元数据双存储** 设计：

- 向量库存语义邻近关系
- MySQL 存 chunk 原文与追踪元数据

这为后续“向量召回 + 原文还原 + 来源解释”提供了基础。

## 3.4 批处理写入机制

Java 入库时采用批量写入：

- 聚合若干 chunk 的 embedding
- 分批 `upsert` 到 Qdrant
- 分批写入 MySQL

这体现的不是算法创新，而是一种典型的 **pipeline throughput optimization**：

- 降低远程调用与写库开销
- 让入库复杂度更接近批处理模式
- 方便以任务进度形式反馈状态

## 4. 查询阶段：从用户问题到检索增强上下文

## 4.1 查询链路定位

当前 Java 侧查询链路由 `RagQueryService` 和 `RagEnhancementService` 共同负责。

关键点在于：

- Java 侧不强行耦合底层检索实现
- Java 负责检索预算规划、超时控制、结果压缩与 Prompt 注入
- Python 侧可承担更复杂的混合检索后端

也就是说，Java 侧更偏向 **retrieval orchestration layer**，而不是单纯的向量检索执行器。

## 4.2 检索输入的形成

在实际工作流中，检索输入并不总是“用户原始问题”，而是任务化重写后的查询。

例如在电路分析场景中，检索输入可能包含：

- 学生追问
- 电路标题 / 上下文标题
- SPICE 网表
- 元件摘要
- 分析上下文摘要

因此当前系统的查询构造，不是问答系统里常见的“query = natural question”，而是更接近：

`query = question + task context + structured circuit evidence`

这会改变检索语义中心，使召回更偏向电路工作原理、元件作用、拓扑相似案例，而不是泛化语句相似。

## 4.3 检索结果压缩与重表示

`RagQueryService` 接收 Python 返回的原始结果后，不会完整传递，而是进行二次重表示。

每条结果通常包含：

- `circuit_id`
- `score`
- `caption`
- `bom`
- `topology`
- `page_number`
- `image_url`
- `document_id`

但真正进入生成上下文的并不是全量字段，而是经过压缩后的紧凑文本行，例如：

- 图序号
- 页码
- 得分
- 裁剪后的 `caption`

从机制角度看，这是一种 **retrieval result abstraction**：

- 原始检索结果是富结构的
- 提供给生成模型的是精简后的证据摘要

这样做的直接收益是：

- 降低输入 token
- 保留最核心的检索判别信息
- 减少“把检索结果原样塞进 Prompt”带来的噪声扩散

## 5. Token预算控制：不是全拼接，而是受约束检索

这一部分是当前方案最值得强调的实现思路之一。

## 5.1 预算分配思想

`ContextBudgetManager` 明确将总上下文划分为多个预算区：

- 总最大上下文 `maxContextTokens`
- 安全输入比例 `safeInputRatio`
- 输出预留 `reservedOutputTokens`
- 历史对话预算 `historyRatio`
- 检索上下文预算 `retrievalRatio`

于是，给定问题 `q` 和历史 `H`，系统不会直接做“Top-K 检索 + 全部拼接”，而是先求：

- `tokens(q)`
- `tokens(H)`
- `safeInputBudget`
- `historyBudget`
- `retrievalBudget`

再进一步推导：

- `suggestedTopK`
- `chunkMaxTokens`
- `maxChunksInContext`
- 是否需要历史压缩

从学术角度，这属于一种 **budget-aware retrieval augmentation**。

## 5.2 历史压缩策略

若历史过长，系统会进行轻量压缩：

- 保留最近若干轮原始对话
- 将更早的对话压缩成一条摘要 `SystemMessage`

这不是长期记忆模型，但它的作用很明确：

- 尽量保留局部对话精度
- 将远距历史转化为低 token 的全局语义摘要
- 为检索上下文留出空间

从方法论上，这是一种 **recent-turn preservation + old-history summarization** 机制。

## 5.3 动态调整检索深度

当系统检测到上下文压力变大时，会收缩检索规模：

- `topK` 从默认值下降
- `chunkMaxTokens` 缩短
- `maxChunksInContext` 下降

这一设计说明系统把检索增强看作“收益随预算变化而动态伸缩”的模块，而不是固定流程。

它与很多论文中的“始终检索 K 篇证据”不同，更接近在线系统中的自适应策略。

## 6. 超时受控的RAG增强

`RagEnhancementService` 采用异步超时等待机制：

- 发起检索
- 仅在 `ragTimeoutMs` 时间内等待
- 若超时则直接放弃 RAG 增强

因此这里存在一个非常重要的设计思想：

**首 token 时延优先级高于检索完整性。**

如果用研究化语言表述，这是一种：

**latency-constrained retrieval augmentation**

其目标不是最大化每轮检索利用率，而是在时延约束下最大化增强收益。

## 7. 多模态PDF处理：从文本RAG扩展到图文证据RAG

## 7.1 多模态前处理动机

课件和实验任务文档存在大量以下内容：

- 电路原理图
- 芯片引脚图
- 波形图
- 表格参数
- 图文混排实验步骤

若仅对 PDF 做文本抽取，则会丢失最关键的电路结构信息。  
因此系统引入 `PdfMultimodalService`，将 PDF 视作图文混合证据源而非纯文本源。

## 7.2 页面选择不是全量扫描，而是复杂页优选

`PdfUtils.selectTopComplexPages()` 会先对页面图像计算复杂度分数，再选择最复杂的若干页做视觉分析。

其复杂度估计基于图像灰度方差，本质上是一种启发式页面筛选。

这一步可以理解为：

- 不是对所有页做同成本视觉推理
- 而是先做一次廉价的视觉先验筛选

从机制角度，它相当于一个非常轻量的 **page-level visual pre-retrieval**。

## 7.3 图像到语义描述

在选中复杂页后，系统调用视觉模型分析图像，并提炼如下信息：

- 电路图核心结构
- 引脚定义
- 波形特征
- 实验步骤要点
- 表格关键参数

这一步得到的是页面级语义摘要，可看作对 PDF 中非文本证据的一次语义显式化。

## 7.4 电路图到结构化设计对象

真正有特色的部分在于，系统并没有停留在“图像描述”。

对于疑似电路图页面，`PdfMultimodalService` 进一步执行：

1. 让视觉模型输出 netlist / 电路描述
2. 使用 `CircuitImageNetlistParser` 解析文本结果
3. 恢复出 `CircuitDesign`

于是，一个 PDF 页面中的电路图，不再只是：

- 一段说明文字

而会进一步转化为：

- 元件集合
- 连接关系
- netlist
- 前端可渲染的结构化电路设计对象

从学术角度，这一步相当于：

**将图像证据映射为结构化电路中间表示，再参与后续检索和推理。**

这比普通多模态 RAG 中“图像 caption 化后并入文本上下文”的策略更进一步。

## 8. 电路场景中的证据融合方式

当前系统的电路分析并不是“知识库文本主导”的，而是多源证据联合：

- 用户问题
- 对话历史
- 检索上下文
- 电路元件清单
- 连线关系
- SPICE 网表
- 分析上下文
- PDF 中恢复出的电路结构

因此当前系统的生成输入可以被视作一种 **heterogeneous evidence bundle**。

其优势在于：

- 检索结果提供外部知识
- 电路结构提供内部证据
- 两者互补

这和传统文本 RAG 的关键区别在于，生成不完全依赖召回文本，而是建立在“显式结构 + 检索知识”的混合证据上。

## 9. 当前实现的主要创新点

如果从论文对比视角总结，当前方案的创新点主要不在底层向量算法，而在 RAG 机制组织方式。

## 9.1 面向电路场景的双证据RAG

传统 RAG：

- query -> text retrieval -> generation

当前方案：

- query -> text retrieval
- query -> circuit structure extraction / direct circuit evidence
- evidence fusion -> generation

即从“单证据文本增强”走向“文本证据 + 电路结构证据”的双证据增强。

## 9.2 多模态内容不只做 caption，而是做结构恢复

多数多模态 RAG 把图像处理为描述文本后就结束。  
我们的方案进一步把电路图恢复成 `CircuitDesign` 与 netlist，这使图像内容能够进入可计算、可解释、可追问的结构层。

## 9.3 检索是预算感知的，而不是固定深度的

当前系统显式建模了：

- 历史预算
- 检索预算
- 输出预留
- chunk 长度上限

因此它不是固定检索深度，而是自适应检索深度。

## 9.4 检索增强服从时延约束

系统明确允许：

- 检索超时
- 增强跳过
- 主回答继续

这与“检索必须完成”的离线式论文假设不同，更贴近真实教学交互场景。

## 9.5 Chunk 机制与多模态机制并存

文本部分采用 chunk-based embedding。  
图像部分采用 page selection + vision parsing + circuit structuring。

因此这不是单一模态管线，而是：

- 文本证据走 chunk pipeline
- 图像证据走 structure recovery pipeline

最后在生成阶段汇合。

## 10. 当前局限

为了后续与论文对比更客观，这里也应明确当前方案的局限。

## 10.1 Chunk切分仍偏浅层

当前 Java 侧 chunking 主要基于：

- 固定长度
- 重叠滑窗
- 断句回退

尚未做到：

- 基于章节层级的语义切分
- 基于版面检测的区域切分
- 基于图文配对的跨模态 chunk 对齐

## 10.2 结构化恢复依赖视觉模型输出质量

电路图恢复的关键步骤仍然是：

- vision model 生成 netlist / 结构文本
- parser 再做结构化解析

因此其稳定性仍受模型输出格式一致性影响。

## 10.3 Java侧检索后端仍以编排为主

当前 Java 侧的强项在于：

- 预算控制
- 超时控制
- 上下文构造
- 多模态证据组织

而不是复杂检索算法本身。  
复杂混合检索、RRF 融合等更深层能力，目前主要依赖 Python 侧。

## 11. 后续可形成的论文式表述方向

如果后续需要对外写成论文式总结，可以把当前方案表述为：

### 方向A：面向电路教学的多模态结构增强RAG

关键词：

- multimodal RAG
- circuit diagram understanding
- structured intermediate representation
- evidence fusion

### 方向B：面向交互时延约束的预算感知RAG

关键词：

- budget-aware retrieval
- latency-constrained augmentation
- adaptive context construction
- graceful degradation

### 方向C：图文混合文档中的电路知识恢复

关键词：

- PDF multimodal parsing
- page-level visual preselection
- circuit netlist reconstruction
- retrieval-ready structured evidence

## 12. 一句话结论

当前 `drawsee-java` 的核心价值，并不只是“把知识库接进了问答流程”，而是构建了一条更偏研究意义的机制链路：

**用 chunk 化文本表征处理文档语义，用视觉解析与 netlist 恢复处理电路图证据，再通过预算感知、时延受控的检索增强机制，将多源证据联合送入生成模型。**

这使它天然适合和后续的多模态 RAG、结构化 RAG、教学场景 RAG 论文做方法层对比。

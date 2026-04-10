## 背景与目标

- **背景**: 当前电路实验相关功能支持用户上传 PDF 文档，但实际“PDF分析”链路采用纯文本对话，未对 PDF 做文本抽取或视觉解析，导致无法有效理解电路图、波形图等关键图像信息。
- **总体目标**: 在不影响现有业务与开发效率的前提下，优先完善“电路实验文档（PDF 为主，后续可扩展 PPTX）”的图文混合分析能力，既能理解图像（电路图/波形/表格），又能结合文档文本内容做结构化分析与总结。

## 范围
- 功能范围：
  - 支持上传 PDF（已有）与可选 PPT/PPTX（仅存储或转换）。
  - 新增“PDF 视觉+文本混合分析（Hybrid）”能力，形成结构化结果与可视化节点。
  - 任务异步处理（RabbitMQ/Redisson 流保持不变），面向前端以 AI 任务方式呈现流式结果。
- 非功能范围（本阶段约束）：
  - 安全加固：为保证开发便捷性，暂不纳入本阶段（不做密钥外置、CORS/Actuator 收敛等变更），仅记录为后续事项。
  - 日志与依赖：允许进行“**不影响现有项目行为**”的日志配置微调与依赖升级/安全扫描（只读/告警级），一旦发现兼容性风险立即回滚。
  - 可观测性/工程化：在不改动接口契约前提下，增量加入必要埋点与测试，不阻塞 M2/M3 主线。

## 现状评估（关键点）
- 上传：`UserDocumentService` 白名单包含 PDF/Word/图片/TXT，未包含 PPT/PPTX（可选后续扩展）。
- Minio：`MinioService` 提供上传、下载与预签名 URL，`getContentType` 尚未覆盖 PPT/PPTX（可选增强）。
- PDF 分析：`PdfCircuitAnalysisDetailWorkFlow` 仅把 Minio URL 追加到对话历史并调用 `StreamAiService.generalChat` 的文本模型，模型不会主动抓取 URL 解析 PDF。
- Prompt：存在 `document-analysis.txt` 但未结合 PDF 文本抽取/检索使用。
- 安全：仓库中存在明文配置与过度暴露风险，**本阶段暂缓整改**，记录为后续事项。

## 需求
- 功能性需求：
  - F1: 能对 PDF 电路实验文档进行“图文混合”分析，识别电路图结构、器件参数、连接关系、测量点、波形与结论。
  - F2: 支持大文档分页处理、分批流式输出，保证可用与稳定。
  - F3: 保留现有 AI 任务编排（`AiTaskType.PDF_CIRCUIT_ANALYSIS_DETAIL`），结果以节点/流消息形式回传。
  - F4: 可选支持 PPT/PPTX 上传（仅存储）与后续“自动转 PDF 再分析”。
- 非功能性需求（约束化执行）：
  - N1: 稳定（限流、重试、幂等等策略以最小改动为原则）。
  - N2: 性能（分页/抽样、任务并发与资源控制）。
  - N3: 可观测（必要指标与日志，不破坏现有日志结构）。

## 技术方案概述（Hybrid：视觉优先 + 文本增强）
- PDF→图片（视觉侧）：
  - 使用 PDFBox `PDFRenderer` 将 PDF 渲染为图片（200–300 DPI，PNG/JPEG），按页采样（优先包含电路图/波形页，可基于图像复杂度/文件大小阈值简单判定）。
  - 图片上传 Minio，获得可访问 URL 或以 Base64 传入（若模型无法访问内网 URL）。
  - 在 `StreamAiService` 接入 vision 模型（如 `doubaoVision`），新增 `visionChat`/`visionGeneralChat`，支持多张 `ImageContent` 分批传入，多轮对话逐步提取结构化信息。
- 文本抽取（文本侧）：
  - 使用 PDFBox 抽取文本（可分页保留页码），过滤噪声（页眉/页脚/水印），限长切分。
  - 结合 `PromptService.buildDocumentAnalysisPrompt(text, maxDepth)` 作为上下文补充或后处理总结，输出“分析要点/器件清单/测量点/结论/引用页码”。
- 结果组织：
  - 沿用 `WorkFlow` 节点与 Redis Stream，分阶段发送进度、部分结果与最终总结。
  - 结果数据结构建议包含：`pagesAnalyzed`、`components[]`、`connections[]`、`measurePoints[]`、`waveforms[]`、`conclusions[]`、`citations[]`（页码/图片编号）。

## 详细改造点

### 1. 模型接入与服务层
- `StreamAiService`
  - 新增：`StreamingChatLanguageModel doubaoVisionStreamingChatLanguageModel`（通过 Spring 配置注入，复用 `LangchainConfig`）。
  - 新增方法：
    - `visionChat(List<ChatMessage> history, List<String> imageUrls, String instruction, String model, StreamingResponseHandler<AiMessage> handler)`
    - 批量传图（每轮 3–5 张），控制超时与重试；必要时支持 Base64。

### 2. 工作流改造
- `PdfCircuitAnalysisDetailWorkFlow`
  - 在 `createUserMessageWithDocument` 中：
    - 若 `documentType == pdf`：
      1) 渲染若干关键页为图片，上传 Minio，得到 `imageUrls`。
      2) 组装 `UserMessage` 包含多张 `ImageContent` 与说明性文本。
      3) 调用 `StreamAiService.visionChat` 分批生成结构化要点。
    - 并行/后续使用文本抽取结果进行补全与一致性检查（可第二轮 `generalChat` 归纳）。
  - 分阶段输出：分页处理 → 中间要点 → 汇总 → DONE。

### 3. PDF 渲染与文本抽取
- 新增工具类（例如 `PdfUtils`）：
  - `List<BufferedImage> renderPages(InputStream pdf, int dpi, Predicate<PageMeta> filter)`
  - `String extractText(InputStream pdf)`（或分页返回 `PageText{pageNo, text}`）。
- 在上传后从 Minio 读取流进行渲染/抽取；或在上传时生成并缓存图片（权衡时延与存储）。

### 4. Minio 与 URL 访问
- `MinioService`
  - `getObjectUrl` 的过期时间与响应头已设置；若模型无法访问 Minio URL，提供 Base64 兜底：`getObjectBase64(objectName)`（注意大小与速率）。
  - `getContentType` 可增补：`.ppt` → `application/vnd.ms-powerpoint`，`.pptx` → `application/vnd.openxmlformats-officedocument.presentationml.presentation`。

### 5. 配置与安全（本阶段策略）
- **不做安全加固变更**（密钥外置、CORS/Actuator 收敛等暂缓）。
- 日志配置与依赖升级/扫描仅在“**不影响现有功能**”前提下进行；如存在兼容风险立即回退。
- Vision 模型配置（`drawsee.models.doubaoVision`）采用环境变量注入 `apiKey/baseUrl/modelName`（仅当不影响现网配置时落地）。

### 6. 接口与任务编排
- 触发方式沿用现有 AI 任务：`AiTaskType.PDF_CIRCUIT_ANALYSIS_DETAIL`，`prompt` 填 `documentUuid`。
- 可新增显式接口：`POST /ai-task/pdf/analysis?uuid=...`（服务内转换为上述任务），非必需。

### 7. 数据模型（可选增强）
- 若需持久化页级图片与提取文本：
  - 新表 `user_document_page`：`id, document_id, page_no, image_object, image_url, text, created_at`。
  - 或在 `user_document` 增加 `meta_json` 存储页清单与摘要，减少表数量。

### 8. 可靠性与性能（最小改动原则）
- RabbitMQ：沿用现配置；必要时仅加手动 ack 与 `prefetch` 调整，不改基础拓扑；失败进入简单重试或记录错误节点。
- Redisson：对任务做轻量幂等（`taskId` 锁），分页限流；RStream 数据清理复用 `CleanScheduleTasks`。
- 超时与重试：对模型调用与 Minio 访问设置合理超时，重试次数受限，避免连锁影响。

### 9. 可观测性（不破坏现有日志）
- 日志：沿用现有 JSON/文本输出；新增必要打点（页数、DPI、图像批次、模型耗时、token 预估）但不改变日志结构。
- 指标：Micrometer 选做，若引入仅暴露内部端点，不对外开放。

## 测试计划
- 单元测试：
  - PDF 渲染/抽取工具的页数、DPI、异常；`MinioService` 的 MIME 判定与 URL 生成。
  - `StreamAiService.visionChat` 参数组装与分批逻辑（可 Mock）。
- 集成测试（Testcontainers）：
  - Minio、Redis、RabbitMQ 环境；上传→创建任务→消费→流式结果校验。
- 端到端：
  - 小/中/大 PDF（含电路图/波形）场景；网络不可达时 Base64 兜底；并发 10/50/100 简单压测。

## 里程碑与排期（聚焦 M2/M3）
- M1（第1周，准备期）：
  - 基线不变：不做安全加固；仅在不影响项目的前提下进行日志配置微调与依赖升级/扫描（只读/告警）。
  - 产出：风险清单、回滚预案、最小可运行链路自测清单。
- M2（第2周，能力搭建：PDF→图与文抽取 + Vision 接入）
  - 工作内容：
    - 引入 `PdfUtils`：页渲染（200–300 DPI）、文本抽取（分页+清洗），提供限长切分策略。
    - `MinioService` 增强：图片上传/URL 获取，必要时 Base64 兜底接口。
    - `StreamAiService`：新增 `doubaoVision` Bean 与 `visionChat`，支持多图分批流式生成。
    - 采样策略：优先分析含电路图/波形的页面（基于文件大小、梯度/边缘密度的简单启发式）。
  - 可行性评估：
    - 依赖可用：PDFBox/Minio 已集成；`langchain4j` 接 vision 模型可按现有配置扩展；网络不通时可 Base64。
    - 性能预估：
      - 页渲染：~50–150ms/页（200 DPI，视页复杂度与机器配置）；
      - I/O：图片上传 ≤ 200ms/张（内网 Minio，取决于带宽）；
      - 模型调用：多模态 1 批（3–5 张）~2–6s；
      - 单文档 20 页抽样 6–10 页，分 2 批处理，首屏 ≤ 60s 可达。
    - 风险与缓解：
      - 模型无法拉取 URL → 转 Base64；
      - 文档过大 → 降 DPI + 减少批次 + 早停。
  - 价值：
    - 相比纯文本分析，能识别电路图/波形/标注等关键信息，准确率与可读性显著提升。
- M3（第3周，工作流融合与结构化产出）
  - 工作内容：
    - 改造 `PdfCircuitAnalysisDetailWorkFlow`：按页/批组织视觉分析，插入中间节点（进度/要点），最终合并总结。
    - 结构化结果：`components[]/connections[]/measurePoints[]/waveforms[]/conclusions[]/citations[]`；支持页码与图片编号引用。
    - 文本增强：在视觉结果基础上，引入文本抽取进行一致性校验与补全（第二轮 `generalChat` 归纳）。
  - 可行性评估：
    - 复用现有节点/Redis Stream 基础，无需改接口契约；
    - 历史上下文与多轮对话由现有 `WorkFlow + StreamAiService` 承载，改造成本可控；
    - 数据量控制：每轮 3–5 张图 + 摘要文本，保障时延与内存占用。
  - 价值：
    - 前端可渐进显示进度与中期要点，提升交互体验；
    - 结构化产出更易复用（知识库、继续问答、教学场景）。
- M4（第4周+，后续）
  - 稳定性与可观测性增强、（可选）安全加固、灰度与压测。

## 验收标准（面向文档分析）
- A1: 10 份涵盖多电路图与波形的 PDF，平均首屏时间 ≤ 60 秒，完整结果 ≤ 5 分钟。
- A2: 输出包含结构化字段（器件、连接、测量点、波形、结论、引用）且引用页码/图片编号正确率 ≥ 80%。
- A3: 失败率 ≤ 2%（可重试后 ≤ 0.5%），超时/网络问题不导致系统雪崩（有兜底结果）。
- A4: 不修改现有对外接口契约与关键运行参数；日志与依赖变更对业务“无感”。

## 风险与缓解（本阶段）
- R1: 模型无法访问 Minio URL → 启用 Base64 兜底；或提供可访问域名。
- R2: 大 PDF/大量图片导致时延/费用上升 → 采样与页筛选、降低 DPI、分批多轮+早停。
- R3: 视觉模型对电路专业图理解不足 → Prompt 工程补强、加入专家规则模板、允许用户补充“实验上下文”。
- R4: 并发导致资源争用 → 控制批次并发与任务限流，必要时降低图像分辨率。

## 交付物
- 代码：`StreamAiService`（vision 接口）、`PdfCircuitAnalysisDetailWorkFlow`（改造）、`PdfUtils`、`MinioService`（增强）。
- 配置：doubaoVision 模型配置（仅在不影响现网的前提下接入）。
- 文档：本文档更新、接口说明（OpenAPI 可选）、操作说明（如何创建任务与查看结果）。

## 后续扩展（PPT 支持）
- 上传：白名单加入 `application/vnd.ms-powerpoint`、`application/vnd.openxmlformats-officedocument.presentationml.presentation`（仅存储）。
- 分析：
  1) 使用 POI 将 PPTX 渲染为图片，走 Vision；或
  2) 转 PDF 再复用 PDF 流程；或
  3) 前端预转换后上传 PDF。

## 任务清单（Checklist）
- [ ] M2：`PdfUtils` 渲染/抽取、`MinioService` 图片上传与 Base64 兜底、`StreamAiService.visionChat` 多图分批。
- [ ] M3：改造 `PdfCircuitAnalysisDetailWorkFlow`，分批视觉分析→中期要点→文本增强→最终总结，输出结构化结果。
- [ ] 集成测试：Minio/Redis/RabbitMQ 路径打通；端到端校验不同类型 PDF（含大量电路图/波形）。
- [ ] 日志与依赖：仅做不影响现有功能的微调与升级/扫描，发现风险立即回滚。 
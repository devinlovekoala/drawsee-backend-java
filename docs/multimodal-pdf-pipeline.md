# M2 改造总结（PDF→图与文抽取 + Vision 接入）

## 范围与目标
- **范围**：在不改接口契约前提下，为“PDF 电路实验文档分析”引入多模态视觉能力与文本抽取，分批流式输出中间要点，并进行一致性校验与二轮总结。
- **目标**：
  - 视觉侧：将采样页渲染成图片，调用多模态模型抽取“电路/波形/表格/标注”等要点。
  - 文本侧：分页提取 PDF 文本片段（带页码），用于补全上下文与引用页码。
  - 结果：分阶段回报进度与中间要点，最终流式给出结构化总结。

## 流程总结

- 当前流程是：前端以 documentUuid 触发 PDF_CIRCUIT_ANALYSIS_DETAIL 任务后，后端工作流从 Minio 拉取 PDF，先用 PdfUtils 计算页“视觉复杂度”并选取 Top10，再取最多 8 页渲染成图片上传 Minio；流程通过 Redis Stream 推送进度（vision START、BATCH_START），对每批最多 4 张图调用多模态模型（doubaoVision）生成“中期要点”并回报（BATCH_DONE）；随后对整份 PDF 分页抽取文本并限长拼接（带页码），将“视觉要点汇总 + 文本片段”合并为一条用户消息加入历史，最后用通用文本模型执行“一致性校验与二轮总结”，以流式 TEXT 输出最终答案并发送 DONE，节点与任务状态随之更新（非 PDF 则走原有文本分析兜底）。


## 关键改动（代码）
- `cn.yifan.drawsee.config.LangchainConfig`
  - 新增 `@Bean("doubaoVisionStreamingChatLanguageModel")`，对接多模态（vision）模型。
- `cn.yifan.drawsee.constant.AiModel`
  - 新增常量 `DOUBAOVISION = "doubaoVision"`。
- `cn.yifan.drawsee.service.base.StreamAiService`
  - 新增依赖注入 `doubaoVisionStreamingChatLanguageModel`。
  - 新增方法 `visionChat(List<ChatMessage> history, List<String> imageUrls, String instruction, String model, StreamingResponseHandler<AiMessage> handler)`：
    - 将最多 8 张图片聚合为单次生成调用，避免多次 `onComplete` 并对齐一次性流式输出的预期。
- `cn.yifan.drawsee.service.base.MinioService`
  - `getContentType` 增补 `.ppt/.pptx`。
  - 新增：`uploadImage(BufferedImage image, String objectName)`、`getObjectBase64(String objectName, int maxBytes)`、`getObjectStream(String objectName)`。
- `cn.yifan.drawsee.util.PdfUtils`
  - 新增工具：
    - `renderPages(InputStream pdf, int dpi, IntPredicate pageFilter)`、`renderFirstNPages(...)`。
    - `extractPageTexts(InputStream pdf)`、`extractAllText(...)`。
    - `selectTopComplexPages(InputStream pdf, int dpi, int topK)`：以灰度方差近似“视觉复杂度”。
    - `splitByMaxLength(String text, int maxLen)`。
- `cn.yifan.drawsee.constant.MinioObjectPath`
  - 新增 `DOCUMENT_PAGE_PATH = "document/page/"`。
- `cn.yifan.drawsee.service.base.PromptService`
  - 新增 `getDocumentAnalysisVisionPrompt()`；
  - 新增模板 `src/main/resources/prompt/document-analysis-vision.txt`。
- `cn.yifan.drawsee.worker.PdfCircuitAnalysisDetailWorkFlow`
  - `streamChat(...)` 主要变更：
    1) 从 Minio 下载 PDF → 复杂度采样 Top10 页（`PdfUtils.selectTopComplexPages`，DPI=200）→ 取前 8 页进入视觉分析；
    2) 渲染选中页（DPI=220）→ 上传 Minio `document/page/{docId}/p{page}.png` → 收集 URL；
    3) 通过 Redis Stream 发送阶段进度与批次事件（见下）；
    4) 每批（4 张图）调用 `StreamAiService.visionChat` 产出“要点文本”，并以 DATA 事件回报中间要点；
    5) 分页抽取 PDF 文本（带页码，限长约 4000 字）作为辅助上下文；
    6) 将“视觉要点汇总 + 文本抽取片段”合并为用户消息追加到历史，调用 `generalChat` 执行一致性校验与最终总结（流式输出至主节点）。

## 事件与数据流（Redis Stream）
- 所有进度/中间要点通过 `type=DATA` 发送，最终总结通过 `type=TEXT`（主流）输出。
- 事件示例：
  - 视觉阶段开始：
    - `{ stage: "vision", status: "START", pages: [采样页索引...] }`
  - 批次开始：
    - `{ stage: "vision", status: "BATCH_START", batchNo: k, totalBatches: n }`
  - 批次完成（中期要点）：
    - `{ stage: "vision", status: "BATCH_DONE", batchNo: k, keyPoints: "..." }`
  - 批次失败：
    - `{ stage: "vision", status: "BATCH_ERROR", batchNo: k, message: "..." }`
  - 进入总结阶段：
    - `{ stage: "summary", status: "START" }`
- 最终总结：沿用 `WorkFlow` 的 `TEXT` 流式输出、`DONE` 结束信号与节点落库流程。

## 可调参数（当前默认）
- 复杂度采样 TopK：`10`
- 视觉送模页数上限：`8`
- 复杂度计算 DPI：`200`
- 渲染 DPI：`220`
- 视觉批大小：`4`
- 文本抽取拼接总长：约 `4000` 字
- Minio 图片路径：`document/page/{docId}/p{page}.png`

## 配置与使用
- 模型配置（`application.yaml` 已存在）：
  - `drawsee.models.doubaoVision.baseUrl/apiKey/modelName`
- 触发方式：沿用 AI 任务 `AiTaskType.PDF_CIRCUIT_ANALYSIS_DETAIL`，`prompt` 传 `documentUuid`；`model` 设为 `doubaoVision` 以启用视觉分析。
- 对外接口契约：未改动。

## 运行与验证
- 构建：`mvn -DskipTests package`
- 运行：保持原启动姿势；视觉分析在任务消费侧自动触发。
- 观测：
  - 进度/要点：订阅 Redis Stream，消费 `type=DATA` 事件。
  - 最终总结：订阅 `type=TEXT` 流式文本与 `DONE` 事件。

## 作用对齐
- **复杂度采样关键页**：优先处理信息密集页，提高首屏速度与有效信息覆盖。
- **分页流式进度 + 中间要点**：缩短“无反馈”时间，便于前端进度展示与“阶段性信息”渲染。
- **文本抽取 + 二轮总结**：视觉要点与文本片段同上下文交叉校验，减少偏差，产出结构化且带引用的结论。

## 注意事项与风险
- 模型无法访问内网 URL 时，可切换 `MinioService.getObjectBase64`（当前未默认启用）。
- 大文档：可降低 TopK、DPI 或批大小，或早停策略以控时延/成本。
- 复杂度评分使用灰度方差，属于启发式；如对电路图识别仍不充分，可叠加边缘密度/图像熵等指标。

## 后续可选增强（面向 M3/M4）
- 将“中间要点”生成子节点，支持点击跳转引用页码/图片编号。
- 二轮总结输出严格结构化 JSON（器件/连接/测量点/波形/结论/引用）。
- Base64 兜底策略的自动切换与速率治理。
- Micrometer 指标与更细粒度的耗时/Token 观测。 

## M3 融合与进度更新（与 M2 高度贴合）

- **工作流落地**: 已在 `PdfCircuitAnalysisDetailWorkFlow.streamChat` 按批（4 张/批）完成视觉要点抽取→文本抽取→一致性校验与二轮总结的完整闭环。
  - 视觉阶段：Top10 复杂度采样→最多 8 页渲染→上传 Minio→分批送入 `doubaoVision`。
  - 总结阶段：将“视觉要点汇总 + 文本抽取片段（带页码）”合并到历史，调用通用文本模型进行一致性校验与最终总结，以主流 `TEXT` 流式输出，结束发送 `DONE`。
- **视觉批处理改造（相对 M2 草案的差异）**：
  - 同步等待：每批使用 `CountDownLatch` 等待 `onComplete` 后再进入下一批与最终总结，确保结果完整性。
  - 超时与容错：为每批加入 120s 超时；异常/超时触发 `BATCH_ERROR` 事件，避免无反馈阻塞。
  - 进度一致性：统一计算 `totalBatches`，严格按序发送 `BATCH_START → BATCH_DONE/BATCH_ERROR`，前端进度展示更稳定。
  - 结果完整性：保证收集完全部 `batchSummaries` 后，再与文本片段合并进入总结，避免“未收齐就汇总”。
  - 事件流不变：中期要点仍走 `type=DATA`，最终总结仍走 `type=TEXT` 主流。
- **可调参数（保持与 M2 默认一致）**：
  - 复杂度采样 TopK: `10`；视觉页上限: `8`；复杂度 DPI: `200`；渲染 DPI: `220`；批大小: `4`；文本抽取拼接总长: 约 `4000`；图片路径：`document/page/{docId}/p{page}.png`。
- **兼容性**：对外接口契约未改动；事件字段与含义与 M2 文档一致，新增的是批内同步与超时容错，实现层面增强，不影响前端协议。
- **触发与观测**：
  - 触发：沿用 `AiTaskType.PDF_CIRCUIT_ANALYSIS_DETAIL`，`prompt=documentUuid`，`model=doubaoVision` 启用视觉分析。
  - 观测：`type=DATA` 接收 `vision START/BATCH_*` 与 `summary START`；主流 `type=TEXT` 接收最终总结，末尾 `DONE`。 
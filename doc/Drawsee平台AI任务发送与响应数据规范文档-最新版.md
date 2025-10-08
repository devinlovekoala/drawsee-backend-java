# Drawsee 平台 AI 任务发送与响应数据规范（最新版）

本文档描述当前后端实现已经适配的请求与响应数据结构、SSE 事件类型、各任务类型的节点产出形态。请以前后端联调为准，字段名、大小写与类型值需和代码常量严格一致。

参考代码：
- 任务创建接口：`FlowController.createTask`（POST `/flow/tasks`，DTO：`CreateAiTaskDTO`）
- SSE 订阅：`FlowController.getCompletion`（GET `/flow/completion?taskId=...`）
- 事件类型常量：`AiTaskMessageType`（node/text/title/data/done/error）
- 节点类型常量：`NodeType`
- 节点载体：`NodeVO`

更新时间：2025-09-28

---

## 一、请求规范（CreateAiTaskDTO）

后端从登录态读取 userId，前端提交以下 JSON：

```json
{
  "type": "TASK_TYPE",
  "prompt": "用户输入内容或对象（字符串）",
  "promptParams": { "k": "v" },
  "model": "可选模型名",
  "convId": 5001,
  "parentId": 1000,
  "classId": "可选-班级ID，用于知识库选择"
}
```

说明：
- 必填字段：type、prompt。
- convId 为空时，后端会新建会话与 ROOT 节点，并回填到返回值的 `conversation` 中。
- parentId 为会话内已有节点 id。不同任务类型对父节点有类型要求（见各类型说明）。
- type 取值必须来自 `AiTaskType` 常量。

任务创建成功返回：

```json
{
  "taskId": 12345,
  "conversation": {
    "id": 5001,
    "title": "新会话",
    "createdAt": "2025-09-28T10:20:30.000+08:00",
    "updatedAt": "2025-09-28T10:20:30.000+08:00"
  }
}
```

---

## 二、SSE 事件规范（/flow/completion?taskId=...）

后端通过 Redis Stream 推送事件，SSE 的单条消息结构统一为：

```json
{
  "type": "node | text | data | title | done | error",
  "data": any
}
```

- type 取值：见 `AiTaskMessageType`
  - node：推送新建节点（载荷为 NodeVO）
  - text：流式 token（载荷 `{ nodeId, content }`）
  - data：进度/阶段性数据（结构因任务不同而异）
  - title：会话标题更新（载荷为字符串）
  - done：任务结束（载荷为空字符串）
  - error：错误信息（载荷为字符串或对象）

---

## 三、核心节点结构（NodeVO）

```json
{
  "id": 1011,
  "type": "query | answer | answer-point | answer-detail | knowledge-head | knowledge-detail | resource | circuit-canvas | circuit-point | pdf-circuit-point | pdf-circuit-detail",
  "data": { "title": "...", "text": "...", "...任务特定字段" },
  "position": { "x": 0, "y": 0 },
  "height": null,
  "parentId": 1000,
  "convId": 5001,
  "userId": 3002,
  "createdAt": "2025-09-28T10:20:30.000+08:00",
  "updatedAt": "2025-09-28T10:20:30.000+08:00"
}
```

---

## 四、任务类型与数据形态

下列为常用任务的请求/响应与节点产出示例。示例中 id 与时间仅为演示。

### 1) 通用对话（GENERAL）

请求：
```json
{ "type": "GENERAL", "prompt": "请介绍一下Java的基本语法", "model": "deepseekV3", "convId": 5001, "parentId": 1000 }
```

事件序列（简化）：
- node：QUERY 节点（data.title="用户提问"，data.text=prompt，data.mode=GENERAL）
- node：ANSWER_POINT 流节点（title="回答角度"）
- text：持续 token（“角度1：…\n…\n\n角度2：…\n…”）
- node：多个 ANSWER_POINT 子节点（每个角度一个）
- done

ANSWER_POINT 子节点示例：
```json
{
  "id": 126,
  "type": "answer-point",
  "data": { "title": "变量和数据类型", "text": "…", "subtype": "ANSWER_POINT" },
  "parentId": 124
}
```

### 2) 通用对话详情（GENERAL_DETAIL）

请求：
```json
{ "type": "GENERAL_DETAIL", "prompt": "", "convId": 5001, "parentId": 126 }
```

事件序列（简化）：
- node：ANSWER_DETAIL 流节点（title="详细解析"，angle 由父节点标题自动带入）
- text：持续 token
- done

ANSWER_DETAIL 节点最终形态：
```json
{
  "id": 127,
  "type": "answer-detail",
  "data": { "title": "详细解析", "subtype": "ANSWER_DETAIL", "text": "……", "angle": "变量和数据类型" },
  "parentId": 126
}
```

### 3) 电路分析（CIRCUIT_ANALYSIS）→ 分点

请求的 prompt 为电路画布对象（此处略）。事件序列：
- node：CIRCUIT_CANVAS 画布节点
- text：ANSWER_POINT 流式角度文本
- node：多个 CIRCUIT_POINT 分点子节点
- done

分点节点示例：
```json
{
  "id": 141,
  "type": "circuit-point",
  "data": { "title": "工作原理", "text": "……", "subtype": "circuit-point" },
  "parentId": 140
}
```

### 4) 电路分析点详情（CIRCUIT_DETAIL）

请求：
```json
{ "type": "CIRCUIT_DETAIL", "prompt": "", "convId": 5001, "parentId": 141 }
```

事件序列：
- node：ANSWER 流节点（title="电路类型详情" 等，angle 从父节点标题带入）
- text：持续 token
- done

---

### 5) PDF 电路实验任务分点（PDF_CIRCUIT_ANALYSIS）

用途：前端传入 PDF 的 Minio 预签名 URL，后端尝试下载并抽取正文，按模板生成“分点”。

请求：
```json
{ "type": "PDF_CIRCUIT_ANALYSIS", "prompt": "https://minio.example.com/bucket/docs/exp-001.pdf?X-Amz-...", "convId": 5001, "parentId": 1000 }
```

事件序列：
- node：QUERY 节点（data.text=该 URL，data.mode=PDF_CIRCUIT_ANALYSIS）
- text：ANSWER_POINT 流式角度文本
- node：多个 PDF_CIRCUIT_POINT 分点子节点
- done

分点节点示例：
```json
{
  "id": 1013,
  "type": "pdf-circuit-point",
  "data": { "title": "关键器件与连接关系", "text": "识别运放/电阻/电容…", "subtype": "pdf-circuit-point" },
  "parentId": 1011
}
```

实现细节：
- 后端会优先尝试从 URL 解析 Minio objectName，下载 PDF，使用 `PdfUtils` 抽取正文填充模板 `{{text}}`；失败时将 URL 作为兜底文本。
- 分点文本解析遵循“角度X：标题 + 下一行描述 + 空行分隔”的规则；兼容 JSON 数组旧格式（title/description）。

### 6) PDF 电路实验任务分点详情（PDF_CIRCUIT_ANALYSIS_DETAIL）

用途：对选中的 `pdf-circuit-point` 进行“视觉+文本”双通道深度分析，伴随进度事件。

请求：
```json
{ "type": "PDF_CIRCUIT_ANALYSIS_DETAIL", "prompt": "文档UUID", "convId": 5001, "parentId": 1013 }
```

事件序列（含进度 DATA）：
- node：QUERY 节点（data 含 documentId/title/type 等）
- node：PDF_CIRCUIT_DETAIL 流式节点（title="电路分析回答"）
- data：{ stage: "init", status: "START", nodeId }
- 若为 PDF：
  - data：{ stage: "vision", status: "START", pages: [..], imageCount: n, nodeId }
  - data：{ stage: "vision", status: "BATCH_START", batchNo, totalBatches, nodeId }
  - data：{ stage: "vision", status: "BATCH_DONE", batchNo, keyPoints, nodeId }
  - …（多批）
  - data：{ stage: "summary", status: "START", nodeId }
- text：持续 token（最终一致性校验与总结）
- data：{ progress: "PDF电路分析完成", analysisResult: "…", nodeId }
- done

最终详情节点示例：
```json
{
  "id": 1021,
  "type": "pdf-circuit-detail",
  "data": { "title": "电路分析回答", "text": "一、实验概述…\n二、器件清单…\n…" },
  "parentId": 1020
}
```

注意：
- 详情任务要求父节点类型为 `pdf-circuit-point`。
- 视觉模型兜底：未显式选择视觉模型时默认切换为 `doubaoVision`。
- 出错时会发送 data：{ stage: "error", status: "FAILED", message, nodeId }，随后立即 done。

---

## 五、错误与完成事件

错误：
```json
{ "type": "error", "data": "错误信息或异常字符串" }
```

完成：
```json
{ "type": "done", "data": "" }
```

---

## 六、字段与类型对照表（关键）

- 事件类型（`AiTaskMessageType`）：`node` | `text` | `data` | `title` | `done` | `error`
- 节点类型（`NodeType`）：
  - `root`, `query`, `answer`, `answer-point`, `answer-detail`,
  - `knowledge-head`, `knowledge-detail`, `resource`,
  - `circuit-canvas`, `circuit-point`,
  - `pdf-circuit-point`, `pdf-circuit-detail`
- 节点标题（常见，`NodeTitle`）：`用户提问`、`回答角度`、`详细解析`、`电路设计`、`电路分析点`、`电路分析详情`、`PDF文档` 等

---

## 七、解析规则补充（回答角度）

系统对回答角度的解析：
1. 若响应为 JSON 数组（旧格式）：元素包含 `title`、`description` 字段
2. 否则按文本格式解析：
   - 标题行：匹配 `^角度\d+：.+`
   - 标题下一行：描述
   - 空行：分隔

---

## 八、联调建议

- SSE 订阅：前端收到 `done` 后应关闭对应任务的流；收到 `error` 需提示并允许重试。
- 节点树：渲染时以 `parentId` 组织层级，`position/height` 可用于画布布局。
- PDF URL：优先传 Minio 预签名 URL，便于后端落地抽取；其他外链目前走兜底路径。

### 1. 通用对话模式 (GENERAL)

#### 发送结构
```json
{
  "userId": 123456,
  "convId": 789012,
  "parentId": 345678,
  "taskId": "task_uuid",
  "type": "GENERAL",
  "model": "deepseekV3",
  "prompt": "请介绍一下Java的基本语法"
}
```

#### 响应节点
1. **查询节点**
```json
{
  "type": "NODE",
  "data": {
    "id": 123,
    "type": "QUERY",
    "data": {
      "title": "用户提问",
      "text": "请介绍一下Java的基本语法",
      "mode": "GENERAL"
    },
    "position": {"x": 0, "y": 0},
    "parentId": 345678
  }
}
```

2. **回答角度节点**
```json
{
  "type": "NODE",
  "data": {
    "id": 124,
    "type": "ANSWER_POINT",
    "data": {
      "title": "回答角度",
      "subtype": "ANSWER_POINT",
      "text": ""
    },
    "position": {"x": 0, "y": 0},
    "parentId": 123
  }
}
```

3. **文本流**
```json
{
  "type": "TEXT",
  "data": {
    "nodeId": 124,
    "content": "角度1：语法基础\n从Java的基本语法规则和代码结构出发进行讲解\n\n角度2：变量和数据类型\n介绍Java中的变量声明、基本数据类型和引用类型"
  }
}
```

4. **角度节点 - 示例1**
```json
{
  "type": "NODE",
  "data": {
    "id": 125,
    "type": "ANSWER_POINT",
    "data": {
      "title": "语法基础",
      "text": "从Java的基本语法规则和代码结构出发进行讲解",
      "subtype": "ANSWER_POINT"
    },
    "position": {"x": 0, "y": 0},
    "parentId": 124
  }
}
```

5. **角度节点 - 示例2**
```json
{
  "type": "NODE",
  "data": {
    "id": 126,
    "type": "ANSWER_POINT",
    "data": {
      "title": "变量和数据类型",
      "text": "介绍Java中的变量声明、基本数据类型和引用类型",
      "subtype": "ANSWER_POINT"
    },
    "position": {"x": 0, "y": 0},
    "parentId": 124
  }
}
```

6. **完成信号**
```json
{
  "type": "DONE",
  "data": ""
}
```

### 2. 通用对话详情模式 (GENERAL_DETAIL)

#### 发送结构
```json
{
  "userId": 123456,
  "convId": 789012,
  "parentId": 125,  // 回答角度节点ID
  "taskId": "task_uuid",
  "type": "GENERAL_DETAIL",
  "model": "deepseekV3"
}
```

#### 响应节点
1. **详细回答节点**
```json
{
  "type": "NODE",
  "data": {
    "id": 127,
    "type": "ANSWER_DETAIL",
    "data": {
      "title": "详细解析",
      "subtype": "ANSWER_DETAIL",
      "text": "",
      "angle": "语法基础"
    },
    "position": {"x": 0, "y": 0},
    "parentId": 125
  }
}
```

2. **文本流**
```json
{
  "type": "TEXT",
  "data": {
    "nodeId": 127,
    "content": "Java的基本语法..."
  }
}
```

3. **完成信号**
```json
{
  "type": "DONE",
  "data": ""
}
```

### 3. 知识点识别 (KNOWLEDGE)

#### 发送结构
```json
{
  "userId": 123456,
  "convId": 789012,
  "parentId": 345678,
  "taskId": "task_uuid",
  "type": "KNOWLEDGE",
  "prompt": "什么是矩阵的特征值和特征向量？"
}
```

#### 响应节点
1. **查询节点** (同上)

2. **回答节点** (同通用对话中的回答节点)

3. **知识点头节点**
```json
{
  "type": "NODE",
  "data": {
    "id": 128,
    "type": "KNOWLEDGE_HEAD",
    "data": {
      "title": "知识点",
      "text": "特征值和特征向量"
    },
    "position": {"x": 0, "y": 0},
    "parentId": 124
  }
}
```

### 4. 知识点详情 (KNOWLEDGE_DETAIL)

#### 发送结构
```json
{
  "userId": 123456,
  "convId": 789012,
  "parentId": 128,  // 知识点头节点ID
  "taskId": "task_uuid",
  "type": "KNOWLEDGE_DETAIL"
}
```

#### 响应节点
1. **知识点详情节点**
```json
{
  "type": "NODE",
  "data": {
    "id": 129,
    "type": "KNOWLEDGE_DETAIL",
    "data": {
      "title": "知识详情",
      "text": "",
      "media": {
        "animationObjectNames": [],
        "bilibiliUrls": [],
        "wordDocUrls": [],
        "pdfDocUrls": []
      }
    },
    "position": {"x": 0, "y": 0},
    "parentId": 128
  }
}
```

2. **动画资源节点** (如果存在)
```json
{
  "type": "NODE",
  "data": {
    "id": 130,
    "type": "RESOURCE",
    "data": {
      "title": "教学动画",
      "subtype": "ANIMATION",
      "objectNames": ["animation1.mp4", "animation2.mp4"]
    },
    "position": {"x": 0, "y": 0},
    "parentId": 129
  }
}
```

3. **B站视频节点** (如果存在)
```json
{
  "type": "NODE",
  "data": {
    "id": 131,
    "type": "RESOURCE",
    "data": {
      "title": "B站视频",
      "subtype": "BILIBILI",
      "urls": ["https://www.bilibili.com/video/xxx"]
    },
    "position": {"x": 0, "y": 0},
    "parentId": 129
  }
}
```

### 5. 动画生成 (ANIMATION)

#### 发送结构
```json
{
  "userId": 123456,
  "convId": 789012,
  "parentId": 345678,
  "taskId": "task_uuid",
  "type": "ANIMATION",
  "prompt": "请制作一个展示二次函数图像变化的动画"
}
```

#### 响应节点
1. **查询节点** (同上)

2. **回答节点** (同上)

3. **动画节点**
```json
{
  "type": "NODE",
  "data": {
    "id": 133,
    "type": "RESOURCE",
    "data": {
      "title": "生成动画",
      "subtype": "GENERATED_ANIMATION",
      "progress": "开始生成动画..."
    },
    "position": {"x": 0, "y": 0},
    "parentId": 124
  }
}
```

### 6. 解题分析 (SOLVER_FIRST)

#### 发送结构
```json
{
  "userId": 123456,
  "convId": 789012,
  "parentId": 345678,
  "taskId": "task_uuid",
  "type": "SOLVER_FIRST",
  "prompt": "求解方程 x^2 + 5x + 6 = 0",
  "promptParams": {
    "method": "因式分解法"
  }
}
```

#### 响应节点
1. **查询节点** (同上)

2. **解题分析节点**
```json
{
  "type": "NODE",
  "data": {
    "id": 134,
    "type": "ANSWER",
    "data": {
      "title": "题目分析",
      "subtype": "SOLVER_FIRST",
      "text": ""
    },
    "position": {"x": 0, "y": 0},
    "parentId": 123
  }
}
```

### 7. 解题推导 (SOLVER_CONTINUE)

#### 发送结构
```json
{
  "userId": 123456,
  "convId": 789012,
  "parentId": 134,  // 解题分析节点ID
  "taskId": "task_uuid",
  "type": "SOLVER_CONTINUE"
}
```

#### 响应节点
1. **解题推导节点**
```json
{
  "type": "NODE",
  "data": {
    "id": 135,
    "type": "ANSWER",
    "data": {
      "title": "题目推导",
      "subtype": "SOLVER_CONTINUE",
      "text": ""
    },
    "position": {"x": 0, "y": 0},
    "parentId": 134
  }
}
```

### 8. 解题总结 (SOLVER_SUMMARY)

#### 发送结构
```json
{
  "userId": 123456,
  "convId": 789012,
  "parentId": 135,  // 解题推导节点ID
  "taskId": "task_uuid",
  "type": "SOLVER_SUMMARY"
}
```

#### 响应节点
1. **解题总结节点**
```json
{
  "type": "NODE",
  "data": {
    "id": 136,
    "type": "ANSWER",
    "data": {
      "title": "题目总结",
      "subtype": "SOLVER_SUMMARY",
      "text": ""
    },
    "position": {"x": 0, "y": 0},
    "parentId": 135
  }
}
```

### 9. 电路分析 (CIRCUIT_ANALYSIS)

#### 发送结构
```json
{
  "userId": 123456,
  "convId": 789012,
  "parentId": 345678,
  "taskId": "task_uuid",
  "type": "CIRCUIT_ANALYSIS",
  "model": "deepseekV3",  // 使用的AI模型
  "prompt": {
    "elements": [
      {
        "id": "r1",
        "type": "resistor",
        "position": {"x": 100, "y": 100},
        "rotation": 0,
        "properties": {"resistance": "1k"},
        "ports": [...]
      },
      {
        "id": "v1",
        "type": "voltage_source",
        "position": {"x": 50, "y": 100},
        "rotation": 90,
        "properties": {"voltage": "5V"},
        "ports": [...]
      }
    ],
    "connections": [...],
    "metadata": {...}
  }
}
```

#### 响应节点
1. **电路画布节点**
```json
{
  "type": "NODE",
  "data": {
    "id": 140,
    "type": "CIRCUIT_CANVAS",
    "data": {
      "title": "电路设计",
      "text": "电路分析请求",
      "circuitDesign": {...},
      "mode": "CIRCUIT_ANALYSIS"
    },
    "position": {"x": 0, "y": 0},
    "parentId": 345678
  }
}
```

2. **电路分析点节点**
```json
{
  "type": "NODE",
  "data": {
    "id": 141,
    "type": "CIRCUIT_POINT",
    "data": {
      "title": "电路类型",
      "text": "这是一个基于运算放大器的反相放大电路，由输入电阻、反馈电阻和运放组成",
      "subtype": "circuit-point"
    },
    "position": {"x": 0, "y": 0},
    "parentId": 140
  }
}
```

3. **更多电路分析点节点示例**
```json
{
  "type": "NODE",
  "data": {
    "id": 142,
    "type": "CIRCUIT_POINT",
    "data": {
      "title": "工作原理",
      "text": "该电路通过负反馈原理实现输入信号的放大，反相器输出与输入信号相位相反",
      "subtype": "circuit-point"
    },
    "position": {"x": 0, "y": 0},
    "parentId": 140
  }
}
```

### 10. 电路分析点详情 (CIRCUIT_DETAIL)

#### 发送结构
```json
{
  "userId": 123456,
  "convId": 789012,
  "parentId": 141,  // 电路分析点节点的ID
  "taskId": "task_uuid",
  "type": "CIRCUIT_DETAIL",
  "model": "deepseekV3",  // 使用的AI模型（可选）
  "prompt": null  // 可选字段，可以不传或传null值
}
```

#### 响应节点
```json
{
  "type": "NODE",
  "data": {
    "id": 143,
    "type": "ANSWER",
    "data": {
      "title": "电路类型详情",
      "text": "## 电路类型\n本电路是一个典型的反相运算放大器电路，它是模拟电子电路中最基础也最常用的电路之一。\n\n## 详细分析\n反相放大器由一个运算放大器（Op-Amp）和两个电阻（反馈电阻Rf和输入电阻Rin）组成。输入信号连接到运放的反相输入端，而同相输入端则接地。这种配置使得输出信号与输入信号相位相差180度（即反相）。\n\n## 技术参数\n放大倍数计算：$A_v = -\\frac{R_f}{R_{in}}$\n\n对于本电路，放大倍数为：$A_v = -\\frac{10k\\Omega}{1k\\Omega} = -10$\n\n这意味着输出信号的幅度是输入信号的10倍，但相位相反。\n\n## 要点总结\n1. 本电路是反相放大器，输出与输入相位相差180度\n2. 放大倍数由反馈电阻与输入电阻的比值决定\n3. 输入阻抗等于输入电阻Rin的值\n4. 理想情况下，运放的虚短特性使反相输入端电压接近于0V",
      "subtype": "circuit-detail",
      "angle": "电路类型"
    },
    "position": {"x": 0, "y": 0},
    "parentId": 141
  }
}
```

## 三、错误处理

当任务处理出现错误时，后端会发送如下格式的错误信息：

```json
{
  "type": "ERROR",
  "data": "发生了错误：无法解析电路设计数据"
}
```

## 四、任务配置注意事项

1. **任务类型必须正确**：确保`type`字段使用`AiTaskType`类中定义的常量值。
2. **父节点类型匹配**：某些任务类型（如`KNOWLEDGE_DETAIL`、`GENERAL_DETAIL`、`CIRCUIT_DETAIL`）要求特定类型的父节点。
3. **提示内容格式**：大多数任务使用字符串作为`prompt`，但`CIRCUIT_ANALYSIS`使用`CircuitDesign`对象。
4. **任务链接顺序**：
   - 解题类任务通常按照：`SOLVER_FIRST` → `SOLVER_CONTINUE` → `SOLVER_SUMMARY`的顺序使用。
   - 通用对话类任务通常按照：`GENERAL`(生成角度) → `GENERAL_DETAIL`(展开具体角度)的顺序使用。
   - 电路分析任务通常按照：`CIRCUIT_ANALYSIS`(生成分析点) → `CIRCUIT_DETAIL`(展开具体分析点)的顺序使用。
5. **模型选择**：可以通过`model`字段指定使用的AI模型，不指定时使用默认模型。
6. **详情类任务参数**：
   - `GENERAL_DETAIL`不需要传递角度参数，系统会自动从父节点中读取角度信息。
   - `CIRCUIT_DETAIL`不需要传递电路设计数据，系统会自动从上游节点中读取相关信息。

## 五、节点类型说明

本系统中使用的主要节点类型包括：

1. **ROOT**：根节点，每个会话的起始点
2. **QUERY**：用户查询节点，存储用户的提问
3. **ANSWER**：AI回答节点，存储常规AI回答内容
4. **ANSWER_POINT**：回答角度节点，存储问题可能的不同回答角度
5. **ANSWER_DETAIL**：详细回答节点，存储特定角度的详细解析
6. **KNOWLEDGE_HEAD**：知识点头节点，存储识别出的知识点
7. **KNOWLEDGE_DETAIL**：知识点详情节点，存储知识点的详细内容
8. **RESOURCE**：资源节点，存储各类资源（动画、视频、文档等）
9. **CIRCUIT_CANVAS**：电路画布节点，存储电路设计数据
10. **CIRCUIT_POINT**：电路分析点节点，存储电路分析的不同角度

## 六、回答角度数据格式更新说明

### 1. 旧版格式（JSON格式）

在旧版本中，回答角度使用JSON数组格式输出：

```json
[
  {
    "title": "语法基础",
    "description": "从Java的基本语法规则和代码结构出发进行讲解"
  },
  {
    "title": "变量和数据类型",
    "description": "介绍Java中的变量声明、基本数据类型和引用类型"
  }
]
```

### 2. 新版格式（文本格式）

在最新版本中，回答角度使用与知识点头节点相似的文本格式输出：

```
角度1：语法基础
从Java的基本语法规则和代码结构出发进行讲解

角度2：变量和数据类型
介绍Java中的变量声明、基本数据类型和引用类型
```

### 3. 格式解析流程

系统现在支持两种格式的解析：
1. 首先尝试以JSON格式解析（兼容旧格式）
2. 如果JSON解析失败，则使用文本格式解析

解析文本格式时：
- 匹配"角度X：[标题]"格式的行作为标题
- 标题后的行作为描述
- 空行作为分隔符

### 4. 优点说明

- **统一数据格式**：与知识问答模式保持一致的数据格式
- **降低复杂度**：文本格式更易于阅读和调试
- **兼容性保障**：保留对旧版JSON格式的兼容支持
- **提高可维护性**：统一处理流程，简化代码结构
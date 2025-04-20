# Drawsee平台AI任务发送与响应数据规范文档（2024版）

## 一、通用任务结构

### 1. 前端发送的任务数据结构

```json
{
  "userId": 123456,  // 用户ID（必填）
  "convId": 789012,  // 会话ID（必填）
  "parentId": 345678,  // 父节点ID（必填）
  "taskId": "task_uuid",  // 任务ID（必填）
  "type": "TASK_TYPE",  // 任务类型（必填）
  "model": "deepseekV3",  // 使用的AI模型（可选）
  "prompt": "用户输入的提示内容",  // 提示内容（必填）
  "promptParams": {  // 额外的提示参数（可选）
    "method": "解题方法"  // 特定任务类型的参数
  }
}
```

### 2. 后端响应的基本流程

后端通过Redis Stream持续向前端推送如下类型的数据：

1. **NODE类型**：创建节点
2. **TEXT类型**：流式输出文本内容
3. **DATA类型**：更新节点属性或进度
4. **TITLE类型**：更新会话标题
5. **DONE类型**：任务完成信号
6. **ERROR类型**：错误信息

## 二、各类任务模式详细说明

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
1. **查询节点**
```json
{
  "type": "NODE",
  "data": {
    "id": 140,
    "type": "QUERY",
    "data": {
      "title": "电路分析请求",
      "text": "电路分析请求",
      "circuitDesign": {...},
      "mode": "CIRCUIT_ANALYSIS"
    },
    "position": {"x": 0, "y": 0},
    "parentId": 345678
  }
}
```

2. **电路基本分析节点**
```json
{
  "type": "NODE",
  "data": {
    "id": 141,
    "type": "ANSWER",
    "data": {
      "title": "电路基本分析",
      "subtype": "CIRCUIT_BASIC",
      "progress": "正在分析电路基本情况...",
      "basicAnalysis": "这个电路由一个5V电压源和一个1kΩ电阻组成。根据欧姆定律，电路中的电流为I=V/R=5V/1kΩ=5mA。"
    },
    "position": {"x": 0, "y": 0},
    "parentId": 140
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
2. **父节点类型匹配**：某些任务类型（如`KNOWLEDGE_DETAIL`、`GENERAL_DETAIL`）要求特定类型的父节点。
3. **提示内容格式**：大多数任务使用字符串作为`prompt`，但`CIRCUIT_ANALYSIS`使用`CircuitDesign`对象。
4. **任务链接顺序**：
   - 解题类任务通常按照：`SOLVER_FIRST` → `SOLVER_CONTINUE` → `SOLVER_SUMMARY`的顺序使用。
   - 通用对话类任务通常按照：`GENERAL`(生成角度) → `GENERAL_DETAIL`(展开具体角度)的顺序使用。
5. **模型选择**：可以通过`model`字段指定使用的AI模型，不指定时使用默认模型。
6. **GENERAL_DETAIL不需要传递角度参数**：最新版本已优化，前端发送`GENERAL_DETAIL`任务时不需要在promptParams中包含`angle`参数，系统会自动从父节点中读取角度信息。

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
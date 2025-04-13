# Drawsee平台AI任务发送与响应数据规范文档

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

2. **回答节点**
```json
{
  "type": "NODE",
  "data": {
    "id": 124,
    "type": "ANSWER",
    "data": {
      "title": "AI解析",
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
    "content": "Java是一种..."
  }
}
```

4. **完成信号**
```json
{
  "type": "DONE",
  "data": ""
}
```

### 2. 知识点识别 (KNOWLEDGE)

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

2. **回答节点** (同上)

3. **知识点头节点**
```json
{
  "type": "NODE",
  "data": {
    "id": 125,
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

### 3. 知识点详情 (KNOWLEDGE_DETAIL)

#### 发送结构
```json
{
  "userId": 123456,
  "convId": 789012,
  "parentId": 125,  // 知识点头节点ID
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
    "id": 126,
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
    "parentId": 125
  }
}
```

2. **动画资源节点** (如果存在)
```json
{
  "type": "NODE",
  "data": {
    "id": 127,
    "type": "RESOURCE",
    "data": {
      "title": "教学动画",
      "subtype": "ANIMATION",
      "objectNames": ["animation1.mp4", "animation2.mp4"]
    },
    "position": {"x": 0, "y": 0},
    "parentId": 126
  }
}
```

3. **B站视频节点** (如果存在)
```json
{
  "type": "NODE",
  "data": {
    "id": 128,
    "type": "RESOURCE",
    "data": {
      "title": "B站视频",
      "subtype": "BILIBILI",
      "urls": ["https://www.bilibili.com/video/xxx"]
    },
    "position": {"x": 0, "y": 0},
    "parentId": 126
  }
}
```

4. **Word文档节点** (如果存在)
```json
{
  "type": "NODE",
  "data": {
    "id": 129,
    "type": "RESOURCE",
    "data": {
      "title": "Word文档",
      "subtype": "WORD",
      "urls": ["https://example.com/doc.docx"]
    },
    "position": {"x": 0, "y": 0},
    "parentId": 126
  }
}
```

5. **PDF文档节点** (如果存在)
```json
{
  "type": "NODE",
  "data": {
    "id": 130,
    "type": "RESOURCE",
    "data": {
      "title": "PDF文档",
      "subtype": "PDF",
      "urls": ["https://example.com/document.pdf"]
    },
    "position": {"x": 0, "y": 0},
    "parentId": 126
  }
}
```

### 4. 动画生成 (ANIMATION)

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
    "id": 131,
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

4. **进度更新**
```json
{
  "type": "DATA",
  "data": {
    "progress": "正在生成动画分镜...",
    "nodeId": 131
  }
}
```

5. **最终帧数据**
```json
{
  "type": "DATA",
  "data": {
    "nodeId": 131,
    "frame": "base64编码的图像数据"
  }
}
```

### 5. 解题分析 (SOLVER_FIRST)

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
    "id": 132,
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

### 6. 解题推导 (SOLVER_CONTINUE)

#### 发送结构
```json
{
  "userId": 123456,
  "convId": 789012,
  "parentId": 132,  // 解题分析节点ID
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
    "id": 133,
    "type": "ANSWER",
    "data": {
      "title": "题目推导",
      "subtype": "SOLVER_CONTINUE",
      "text": ""
    },
    "position": {"x": 0, "y": 0},
    "parentId": 132
  }
}
```

2. **推导完成状态**
```json
{
  "type": "DATA",
  "data": {
    "nodeId": 133,
    "isDone": true
  }
}
```

### 7. 解题总结 (SOLVER_SUMMARY)

#### 发送结构
```json
{
  "userId": 123456,
  "convId": 789012,
  "parentId": 133,  // 解题推导节点ID
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
    "id": 134,
    "type": "ANSWER",
    "data": {
      "title": "题目总结",
      "subtype": "SOLVER_SUMMARY",
      "text": ""
    },
    "position": {"x": 0, "y": 0},
    "parentId": 133
  }
}
```

### 8. 目标规划 (PLANNER)

#### 发送结构
```json
{
  "userId": 123456,
  "convId": 789012,
  "parentId": 345678,
  "taskId": "task_uuid",
  "type": "GENERAL",
  "prompt": "如何准备高考数学考试？"
}
```

#### 响应节点
1. **查询节点** (同上)

2. **目标分析节点**
```json
{
  "type": "NODE",
  "data": {
    "id": 135,
    "type": "ANSWER",
    "data": {
      "title": "目标分析",
      "subtype": "PLANNER_FIRST",
      "text": ""
    },
    "position": {"x": 0, "y": 0},
    "parentId": 123
  }
}
```

### 9. HTML制作 (HTML_MAKER)

#### 发送结构
```json
{
  "userId": 123456,
  "convId": 789012,
  "parentId": 345678,
  "taskId": "task_uuid",
  "type": "GENERAL",
  "prompt": "制作一个简单的个人介绍网页"
}
```

#### 响应节点
1. **查询节点** (同上)

2. **HTML制作节点**
```json
{
  "type": "NODE",
  "data": {
    "id": 136,
    "type": "ANSWER",
    "data": {
      "title": "HTML制作",
      "subtype": "HTML_MAKER",
      "text": ""
    },
    "position": {"x": 0, "y": 0},
    "parentId": 123
  }
}
```

### 10. 电路分析 (CIRCUIT_ANALYSIS)

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
        "ports": [
          {
            "id": "p1", 
            "name": "端口1", 
            "type": "input",
            "position": {"side": "left", "x": 0, "y": 0, "align": "start"}
          },
          {
            "id": "p2", 
            "name": "端口2", 
            "type": "output",
            "position": {"side": "right", "x": 0, "y": 0, "align": "start"}
          }
        ]
      },
      {
        "id": "v1",
        "type": "voltage_source",
        "position": {"x": 50, "y": 100},
        "rotation": 90,
        "properties": {"voltage": "5V"},
        "ports": [
          {
            "id": "p1", 
            "name": "正极", 
            "type": "output",
            "position": {"side": "top", "x": 0, "y": 0, "align": "center"}
          },
          {
            "id": "p2", 
            "name": "负极", 
            "type": "input",
            "position": {"side": "bottom", "x": 0, "y": 0, "align": "center"}
          }
        ]
      }
    ],
    "connections": [
      {
        "id": "conn1",
        "source": {"elementId": "v1", "portId": "p1"},
        "target": {"elementId": "r1", "portId": "p1"}
      },
      {
        "id": "conn2",
        "source": {"elementId": "r1", "portId": "p2"},
        "target": {"elementId": "v1", "portId": "p2"}
      }
    ],
    "metadata": {
      "title": "简单电路示例",
      "description": "一个包含电阻和电压源的电路",
      "createdAt": "2024-04-01T10:00:00Z",
      "updatedAt": "2024-04-01T10:00:00Z"
    }
  }
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
      "title": "电路分析请求",
      "text": "电路分析请求",
      "circuitDesign": {
        "elements": [
          {
            "id": "r1",
            "type": "resistor",
            "position": {"x": 100, "y": 100},
            "rotation": 0,
            "properties": {"resistance": "1k"},
            "ports": [
              {
                "id": "p1",
                "name": "端口1",
                "type": "input",
                "position": {"side": "left", "x": 0, "y": 0, "align": "start"}
              },
              {
                "id": "p2",
                "name": "端口2",
                "type": "output",
                "position": {"side": "right", "x": 0, "y": 0, "align": "start"}
              }
            ]
          },
          {
            "id": "v1",
            "type": "voltage_source",
            "position": {"x": 50, "y": 100},
            "rotation": 90,
            "properties": {"voltage": "5V"},
            "ports": [
              {
                "id": "p1",
                "name": "正极",
                "type": "output",
                "position": {"side": "top", "x": 0, "y": 0, "align": "center"}
              },
              {
                "id": "p2",
                "name": "负极",
                "type": "input",
                "position": {"side": "bottom", "x": 0, "y": 0, "align": "center"}
              }
            ]
          }
        ],
        "connections": [
          {
            "id": "conn1",
            "source": {"elementId": "v1", "portId": "p1"},
            "target": {"elementId": "r1", "portId": "p1"}
          },
          {
            "id": "conn2",
            "source": {"elementId": "r1", "portId": "p2"},
            "target": {"elementId": "v1", "portId": "p2"}
          }
        ],
        "metadata": {
          "title": "简单电路示例",
          "description": "一个包含电阻和电压源的电路",
          "createdAt": "2024-04-01T10:00:00Z",
          "updatedAt": "2024-04-01T10:00:00Z"
        }
      },
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
    "id": 137,
    "type": "ANSWER",
    "data": {
      "title": "电路基本分析",
      "subtype": "CIRCUIT_BASIC",
      "progress": "正在分析电路基本情况...",
      "basicAnalysis": "这个电路由一个5V电压源和一个1kΩ电阻组成。根据欧姆定律，电路中的电流为I=V/R=5V/1kΩ=5mA。"
    },
    "position": {"x": 0, "y": 0},
    "parentId": 123
  }
}
```

3. **节点分析节点**
```json
{
  "type": "NODE",
  "data": {
    "id": 138,
    "type": "ANSWER",
    "data": {
      "title": "节点[N1]分析",
      "subtype": "CIRCUIT_NODE_ANALYSIS",
      "progress": "正在分析节点[N1]...",
      "nodeName": "N1",
      "nodeDescription": "电压源正极与电阻连接点",
      "nodeAnalysis": "节点N1连接电压源的正极和电阻的一端，维持在5V电位。"
    },
    "position": {"x": 0, "y": 0},
    "parentId": 137
  }
}
```

4. **电路功能分析节点**
```json
{
  "type": "NODE",
  "data": {
    "id": 139,
    "type": "ANSWER",
    "data": {
      "title": "电路功能分析",
      "subtype": "CIRCUIT_FUNCTION",
      "progress": "正在分析电路功能...",
      "functionAnalysis": "这是一个简单的负载电路，电压源提供恒定电压，电阻作为负载消耗电能，将电能转换为热能。"
    },
    "position": {"x": 0, "y": 0},
    "parentId": 137
  }
}
```

5. **电路优化建议节点**
```json
{
  "type": "NODE",
  "data": {
    "id": 140,
    "type": "ANSWER",
    "data": {
      "title": "电路优化建议",
      "subtype": "CIRCUIT_OPTIMIZATION",
      "progress": "正在生成优化建议...",
      "optimizationResult": "可以考虑添加一个并联电容以滤除可能的电压波动，提高电路稳定性。"
    },
    "position": {"x": 0, "y": 0},
    "parentId": 139
  }
}
```

6. **完成进度信息**
```json
{
  "type": "DATA",
  "data": {
    "progress": "电路分析完成",
    "nodeId": 140
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
2. **父节点类型匹配**：某些任务类型（如`KNOWLEDGE_DETAIL`）要求特定类型的父节点。
3. **提示内容格式**：大多数任务使用字符串作为`prompt`，但`CIRCUIT_ANALYSIS`使用`CircuitDesign`对象。
4. **任务链接顺序**：解题类任务通常按照`SOLVER_FIRST` → `SOLVER_CONTINUE` → `SOLVER_SUMMARY`的顺序使用。
5. **模型选择**：可以通过`model`字段指定使用的AI模型，不指定时使用默认模型。
6. **电路节点获取**：使用`/tool/circuit/nodes`接口获取电路节点标号信息，便于前端渲染节点标识。
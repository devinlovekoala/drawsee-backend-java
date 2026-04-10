以下是通用对话的分点功能（回答角度）回传数据的样例格式：

## 前端发送请求

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

## 后端响应流程

1. **查询节点**：
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

2. **父角度节点**：
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

3. **文本流**（可能包含文本块）：
```json
{
  "type": "TEXT",
  "data": {
    "nodeId": 124,
    "content": "角度1：语法结构\nJava程序的基本组成单位，包括类、方法和语句结构\n\n角度2：数据类型\nJava支持的基本数据类型和引用数据类型详解\n\n角度3：运算符和表达式\n各类运算符的使用方法和优先级规则\n\n角度4：流程控制\n条件判断、循环和跳转语句的语法与应用"
  }
}
```

4. **创建的角度子节点**（每个角度一个节点）：
```json
{
  "type": "NODE",
  "data": {
    "id": 125,
    "type": "ANSWER_POINT",
    "data": {
      "title": "语法结构",
      "text": "Java程序的基本组成单位，包括类、方法和语句结构",
      "subtype": "ANSWER_POINT"
    },
    "position": {"x": 0, "y": 0},
    "parentId": 124
  }
}
```

```json
{
  "type": "NODE",
  "data": {
    "id": 126,
    "type": "ANSWER_POINT",
    "data": {
      "title": "数据类型",
      "text": "Java支持的基本数据类型和引用数据类型详解",
      "subtype": "ANSWER_POINT"
    },
    "position": {"x": 0, "y": 0},
    "parentId": 124
  }
}
```

```json
{
  "type": "NODE",
  "data": {
    "id": 127,
    "type": "ANSWER_POINT",
    "data": {
      "title": "运算符和表达式",
      "text": "各类运算符的使用方法和优先级规则",
      "subtype": "ANSWER_POINT"
    },
    "position": {"x": 0, "y": 0},
    "parentId": 124
  }
}
```

```json
{
  "type": "NODE",
  "data": {
    "id": 128,
    "type": "ANSWER_POINT",
    "data": {
      "title": "流程控制",
      "text": "条件判断、循环和跳转语句的语法与应用",
      "subtype": "ANSWER_POINT"
    },
    "position": {"x": 0, "y": 0},
    "parentId": 124
  }
}
```

5. **完成信号**：
```json
{
  "type": "DONE",
  "data": ""
}
```

如您所见，后端已改为使用文本格式而非JSON格式来处理回答角度，这使得数据结构更加统一，与知识点问答模式保持一致。系统能够正确解析以"角度X：标题"开头的格式，生成相应的节点数据。
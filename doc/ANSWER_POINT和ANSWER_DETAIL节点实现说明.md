# ANSWER_POINT和ANSWER_DETAIL节点实现说明

## 实现流程概述

本文档详细说明了通用对话模式下ANSWER_POINT和ANSWER_DETAIL节点的实现流程，采用了与知识点详情类似的设计方式，实现从父节点获取必要信息的方案。

## 数据流程

### 1. 通用对话 (GENERAL) - 生成回答角度

1. **前端发送**:
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

2. **WorkFlow处理**:
   - 创建QUERY节点，存储原始问题
   - 创建ANSWER_POINT流式节点
   - 调用`StreamAiService.answerPointChat`方法生成回答角度
   - 解析回答角度JSON，创建多个ANSWER_POINT子节点

3. **提示词模板**:
   - 使用`answer-point.txt`模板，传入用户问题`{{question}}`
   - 大模型返回JSON格式的多个回答角度

### 2. 通用对话详情 (GENERAL_DETAIL) - 展开具体角度

1. **前端发送**:
   ```json
   {
     "userId": 123456,
     "convId": 789012,
     "parentId": 125,  // ANSWER_POINT节点ID
     "taskId": "task_uuid",
     "type": "GENERAL_DETAIL"
   }
   ```

2. **GeneralDetailWorkFlow处理**:
   - 验证父节点类型是否为ANSWER_POINT
   - 从父节点读取角度信息（title字段）
   - 创建ANSWER_DETAIL流式节点
   - 通过`findOriginalQuestion`方法回溯查找原始问题
   - 调用`StreamAiService.answerDetailChat`方法生成详细回答

3. **提示词模板**:
   - 使用`answer-detail.txt`模板，传入原始问题`{{question}}`和角度`{{angle}}`
   - 大模型返回特定角度的详细解析

## 关键实现细节

### 1. 父节点验证

```java
@Override
public Boolean validateAndInit(WorkContext workContext) {
    Boolean isValid = super.validateAndInit(workContext);
    if (!isValid) return false;
    
    Node parentNode = workContext.getParentNode();
    AiTaskMessage aiTaskMessage = workContext.getAiTaskMessage();
    
    // 校验父节点类型是否为回答角度节点
    if (!parentNode.getType().equals(NodeType.ANSWER_POINT)) {
        log.error("父节点不是回答角度节点, taskMessage: {}", aiTaskMessage);
        return false;
    }
    
    return true;
}
```

### 2. 回溯查找原始问题

```java
private String findOriginalQuestion(WorkContext workContext) {
    Node parentNode = workContext.getParentNode();
    Long convId = parentNode.getConvId();
    List<Node> nodes = nodeMapper.getByConvId(convId);
    Map<Long, Node> nodeMap = nodes.stream().collect(ConcurrentHashMap::new, (map, node) -> map.put(node.getId(), node), ConcurrentHashMap::putAll);
    
    // 向上回溯查找QUERY节点
    Node currentNode = parentNode;
    while (currentNode != null && !currentNode.getType().equals(NodeType.ROOT)) {
        Node nextNode = nodeMap.get(currentNode.getParentId());
        
        // 如果下一个节点是QUERY节点，则返回其文本内容
        if (nextNode != null && nextNode.getType().equals(NodeType.QUERY)) {
            try {
                TypeReference<Map<String, Object>> dataTypeRef = new TypeReference<>() {};
                Map<String, Object> queryNodeData = objectMapper.readValue(nextNode.getData(), dataTypeRef);
                return (String) queryNodeData.get("text");
            } catch (JsonProcessingException e) {
                log.error("解析QUERY节点数据失败: {}", e.getMessage());
                return null;
            }
        }
        
        currentNode = nextNode;
    }
    
    return null;
}
```

### 3. 创建详细回答节点

```java
@Override
public void createInitStreamNode(WorkContext workContext, Long parentNodeId) throws JsonProcessingException {
    AiTaskMessage aiTaskMessage = workContext.getAiTaskMessage();
    Node parentNode = workContext.getParentNode();
    
    // 从父节点(ANSWER_POINT)中读取角度信息
    TypeReference<Map<String, Object>> dataTypeRef = new TypeReference<>() {};
    Map<String, Object> parentNodeData = objectMapper.readValue(parentNode.getData(), dataTypeRef);
    String angleTitle = (String) parentNodeData.get("title");
    
    // 创建详细回答节点
    Map<String, Object> answerDetailNodeData = new ConcurrentHashMap<>();
    answerDetailNodeData.put("subtype", NodeSubType.ANSWER_DETAIL);
    answerDetailNodeData.put("title", NodeTitle.ANSWER_DETAIL);
    answerDetailNodeData.put("text", "");
    answerDetailNodeData.put("angle", angleTitle);
    
    Node answerDetailNode = new Node(
        NodeType.ANSWER_DETAIL,
        objectMapper.writeValueAsString(answerDetailNodeData),
        objectMapper.writeValueAsString(XYPosition.origin()),
        parentNodeId,
        aiTaskMessage.getUserId(),
        aiTaskMessage.getConvId(),
        true
    );
    insertAndPublishStreamNode(workContext, answerDetailNode, answerDetailNodeData);
}
```

## 改进点总结

1. **完全使用父节点获取数据**：与知识点详情相似，通过父节点获取角度信息，而不依赖前端传递参数。
2. **回溯查找原始问题**：通过向上遍历节点树找到原始QUERY节点，获取用户的初始问题。
3. **流程统一**：统一了通用对话与知识点详情的数据流程，提高了系统一致性。
4. **代码健壮性**：添加了类型验证，提高了系统的错误处理能力。

这种设计保持了前后端数据的一致性，简化了API接口，提高了系统的可维护性和可靠性。
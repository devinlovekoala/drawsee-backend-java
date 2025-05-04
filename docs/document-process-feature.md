# 电子教材自动解析功能文档

## 1. 功能概述

电子教材自动解析功能允许用户上传电子教材（如PDF、Word文档等），通过大模型AI技术自动提取教材中的知识点结构，并自动构建知识点层级关系，极大提高知识库初始化的效率。

## 2. 系统架构

### 2.1 核心组件

- **文档处理控制器(DocumentProcessController)**：提供REST API接口，处理用户的文档上传请求
- **文档处理服务(DocumentProcessService)**：实现文档解析和知识点提取的核心业务逻辑
- **Minio对象存储**：用于存储上传的原始文档和处理后的结果
- **大模型服务**：利用LangChain4j调用AI模型，分析文档内容并提取知识点结构

### 2.2 处理流程

1. 用户上传电子教材文件，指定目标知识库
2. 系统创建异步处理任务，返回任务ID
3. 系统后台解析文档，提取文本内容
4. 调用大模型分析文本，识别章节结构和知识点层级
5. 将识别出的知识点结构导入到指定知识库
6. 用户可以实时查询处理进度和最终结果

## 3. 特性与优势

- **自动化程度高**：完全自动化地将文档结构转化为知识库结构
- **异步处理**：支持后台处理大型文档，不阻塞用户操作
- **进度追踪**：提供实时进度查询，让用户了解处理状态
- **灵活配置**：支持多种提取选项，如关键词提取、知识图谱构建等
- **多格式支持**：支持PDF、Word、TXT等多种文档格式
- **智能分析**：利用大模型能力智能识别文档结构和内容关联
- **层级保留**：自动保留原始文档的章节层级关系

## 4. 使用方法

### 4.1 上传文档并解析

**API路径**：`/document/process/extract`  
**请求方式**：POST  
**请求参数**：
- `file`：文档文件（PDF、DOCX、TXT）
- `knowledgeBaseId`：目标知识库ID
- `options`：可选配置项，JSON格式

**示例请求**：
```http
POST /document/process/extract
Content-Type: multipart/form-data

file: [文件数据]
knowledgeBaseId: "60a1e2c45f3d2a1234567890"
options: "{\"extractKeywords\":true,\"buildKnowledgeGraph\":true,\"maxDepth\":4}"
```

**响应结果**：
```json
{
  "taskId": "8f7e6d5c-4b3a-2c1d-9e8f-7a6b5c4d3e2f",
  "status": "PENDING",
  "progress": 0,
  "extractedCount": 0,
  "importedCount": 0
}
```

### 4.2 查询处理状态

**API路径**：`/document/process/status/{taskId}`  
**请求方式**：GET  

**示例请求**：
```http
GET /document/process/status/8f7e6d5c-4b3a-2c1d-9e8f-7a6b5c4d3e2f
```

**响应结果**：
```json
{
  "taskId": "8f7e6d5c-4b3a-2c1d-9e8f-7a6b5c4d3e2f",
  "status": "PROCESSING",
  "progress": 60,
  "extractedCount": 45,
  "importedCount": 0,
  "knowledgeStructure": [
    {
      "title": "第一章 基础知识",
      "description": "本章介绍了该学科的基础概念和理论框架",
      "level": 1,
      "keywords": ["基础", "概念", "框架"],
      "summary": "这是第一章的内容摘要，介绍了学科基础...",
      "children": [...]
    }
  ]
}
```

### 4.3 取消处理任务

**API路径**：`/document/process/cancel/{taskId}`  
**请求方式**：DELETE  

**示例请求**：
```http
DELETE /document/process/cancel/8f7e6d5c-4b3a-2c1d-9e8f-7a6b5c4d3e2f
```

## 5. 技术实现细节

- 使用PDFBox库解析PDF文档
- 使用Apache POI解析Word文档
- 使用Minio存储上传的文档文件
- 使用Redisson管理异步任务状态
- 利用Spring的CompletableFuture实现异步处理
- 通过LangChain4j调用大模型API进行文本分析

## 6. 注意事项与限制

- 支持的最大文件大小为100MB
- 当前支持的文档格式：PDF、DOCX、TXT
- 处理大型文档可能需要较长时间
- 知识点提取质量依赖于原始文档结构的清晰度
- 建议在导入后手动检查和微调自动生成的知识点

## 7. 后续优化方向

- 支持更多文档格式（EPUB、PPT等）
- 增强OCR能力，处理无法直接提取文本的扫描版文档
- 改进知识点关联推断，自动建立更丰富的知识图谱关系
- 加入批量导入功能，一次处理多个文档
- 增加文档结构预览和编辑功能，允许用户在导入前调整
- 集成向量数据库，实现更智能的知识检索
# 百度AI搜索API接入指南

## 概述

本项目使用**百度AI搜索API**（百度AppBuilder服务）为电路实验文档分析提供联网搜索能力，用于查询电子元器件的数据手册、引脚图等资料。

---

## API介绍

**百度AI搜索API** 是百度智能云千帆AppBuilder平台提供的智能搜索服务，整合了"百度搜索"与"智能搜索生成"功能，能够：
- 🔍 联网搜索最新信息
- 🧠 结合大模型生成结构化答案
- 📊 支持多种搜索模式和多模态搜索

**适用场景**：
- 电子元器件资料查询（数据手册、引脚图、参数）
- 实验教程和应用电路搜索
- 技术规格和标准文档查询

---

## 快速接入

### 步骤1：注册并获取API Key

1. **访问百度智能云千帆平台**
   - 网址：https://qianfan.cloud.baidu.com/
   - 注册/登录百度智能云账号

2. **开通AppBuilder服务**
   - 进入控制台
   - 找到"千帆AppBuilder"产品
   - 开通服务（新用户有免费额度）

3. **创建API Key**
   - 在控制台点击"API Key"
   - 点击"创建"
   - 服务选择：**千帆AppBuilder**
   - 复制生成的API Key（格式：`bce-v3/ALTAK-***`）

### 步骤2：配置项目

编辑 `application.yaml` 文件：

```yaml
drawsee:
  search:
    enabled: true  # 启用搜索功能
    api-key: bce-v3/ALTAK-YourApiKeyHere  # 替换为你的API Key
```

或使用环境变量（推荐）：

```bash
export SEARCH_ENABLED=true
export SEARCH_API_KEY="bce-v3/ALTAK-YourApiKeyHere"
```

### 步骤3：验证配置

启动项目后，查看日志：
```
INFO  - Web搜索功能已启用，使用百度AI搜索API
```

如果配置错误，会看到警告：
```
WARN  - Web搜索功能未启用或未配置API密钥
```

---

## API使用说明

### 接口端点

```
POST https://appbuilder.baidu.com/rpc/2.0/cloud_hub/v1/ai_engine/copilot_engine/service/v1/baidu_search_rag/general
```

### 请求示例

```java
// 项目中的使用方式（已封装）
WebSearchService searchService = ...;
String result = searchService.searchComponentInfo("555", "datasheet");
```

**底层API调用**：
```bash
curl -X POST 'https://appbuilder.baidu.com/rpc/2.0/cloud_hub/v1/ai_engine/copilot_engine/service/v1/baidu_search_rag/general' \
  -H 'Content-Type: application/json' \
  -H 'X-Appbuilder-Authorization: Bearer bce-v3/ALTAK-***' \
  -d '{
    "message": [
      {
        "role": "user",
        "content": "555定时器 数据手册 datasheet PDF 参数"
      }
    ],
    "instruction": "你是一位专业的电子工程助手，请简洁准确地回答用户关于电子元器件的问题。",
    "stream": false,
    "model": "ERNIE-4.0-8K"
  }'
```

### 响应示例

```json
{
  "result": {
    "answer": "555定时器是一款经典的集成电路芯片，广泛应用于定时、脉冲生成和振荡电路...",
    "content": [
      {
        "type": "text",
        "text": "555定时器的详细参数..."
      }
    ]
  }
}
```

---

## 代码实现

项目中的搜索服务位于：[WebSearchService.java](src/main/java/cn/yifan/drawsee/service/base/WebSearchService.java)

**核心方法**：

1. **单个元器件搜索**
```java
public String searchComponentInfo(String componentName, String searchType)
```
- `componentName`: 元器件型号（如"555"、"LM358"）
- `searchType`: 搜索类型
  - `datasheet`: 数据手册
  - `pinout`: 引脚图
  - `tutorial`: 使用教程
  - `general`: 通用信息

2. **批量搜索**
```java
public Map<String, String> batchSearchComponents(List<String> componentNames)
```
自动为多个元器件搜索数据手册和引脚图。

---

## 工作流集成

搜索功能已集成到PDF电路分析工作流中：

```
PDF上传
  ↓
多模态分析（提取文本+识别图片）
  ↓
智能识别元器件型号（正则匹配）
  ↓
【百度AI搜索】查询元器件资料  ← 这里使用搜索API
  ↓
整合内容生成分析报告
```

**识别的元器件类型**：
- 集成电路：555、LM358、74HC04
- 带封装芯片：NE555、CD4017
- 三极管/场效应管：2N3904、IRF540

---

## 费用与配额

### 免费额度

百度AppBuilder新用户免费额度：
- ✅ 每月一定量的免费调用次数
- ✅ 包含ERNIE-4.0-8K模型使用

### 计费方式

按API调用次数和Token消耗计费：
- 💰 搜索API调用：按次数计费
- 💰 大模型生成：按Token计费

**成本估算**（每个PDF文档）：
- 识别元器件：0-5个
- 每个元器件搜索2次（数据手册+引脚图）
- 总API调用：0-10次/文档
- 添加500ms延迟避免频繁调用

详细定价请查看：https://cloud.baidu.com/doc/AppBuilder/s/pricing

---

## 性能优化

### 1. 批量处理延迟
```java
// 在批量搜索中添加延迟避免超频
Thread.sleep(500);  // 500ms延迟
```

### 2. 结果缓存
建议在业务层添加缓存：
```java
// 使用Redis或本地缓存
@Cacheable(value = "componentSearch", key = "#componentName + '_' + #searchType")
public String searchComponentInfo(String componentName, String searchType) {
    // ...
}
```

### 3. 限制搜索数量
```java
// 最多搜索5个元器件
if (components.size() > 5) {
    components = components.subList(0, 5);
}
```

### 4. 答案长度控制
```java
// 限制每个答案最多500字符
if (answer.length() > 500) {
    answer = answer.substring(0, 500) + "...";
}
```

---

## 常见问题

### Q1: API Key格式是什么？
**A**: 百度AppBuilder API Key格式为 `bce-v3/ALTAK-***`，与百度其他服务的API Key格式不同。

### Q2: 搜索结果为空？
**A**: 可能原因：
1. API Key配置错误
2. 搜索查询过于具体，找不到结果
3. 网络连接问题

检查日志中的错误信息。

### Q3: 如何暂时禁用搜索功能？
**A**: 设置配置：
```yaml
drawsee:
  search:
    enabled: false
```

### Q4: 搜索速度慢？
**A**:
- 每个搜索需要调用百度服务器，延迟约1-3秒
- 批量搜索会串行执行，总耗时 = 数量 × (搜索时间 + 500ms延迟)
- 建议使用缓存减少重复搜索

### Q5: 与Google搜索API有何不同？
**A**:
| 对比项 | 百度AI搜索 | Google搜索 |
|--------|-----------|-----------|
| **可用性** | ✅ 国内可用 | ❌ 需要翻墙 |
| **结果形式** | AI生成摘要 | 链接列表 |
| **准确性** | 中文资料优秀 | 英文资料优秀 |
| **价格** | 按Token计费 | 按次数计费 |

---

## 技术支持

### 官方文档
- 百度AI搜索API文档：https://cloud.baidu.com/doc/AppBuilder/s/wm88pf14e
- 百度智能云千帆：https://qianfan.cloud.baidu.com/
- API在线调试：https://cloud.baidu.com/doc/AppBuilder/s/klv2eywua

### 社区支持
- 百度智能云千帆社区：https://qianfan.cloud.baidu.com/qianfandev/
- CSDN百度API专栏：搜索"百度AppBuilder API"

---

## 版本信息

- **API版本**: V1 (2025有效)
- **推荐模型**: ERNIE-4.0-8K
- **更新时间**: 2025-12-05

**注意**: 百度AppBuilder V1版本预计在2025年6月底下线，建议关注官方文档更新V2版本（支持OpenAI SDK调用）。

---

## 示例：完整搜索流程

```java
// 1. 配置API Key
drawsee:
  search:
    enabled: true
    api-key: "bce-v3/ALTAK-YourKeyHere"

// 2. 上传PDF文档
POST /api/pdf-analysis
Body: {
  "pdfUrl": "https://minio.xxx.com/实验文档.pdf"
}

// 3. 系统自动处理
[系统] 提取PDF文本和图片
[系统] 识别元器件: ["555", "LM358", "1N4148"]
[系统] 搜索元器件资料:
  - 555 数据手册: ✅ 返回参数和引脚信息
  - 555 引脚图: ✅ 返回引脚定义
  - LM358 数据手册: ✅ 返回运放参数
  ...

// 4. 返回增强后的分析结果
{
  "analysisPoints": [...],
  "componentInfo": {
    "555": "555定时器参数: VCC=5-15V, 输出电流200mA...",
    "LM358": "LM358双运放: 增益带宽1MHz, 输入偏置电流45nA..."
  }
}
```

---

## 总结

✅ **优势**：
- 国内可用，无需翻墙
- AI生成结构化答案，比链接列表更实用
- 中文电子元器件资料查询准确

⚠️ **注意事项**：
- 需要注册百度智能云账号
- 按使用量计费（有免费额度）
- 搜索速度比本地处理慢2-5秒

💡 **建议**：
- 在教学场景下启用以提供完整资料
- 在高并发场景下可暂时禁用以降低成本
- 配合缓存使用以提高性能

---

如有问题，请查看[官方文档](https://cloud.baidu.com/doc/AppBuilder/s/wm88pf14e)或联系技术支持。

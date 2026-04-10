# RAG 系统第一阶段集成完成总结

**日期**: 2025-12-12
**版本**: v1.0 Phase 1
**状态**: ✅ **开发完成，已全面部署，待端到端测试**

---

## 🎯 完成目标

**Phase 1: 文档管理增强** - 为现有文档上传功能添加 Python RAG ETL 支持

✅ 用户上传文档后自动触发 Python ETL 入库
✅ 实时轮询显示 ETL 任务处理进度
✅ Toast 通知用户处理成功/失败
✅ 前后端 API 接口完全对接

---

## 📋 新增文件清单

### Java 后端 (1 个文件)

#### ✅ RagController.java
**路径**: `src/main/java/cn/yifan/drawsee/controller/RagController.java`

**功能**:
- `POST /api/rag/documents/ingest` - 触发文档 ETL 入库
- `GET /api/rag/tasks/{taskId}` - 查询 ETL 任务状态
- `GET /api/rag/health` - 检查 RAG 服务健康状态

**关键特性**:
- 参数验证
- Python 服务可用性检查
- 详细的错误处理和日志记录
- 返回统一的 JSON 响应格式

### 前端 (5 个文件)

#### ✅ 1. rag-service.types.ts
**路径**: `src/api/types/rag-service.types.ts`

**导出类型**:
```typescript
- DocumentIngestRequest    // 文档入库请求
- DocumentIngestResponse   // 文档入库响应（含 task_id）
- TaskStatusResponse       // 任务状态响应（含进度、步骤、结果）
- RagHealthResponse        // RAG 服务健康状态
```

#### ✅ 2. rag-service.methods.ts
**路径**: `src/api/methods/rag-service.methods.ts`

**导出方法**:
```typescript
- ingestDocument()    // 触发文档入库 ETL
- getTaskStatus()     // 查询任务状态
- checkRagHealth()    // 检查服务健康状态
```

#### ✅ 3. useRagTask.ts
**路径**: `src/hooks/useRagTask.ts`

**功能**: 自定义 React Hook，用于轮询 ETL 任务状态

**特性**:
- 每 2 秒自动轮询
- 任务完成后自动停止轮询
- 支持 onSuccess/onError 回调
- 自动清理定时器

#### ✅ 4. RagTaskListener.tsx
**路径**: `src/components/knowledge-base/RagTaskListener.tsx`

**功能**: 轻量级任务监听组件（无 UI）

**特性**:
- 使用 useRagTask Hook 监听任务状态
- 通过 Toast 通知用户
- 成功时显示提取的电路数量
- 失败时显示错误原因
- 控制台输出处理进度日志

#### ✅ 5. DocumentManagerSection.tsx (修改)
**路径**: `src/components/knowledge-base/DocumentManagerSection.tsx`

**修改内容**:
1. 导入 RAG 相关模块
2. 添加 `ragTaskId` 状态
3. 修改 `handleUpload` 函数：
   - 上传成功后触发 `ingestDocument()`
   - 保存返回的 `task_id`
   - 显示"正在后台进行智能解析..."提示
4. 渲染 `RagTaskListener` 组件监听任务

---

## 🔌 API 接口对接

### 前后端接口映射

| 前端调用 | Java 后端 | Python 后端 | 说明 |
|---------|-----------|-------------|------|
| `ingestDocument()` | `POST /api/rag/documents/ingest` | `pythonRagService.ingestDocument()` | 触发 ETL |
| `getTaskStatus()` | `GET /api/rag/tasks/{taskId}` | `pythonRagService.getTaskStatus()` | 查询状态 |
| `checkRagHealth()` | `GET /api/rag/health` | `pythonRagService.isServiceAvailable()` | 健康检查 |

### 数据流

```
前端上传文档
    ↓
Java 存储文档元数据
    ↓
前端调用 ingestDocument()
    ↓
Java RagController 接收请求
    ↓
Java 调用 PythonRagService.ingestDocument()
    ↓
Python 接收请求，提交 Celery 任务
    ↓
Python 返回 task_id
    ↓
Java 转发 task_id 给前端
    ↓
前端开始轮询 getTaskStatus(task_id)
    ↓
每 2 秒查询一次任务状态
    ↓
任务完成后显示 Toast 通知并停止轮询
```

---

## ⚙️ 配置要求

### Java 配置

**application.yaml** 确保以下配置正确：

```yaml
drawsee:
  python-service:
    base-url: http://localhost:8001
    enabled: true
    timeout: 60000
```

### Python 配置

**app/config.py** 确保以下配置正确：

```python
DEBUG: bool = True  # 开发环境启用 DEBUG
SERVICE_PORT: int = 8001  # 默认端口
```

---

## 🧪 测试步骤

### 1. 启动所有服务

```bash
# 终端 1: 启动 Java 主服务
cd /home/devin/Workspace/drawsee-platform/drawsee-java
mvn spring-boot:run

# 终端 2: 启动 Python RAG 服务
cd /home/devin/Workspace/python/drawsee-rag-python
source .venv/bin/activate
uvicorn app.main:app --host 0.0.0.0 --port 8001 --reload

# 终端 3: 启动 Celery Worker
cd /home/devin/Workspace/python/drawsee-rag-python
source .venv/bin/activate
celery -A app.celery_config worker --loglevel=info

# 终端 4: 启动前端
cd /home/devin/Workspace/drawsee-platform/drawsee-admin-web
npm run dev
```

### 2. 验证服务健康

```bash
# Java 主服务
curl http://localhost:6868/actuator/health

# Python RAG 服务
curl http://localhost:8001/health
# 输出: {"status": "healthy"}

# RAG 控制器健康检查
curl http://localhost:6868/api/rag/health
# 输出: {"available": true, "message": "Python RAG 服务正常"}
```

### 3. 端到端测试流程

#### 步骤 1: 登录系统
- 访问 http://localhost:5173
- 使用教师或管理员账号登录

#### 步骤 2: 进入知识库
- 导航到"知识库管理"
- 选择或创建一个知识库
- 进入知识库详情页

#### 步骤 3: 上传文档
- 点击"选择文件"按钮
- 选择一个 PDF 文档
- 点击"上传文档"按钮

#### 步骤 4: 观察流程

**预期结果**:

1. **上传成功后立即显示**:
   ```
   ✅ Toast: "文档上传成功"
   ℹ️ Toast: "文档已上传，正在后台进行智能解析..."
   ```

2. **浏览器控制台输出**:
   ```
   [RAG ETL] 触发文档入库: {documentId: "xxx", knowledgeBaseId: "xxx", ...}
   [RAG ETL] 任务已提交: celery-task-id-xxx
   [RAG ETL] 正在解析 PDF 第 1/5 页 - 20%
   [RAG ETL] 正在生成向量... - 60%
   [RAG ETL] 写入向量数据库... - 90%
   ```

3. **2-5 分钟后显示**:
   ```
   ✅ Toast: "文档 RAG 处理完成！提取了 3 个电路"
   ```

4. **文档列表自动刷新**

#### 步骤 5: 检查后端日志

**Java 日志** (drawsee.log):
```
INFO  [RagController] 收到文档入库请求: documentId=xxx, knowledgeBaseId=xxx
INFO  [RagController] 文档入库任务触发成功: taskId=celery-task-id-xxx
DEBUG [RagController] 查询任务状态: taskId=celery-task-id-xxx
```

**Python 日志**:
```
INFO  [main.py] POST /api/v1/documents/ingest
INFO  [etl_tasks.py] 文档入库任务提交: task_id=celery-task-id-xxx
INFO  [etl_tasks.py] 开始解析 PDF: document_id=xxx
INFO  [etl_tasks.py] 提取了 3 个电路图
INFO  [etl_tasks.py] 生成了 150 个文本块
INFO  [etl_tasks.py] 生成了 150 个向量
INFO  [etl_tasks.py] 任务完成: task_id=celery-task-id-xxx
```

---

## 🐛 常见问题排查

### 问题 1: "Python RAG 服务暂不可用"

**原因**: Python 服务未启动或 Java 配置错误

**排查**:
```bash
# 1. 检查 Python 服务是否运行
curl http://localhost:8001/health

# 2. 检查 Java 配置
grep -A 3 "python-service" src/main/resources/application.yaml
# 确保 enabled: true, base-url: http://localhost:8001

# 3. 检查 Java 日志
tail -f logs/drawsee.log | grep "Python"
```

### 问题 2: "文档已上传，但智能解析启动失败"

**原因**: Celery Worker 未启动或 Redis 连接失败

**排查**:
```bash
# 1. 检查 Celery Worker 是否运行
ps aux | grep celery

# 2. 启动 Celery Worker
cd /home/devin/Workspace/python/drawsee-rag-python
source .venv/bin/activate
celery -A app.celery_config worker --loglevel=info

# 3. 检查 Redis
redis-cli ping
# 输出: PONG
```

### 问题 3: 任务一直 PENDING 不处理

**原因**: Celery Worker 队列配置错误

**排查**:
```bash
# 检查 Celery 任务队列
redis-cli
> LLEN celery  # 查看队列长度
> LRANGE celery 0 -1  # 查看队列内容

# 重启 Celery Worker
pkill -f "celery worker"
celery -A app.celery_config worker --loglevel=info
```

### 问题 4: 前端轮询报错

**原因**: API 路径错误或响应格式不匹配

**排查**:
```bash
# 1. 浏览器 DevTools → Network
# 检查请求: GET /api/rag/tasks/{taskId}
# 查看响应: 是否返回正确的 JSON 格式

# 2. 手动测试 API
curl http://localhost:6868/api/rag/tasks/your-task-id

# 3. 检查 TypeScript 类型是否匹配响应格式
```

---

## 📊 功能对比

### 集成前 (旧流程)

```
用户上传文档
    ↓
Java 存储元数据
    ↓
文档状态: UPLOADED
    ↓
【结束】无进一步处理
```

### 集成后 (新流程)

```
用户上传文档
    ↓
Java 存储元数据
    ↓
触发 Python ETL
    ↓
Celery 异步任务处理
    ↓
状态: PARSING → CHUNKING → EMBEDDING → INDEXING
    ↓
前端实时显示进度
    ↓
完成: COMPLETED
    ↓
Toast 通知用户
    ↓
文档可用于 RAG 检索
```

### 用户体验提升

| 维度 | 旧流程 | 新流程 |
|------|--------|--------|
| **上传后反馈** | 仅显示"上传成功" | 显示"正在后台智能解析..." |
| **进度可见性** | 无 | 实时轮询显示进度 |
| **处理状态** | 手动刷新页面 | 自动刷新文档列表 |
| **完成通知** | 无 | Toast 通知"提取了 X 个电路" |
| **错误提示** | 无 | Toast 通知具体错误原因 |

---

## 🎯 下一步计划

### Phase 2: RAG 检索测试界面 (2-3 周)

**目标**: 新增 RAG 检索测试工具页面

**功能**:
- 交互式查询输入面板
- Top-K 参数调整
- 检索结果可视化（BOM 表格、电路拓扑图）

### Phase 3: 系统监控 (1 周)

**目标**: RAG 系统运营监控仪表板

**功能**:
- ETL 任务执行统计 (使用 ECharts)
- 检索性能分析
- 知识库质量评估

---

## 📚 相关文档

- [RAG 前端集成完整方案](./RAG-Frontend-Integration-Plan.md)
- [快速开始指南](./RAG-Frontend-Quick-Start.md)
- [PythonRagService 使用分析](./PythonRagService-Usage-Analysis.md)
- [Python RAG API 文档](http://localhost:8001/docs)

---

## ✅ 完成清单

### Java 后端
- [x] 创建 `RagController.java`
- [x] 添加 `/api/rag/documents/ingest` 接口
- [x] 添加 `/api/rag/tasks/{taskId}` 接口
- [x] 添加 `/api/rag/health` 接口
- [x] 编译通过（mvn compile）

### 前端
- [x] 创建 `rag-service.types.ts` 类型定义
- [x] 创建 `rag-service.methods.ts` API 方法
- [x] 创建 `useRagTask.ts` Hook
- [x] 创建 `RagTaskListener.tsx` 组件
- [x] 修改 `DocumentManagerSection.tsx` 集成 ETL

### 文档
- [x] 创建集成总结文档
- [x] 包含测试步骤
- [x] 包含排查指南

---

**🎉 Phase 1 开发完成！等待测试验证。**

**预计测试时间**: 30 分钟
**预计修复时间**: 1 小时（如有问题）

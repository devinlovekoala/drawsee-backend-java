# RAG 系统后台管理前端集成方案

**日期**: 2025-12-10
**项目**: Drawsee RAG System v2.0
**前端工程**: drawsee-admin-web (`/home/devin/Workspace/drawsee-platform/drawsee-admin-web`)
**状态**: 📋 规划完成

---

## 📊 现有前端项目分析

### 技术栈
- **框架**: React 19.0 + TypeScript 5.7.2
- **构建**: Vite 6.2.0
- **路由**: React Router v7.4.0
- **UI**: Ant Design v5.27.4 + DaisyUI + Tailwind CSS v4.0
- **HTTP**: Alova v3.2.10
- **状态**: Local State (无 Redux/Zustand)
- **图标**: Lucide React

### 已有相关功能
✅ **知识库管理** (`KnowledgeBasePage.tsx`)
✅ **文档上传** (`DocumentManagerSection.tsx`)
✅ **RAG 知识列表** (`RagKnowledgeListPage.tsx`)

---

## 🎯 集成目标

### Phase 1: 文档管理增强 ⭐ 高优先级

**功能**: 为现有文档上传添加 Python RAG ETL 支持

**核心需求**:
1. 上传文档后自动触发 Python ETL 入库
2. 实时显示 ETL 任务处理进度（轮询）
3. 任务状态监控与失败重试

**涉及组件**:
- 修改: `DocumentManagerSection.tsx`
- 新增: `RagTaskProgressModal.tsx` (进度弹窗)
- 新增: `RagTaskStatusBadge.tsx` (状态徽章)
- 新增: `useRagTask.ts` Hook (任务轮询)

### Phase 2: RAG 检索测试界面 🔄 中优先级

**功能**: 新增 RAG 检索测试工具页面

**核心需求**:
1. 交互式查询输入面板
2. Top-K 参数调整
3. 检索结果可视化（BOM 表格、电路拓扑图、相似度）

**新增组件**:
- 新增页面: `RagTestPage.tsx`
- 新增组件: `RagQueryPanel.tsx`
- 新增组件: `RagResultCard.tsx`
- 新增组件: `CircuitTopologyViewer.tsx` (使用 Reactflow)

### Phase 3: 系统监控 ⏳ 低优先级

**功能**: RAG 系统运营监控仪表板

**核心需求**:
1. ETL 任务执行统计（使用 ECharts）
2. 检索性能分析
3. 知识库质量评估

---

## 📁 新增文件清单

```
src/
├── api/
│   ├── methods/
│   │   └── rag-service.methods.ts          # ✨ Python RAG 服务 API
│   └── types/
│       └── rag-service.types.ts            # ✨ RAG 类型定义
├── pages/
│   └── RagTestPage.tsx                     # ✨ RAG 检索测试页面
├── components/
│   ├── knowledge-base/
│   │   ├── DocumentManagerSection.tsx      # 🔧 修改: 集成 ETL
│   │   ├── RagTaskProgressModal.tsx        # ✨ ETL 进度弹窗
│   │   └── RagTaskStatusBadge.tsx          # ✨ 状态徽章
│   └── rag/                                # ✨ 新目录
│       ├── RagQueryPanel.tsx
│       ├── RagResultCard.tsx
│       └── CircuitTopologyViewer.tsx
└── hooks/
    └── useRagTask.ts                       # ✨ 任务轮询 Hook
```

---

## 🔌 API 集成设计

### 1. RAG 服务 API 方法

**文件**: `src/api/methods/rag-service.methods.ts`

```typescript
import { alovaInstance } from '../index';

/**
 * RAG 混合检索
 */
export const ragQuery = (data: RagQueryRequest) => {
  return alovaInstance.Post<RagQueryResponse>('/api/v1/rag/query', data, {
    baseURL: 'http://localhost:8001', // Python 服务
    timeout: 30000,
  });
};

/**
 * 触发文档入库 ETL（通过 Java 代理）
 */
export const ingestDocument = (data: DocumentIngestRequest) => {
  return alovaInstance.Post<DocumentIngestResponse>(
    '/api/rag/documents/ingest',
    data
  );
};

/**
 * 查询 ETL 任务状态
 */
export const getTaskStatus = (taskId: string) => {
  return alovaInstance.Get<TaskStatusResponse>(`/api/rag/tasks/${taskId}`);
};
```

### 2. TypeScript 类型定义

**文件**: `src/api/types/rag-service.types.ts`

**关键类型**:
- `RagQueryRequest` - 检索请求
- `RagQueryResponse` - 检索响应（含 BOM、拓扑、相似度）
- `DocumentIngestRequest` - 入库请求
- `TaskStatusResponse` - 任务状态（PENDING/PROCESSING/SUCCESS/FAILED）

---

## 🎨 核心 UI 组件

### 1. RAG 任务进度弹窗

**组件**: `RagTaskProgressModal`

**功能**:
- 实时显示 ETL 进度 (0-100%)
- 展示当前步骤 ("正在解析 PDF 第 3/10 页")
- 成功后显示统计（提取电路数、生成向量数）
- 失败时显示错误原因

**技术要点**:
```typescript
// 使用 useRagTask Hook 轮询任务状态
const { taskStatus, isPolling, error } = useRagTask(taskId, open);

// 每 2 秒查询一次
// 成功或失败后停止轮询
```

### 2. RAG 任务状态徽章

**组件**: `RagTaskStatusBadge`

**样式**:
- PENDING: 灰色 + Clock 图标
- PROCESSING: 蓝色 + 旋转 Loader2 图标
- SUCCESS: 绿色 + CheckCircle 图标
- FAILED: 红色 + XCircle 图标

### 3. RAG 检索结果卡片

**组件**: `RagResultCard`

**展示内容**:
- 电路名称 + 相似度分数
- 电路描述 (Caption)
- BOM 表格（元器件类型、参数值、数量）
- 电路拓扑图（使用 Reactflow 渲染）
- 电路图切片预览

---

## 🔧 现有组件修改

### DocumentManagerSection 修改点

**文件**: `src/components/knowledge-base/DocumentManagerSection.tsx`

**修改逻辑**:

```typescript
// 1. 上传成功后触发 Python ETL
const handleUploadSuccess = async (documentId: string, pdfPath: string) => {
  const response = await ingestDocument({
    document_id: documentId,
    knowledge_base_id: knowledgeBaseId,
    class_id: classId,
    user_id: getCurrentUserId(),
    pdf_path: pdfPath,
  });

  if (response.success) {
    setRagTaskId(response.task_id);
    setShowProgressModal(true); // 显示进度弹窗
  }
};

// 2. 渲染进度弹窗
<RagTaskProgressModal
  open={showProgressModal}
  taskId={ragTaskId}
  onClose={() => setShowProgressModal(false)}
  onSuccess={() => {
    Toast.success('文档 RAG 处理完成！');
    refreshDocumentList();
  }}
/>
```

---

## 🚀 实施步骤

### Week 1: 基础 API 与组件开发

**任务**:
- [ ] 创建 `rag-service.methods.ts` 和 `rag-service.types.ts`
- [ ] 开发 `RagTaskStatusBadge` 组件
- [ ] 开发 `RagTaskProgressModal` 组件
- [ ] 开发 `useRagTask` Hook

**验收标准**:
- ✅ 可独立运行组件 Storybook/demo
- ✅ TypeScript 类型检查通过

### Week 2: 文档管理集成

**任务**:
- [ ] 修改 `DocumentManagerSection` 组件
- [ ] 集成 ETL 触发逻辑
- [ ] 添加进度监控

**验收标准**:
- ✅ 上传文档 → 自动触发 ETL
- ✅ 实时显示任务进度
- ✅ 成功/失败状态正确显示

### Week 3: RAG 检索测试页面

**任务**:
- [ ] 创建 `RagTestPage.tsx`
- [ ] 开发 `RagQueryPanel` (查询面板)
- [ ] 开发 `RagResultCard` (结果展示)
- [ ] 使用 Reactflow 渲染电路拓扑图

**验收标准**:
- ✅ 可输入查询并获取结果
- ✅ BOM 表格、拓扑图正确渲染
- ✅ 相似度分数可视化

### Week 4: 测试与部署

**任务**:
- [ ] 端到端测试
- [ ] 性能优化
- [ ] 生产环境配置

---

## 📝 Java 后端配套开发

### 需要新增的 Controller

**文件**: `src/main/java/cn/yifan/drawsee/controller/DocumentIngestionController.java`

```java
@RestController
@RequestMapping("/api/rag")
public class DocumentIngestionController {

    @Autowired
    private PythonRagService pythonRagService;

    @PostMapping("/documents/ingest")
    public ResponseEntity<?> ingestDocument(@RequestBody DocumentIngestRequest request) {
        Map<String, Object> result = pythonRagService.ingestDocument(
            request.getDocumentId(),
            request.getKnowledgeBaseId(),
            request.getClassId(),
            request.getUserId(),
            request.getPdfPath()
        );
        return ResponseEntity.ok(result);
    }

    @GetMapping("/tasks/{taskId}")
    public ResponseEntity<?> getTaskStatus(@PathVariable String taskId) {
        return ResponseEntity.ok(pythonRagService.getTaskStatus(taskId));
    }
}
```

---

## 🎯 预期效果

### 优化前后对比

**之前**:
```
用户上传文档 → Java 存储 → 状态: UPLOADED → 结束
```

**之后**:
```
用户上传文档 → Java 存储 → 触发 Python ETL →
显示进度弹窗 (PARSING → CHUNKING → EMBEDDING → INDEXING) →
完成 (COMPLETED) → 可用于 RAG 检索
```

### 用户体验提升

✅ **实时反馈**: 进度条显示 0-100%
✅ **透明处理**: 当前步骤描述 ("正在生成向量 50/100")
✅ **错误可追溯**: 失败时显示详细错误原因
✅ **检索可测试**: 管理员可测试 RAG 检索效果

---

## 🔒 权限控制

| 功能 | 管理员 | 教师 | 学生 |
|------|--------|------|------|
| 上传文档（触发 ETL） | ✅ | ✅ | ❌ |
| 查看 ETL 任务进度 | ✅ | ✅ | ❌ |
| RAG 检索测试 | ✅ | ✅ | ✅ |
| 系统监控 | ✅ | ❌ | ❌ |

---

## 📦 部署配置

### 环境变量

```bash
# .env.development
VITE_JAVA_API_BASE_URL=http://localhost:6868
VITE_PYTHON_RAG_BASE_URL=http://localhost:8001

# .env.production
VITE_JAVA_API_BASE_URL=https://api.your-domain.com
VITE_PYTHON_RAG_BASE_URL=https://rag.your-domain.com
```

### Nginx 配置

```nginx
# Java 主服务
location /api/ {
    proxy_pass http://localhost:6868;
}

# Python RAG 服务
location /api/v1/rag/ {
    proxy_pass http://localhost:8001;
}
```

---

## 📚 参考资料

- [Python RAG API 文档](http://localhost:8001/docs)
- [Ant Design React](https://ant.design/)
- [Reactflow 电路图](https://reactflow.dev/)
- [Alova HTTP 客户端](https://alova.js.org/)

---

**创建时间**: 2025-12-10
**负责人**: 前端团队
**优先级**: P0 (高)

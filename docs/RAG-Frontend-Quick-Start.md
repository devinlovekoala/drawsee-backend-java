# RAG 前端集成快速开始指南

**快速实现第一个功能**: 文档上传触发 RAG ETL

---

## 🎯 目标

在现有的文档上传功能基础上，添加 Python RAG ETL 入库支持，让用户上传文档后自动进行智能解析和向量化。

---

## 📋 前置条件

### 后端服务状态检查

```bash
# 1. Java 主服务运行在 6868 端口
curl http://localhost:6868/api/health

# 2. Python RAG 服务运行在 8001 端口
curl http://localhost:8001/health
# 输出: {"status": "healthy"}

# 3. 检查 Java 配置
# application.yaml 中确保:
# drawsee.python-service.enabled: true
# drawsee.python-service.base-url: http://localhost:8001
```

### 前端项目准备

```bash
cd /home/devin/Workspace/drawsee-platform/drawsee-admin-web

# 安装依赖（如果未安装）
npm install

# 启动开发服务器
npm run dev
```

---

## 📝 实施步骤

### Step 1: 创建 RAG 服务 API 方法 (15 分钟)

**文件**: `src/api/methods/rag-service.methods.ts`

```typescript
import { alovaInstance } from '../index';

/**
 * 触发文档入库 ETL
 */
export const ingestDocument = (data: {
  document_id: string;
  knowledge_base_id: string;
  class_id: string;
  user_id: number;
  pdf_path: string;
}) => {
  return alovaInstance.Post('/api/rag/documents/ingest', data);
};

/**
 * 查询 ETL 任务状态
 */
export const getTaskStatus = (taskId: string) => {
  return alovaInstance.Get(`/api/rag/tasks/${taskId}`);
};
```

**文件**: `src/api/types/rag-service.types.ts`

```typescript
export interface DocumentIngestResponse {
  success: boolean;
  message: string;
  task_id: string;
  status: 'PENDING' | 'PROCESSING';
  estimated_time_seconds: number;
}

export interface TaskStatusResponse {
  success: boolean;
  task_id: string;
  status: 'PENDING' | 'PROCESSING' | 'SUCCESS' | 'FAILED';
  progress: number; // 0-100
  current_step: string;
  result?: {
    circuits_extracted: number;
    chunks_created: number;
    embeddings_generated: number;
  };
  error?: string;
}
```

---

### Step 2: 创建任务轮询 Hook (20 分钟)

**文件**: `src/hooks/useRagTask.ts`

```typescript
import { useState, useEffect, useRef } from 'react';
import { getTaskStatus } from '../api/methods/rag-service.methods';
import type { TaskStatusResponse } from '../api/types/rag-service.types';

export const useRagTask = (taskId: string, enabled: boolean = true) => {
  const [taskStatus, setTaskStatus] = useState<TaskStatusResponse | null>(null);
  const [isPolling, setIsPolling] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const intervalRef = useRef<NodeJS.Timeout | null>(null);

  useEffect(() => {
    if (!enabled || !taskId) {
      return;
    }

    const pollTaskStatus = async () => {
      try {
        setIsPolling(true);
        const response = await getTaskStatus(taskId);
        setTaskStatus(response);

        // 任务完成后停止轮询
        if (response.status === 'SUCCESS' || response.status === 'FAILED') {
          if (intervalRef.current) {
            clearInterval(intervalRef.current);
            intervalRef.current = null;
          }
          setIsPolling(false);
        }
      } catch (err: any) {
        setError(err.message || '查询任务状态失败');
        if (intervalRef.current) {
          clearInterval(intervalRef.current);
        }
        setIsPolling(false);
      }
    };

    // 立即查询一次
    pollTaskStatus();

    // 每 2 秒轮询一次
    intervalRef.current = setInterval(pollTaskStatus, 2000);

    return () => {
      if (intervalRef.current) {
        clearInterval(intervalRef.current);
      }
    };
  }, [taskId, enabled]);

  return { taskStatus, isPolling, error };
};
```

---

### Step 3: 创建简单的任务进度提示 (15 分钟)

**文件**: `src/components/knowledge-base/RagTaskToast.tsx`

```typescript
import React, { useEffect } from 'react';
import { useRagTask } from '../../hooks/useRagTask';
import { Toast } from '../Toast';

interface RagTaskToastProps {
  taskId: string;
  onSuccess?: () => void;
  onError?: (error: string) => void;
}

export const RagTaskToast: React.FC<RagTaskToastProps> = ({
  taskId,
  onSuccess,
  onError,
}) => {
  const { taskStatus, error } = useRagTask(taskId, !!taskId);

  useEffect(() => {
    if (taskStatus?.status === 'SUCCESS') {
      Toast.success(
        `文档 RAG 处理完成！提取了 ${taskStatus.result?.circuits_extracted || 0} 个电路`
      );
      onSuccess?.();
    } else if (taskStatus?.status === 'FAILED') {
      Toast.error(`文档处理失败: ${taskStatus.error || '未知错误'}`);
      onError?.(taskStatus.error || '未知错误');
    } else if (taskStatus?.status === 'PROCESSING') {
      // 可选：显示进度提示
      console.log(`处理中: ${taskStatus.current_step} (${taskStatus.progress}%)`);
    }
  }, [taskStatus]);

  useEffect(() => {
    if (error) {
      Toast.error(`查询任务状态失败: ${error}`);
      onError?.(error);
    }
  }, [error]);

  return null; // 这是一个逻辑组件，不渲染 UI
};
```

---

### Step 4: 修改文档管理组件 (20 分钟)

**文件**: `src/components/knowledge-base/DocumentManagerSection.tsx`

**修改要点**:

```typescript
// 1. 导入新 API
import { ingestDocument } from '../../api/methods/rag-service.methods';
import { RagTaskToast } from './RagTaskToast';

// 2. 添加状态
const [ragTaskId, setRagTaskId] = useState<string | null>(null);

// 3. 在文档上传成功回调中触发 ETL
const handleUploadSuccess = async (uploadedDocument: any) => {
  try {
    // 调用原有的刷新列表逻辑
    await refreshDocuments();

    // 触发 Python RAG ETL
    const response = await ingestDocument({
      document_id: uploadedDocument.id,
      knowledge_base_id: knowledgeBaseId,
      class_id: classId,
      user_id: Number(sessionStorage.getItem('Auth:UserId')),
      pdf_path: uploadedDocument.filePath, // 或 uploadedDocument.url
    });

    if (response.success) {
      setRagTaskId(response.task_id);
      Toast.info('文档已上传，正在后台处理...');
    }
  } catch (error: any) {
    console.error('触发 RAG ETL 失败:', error);
    Toast.error('触发智能解析失败，请稍后重试');
  }
};

// 4. 渲染任务监听组件
return (
  <div>
    {/* 原有的文档列表等 UI */}
    {/* ... */}

    {/* RAG 任务监听 */}
    {ragTaskId && (
      <RagTaskToast
        taskId={ragTaskId}
        onSuccess={() => {
          setRagTaskId(null);
          refreshDocuments(); // 刷新文档列表
        }}
        onError={() => {
          setRagTaskId(null);
        }}
      />
    )}
  </div>
);
```

---

### Step 5: Java 后端添加代理接口 (15 分钟)

**文件**: `src/main/java/cn/yifan/drawsee/controller/RagController.java` (新建)

```java
package cn.yifan.drawsee.controller;

import cn.yifan.drawsee.service.base.PythonRagService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/rag")
public class RagController {

    @Autowired
    private PythonRagService pythonRagService;

    /**
     * 触发文档 ETL 入库
     */
    @PostMapping("/documents/ingest")
    public ResponseEntity<?> ingestDocument(@RequestBody Map<String, Object> request) {
        String documentId = (String) request.get("document_id");
        String knowledgeBaseId = (String) request.get("knowledge_base_id");
        String classId = (String) request.get("class_id");
        Integer userId = (Integer) request.get("user_id");
        String pdfPath = (String) request.get("pdf_path");

        log.info("触发文档 ETL: documentId={}, knowledgeBaseId={}", documentId, knowledgeBaseId);

        if (!pythonRagService.isServiceAvailable()) {
            Map<String, Object> error = new HashMap<>();
            error.put("success", false);
            error.put("message", "Python RAG 服务暂不可用");
            return ResponseEntity.status(503).body(error);
        }

        try {
            Map<String, Object> result = pythonRagService.ingestDocument(
                documentId,
                knowledgeBaseId,
                classId,
                userId.longValue(),
                pdfPath
            );
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            log.error("文档入库失败", e);
            Map<String, Object> error = new HashMap<>();
            error.put("success", false);
            error.put("message", "文档入库失败: " + e.getMessage());
            return ResponseEntity.status(500).body(error);
        }
    }

    /**
     * 查询 ETL 任务状态
     */
    @GetMapping("/tasks/{taskId}")
    public ResponseEntity<?> getTaskStatus(@PathVariable String taskId) {
        log.info("查询任务状态: taskId={}", taskId);

        try {
            Map<String, Object> status = pythonRagService.getTaskStatus(taskId);
            if (status == null) {
                return ResponseEntity.notFound().build();
            }
            return ResponseEntity.ok(status);
        } catch (Exception e) {
            log.error("查询任务状态失败", e);
            return ResponseEntity.status(500).body(Map.of(
                "success", false,
                "message", "查询任务状态失败: " + e.getMessage()
            ));
        }
    }
}
```

---

## ✅ 测试验证

### 1. 启动所有服务

```bash
# 终端 1: 启动 Java 服务
cd /home/devin/Workspace/drawsee-platform/drawsee-java
mvn spring-boot:run

# 终端 2: 启动 Python RAG 服务
cd /home/devin/Workspace/python/drawsee-rag-python
source .venv/bin/activate
uvicorn app.main:app --host 0.0.0.0 --port 8001 --reload

# 终端 3: 启动前端
cd /home/devin/Workspace/drawsee-platform/drawsee-admin-web
npm run dev
```

### 2. 功能测试流程

1. **登录系统**
   - 访问 http://localhost:5173
   - 使用教师或管理员账号登录

2. **进入知识库**
   - 导航到 "知识库管理"
   - 选择或创建一个知识库
   - 进入知识库详情页

3. **上传文档**
   - 点击 "上传文档" 按钮
   - 选择一个 PDF 电路图文档
   - 上传成功后观察右上角通知

4. **观察处理进度**
   - 上传后应显示 "文档已上传，正在后台处理..."
   - 打开浏览器控制台，观察轮询日志
   - 2-5 分钟后应显示 "文档 RAG 处理完成！"

5. **检查后端日志**
   ```bash
   # Java 日志
   # 应显示: 触发文档 ETL: documentId=xxx, knowledgeBaseId=xxx

   # Python 日志
   # 应显示: 文档入库任务提交成功: task_id=xxx
   ```

### 3. 常见问题排查

**问题 1**: "Python RAG 服务暂不可用"
```bash
# 检查 Python 服务是否运行
curl http://localhost:8001/health

# 检查 Java 配置
grep -A 3 "python-service" src/main/resources/application.yaml
```

**问题 2**: "触发智能解析失败"
```bash
# 检查 Java 后端日志
tail -f logs/drawsee.log | grep RAG

# 检查前端网络请求
# 浏览器 DevTools → Network → 查看 /api/rag/documents/ingest 请求
```

**问题 3**: 任务一直 PENDING
```bash
# 检查 Celery worker 是否运行
cd /home/devin/Workspace/python/drawsee-rag-python
celery -A app.celery_config worker --loglevel=info

# 检查 Redis 是否运行
redis-cli ping
```

---

## 🎯 预期效果

### 成功流程

```
用户上传 PDF
    ↓
前端显示: "文档已上传，正在后台处理..."
    ↓
后台开始 ETL 处理
    ↓
前端轮询任务状态 (每 2 秒)
    ↓
控制台显示进度: "正在解析 PDF..." (33%)
控制台显示进度: "正在生成向量..." (66%)
    ↓
2-5 分钟后
    ↓
前端显示: "文档 RAG 处理完成！提取了 3 个电路"
    ↓
文档列表自动刷新
```

### 用户体验

✅ **上传即触发**: 无需额外操作
✅ **后台处理**: 不阻塞用户继续使用系统
✅ **实时反馈**: Toast 通知处理进度
✅ **自动完成**: 处理完成后自动刷新列表

---

## 📊 下一步优化

完成基础功能后，可以逐步添加：

1. **进度条可视化**: 用 Ant Design Progress 组件替代 Toast
2. **任务列表查看**: 显示所有正在处理的文档
3. **失败重试**: 添加重新触发 ETL 的按钮
4. **RAG 检索测试**: 新增页面测试检索效果

---

## 📚 相关文档

- [完整集成方案](./RAG-Frontend-Integration-Plan.md)
- [PythonRagService 使用分析](./PythonRagService-Usage-Analysis.md)
- [Python RAG API 文档](http://localhost:8001/docs)

---

**预计实施时间**: 1.5 小时
**难度**: ⭐⭐ (中等)
**优先级**: P0 (高)

# RAG 文档上传修复后重启指南

**问题**: 添加了 commons-io 依赖但仍然报错
**原因**: 后端服务没有重启，新依赖未加载

---

## 🚨 重要提醒

**Maven 依赖修改后，必须重启应用才能生效！**

- ❌ 只是 `mvn compile` 不够
- ❌ 热重载不会重新加载依赖
- ✅ **必须完全重启应用**

---

## ✅ 正确的重启步骤

### 步骤 1: 停止当前运行的后端

#### 方法 A: 使用 Ctrl+C

如果后端在终端中运行：
```bash
# 按 Ctrl+C 停止
```

#### 方法 B: 查找并杀死进程

```bash
# 查找 Spring Boot 进程
ps aux | grep drawsee-java

# 杀死进程（替换 <PID> 为实际进程 ID）
kill -9 <PID>

# 或者一键杀死所有 Java 进程（谨慎使用）
pkill -f "drawsee-java"
```

### 步骤 2: 清理并重新构建

```bash
cd /home/devin/Workspace/drawsee-platform/drawsee-java

# 清理旧的构建产物
mvn clean

# 重新编译（跳过测试以加快速度）
mvn compile -DskipTests
```

**预期输出**:
```
[INFO] BUILD SUCCESS
```

### 步骤 3: 验证依赖已下载

```bash
# 检查 commons-io 是否在依赖树中
mvn dependency:tree | grep commons-io
```

**预期输出**:
```
[INFO] +- commons-io:commons-io:jar:2.15.1:compile
```

### 步骤 4: 启动后端服务

```bash
mvn spring-boot:run
```

**预期输出**:
```
  .   ____          _            __ _ _
 /\\ / ___'_ __ _ _(_)_ __  __ _ \ \ \ \
( ( )\___ | '_ | '_| | '_ \/ _` | \ \ \ \
 \\/  ___)| |_)| | | | | || (_| |  ) ) ) )
  '  |____| .__|_| |_|_| |_\__, | / / / /
 =========|_|==============|___/=/_/_/_/
 :: Spring Boot ::               (v3.4.3)

INFO  [...] Started DrawseeApplication in X.XXX seconds
```

---

## 🧪 验证修复

### 1. 检查后端日志

启动后查看日志，**不应该再有** `NoClassDefFoundError`。

### 2. 测试 PDF 上传

1. 访问知识库管理页面
2. 选择一个知识库
3. 上传 PDF 文档
4. 观察前端控制台

**预期日志**:
```javascript
[RAG ETL] 触发文档入库: {documentId: "xxx", ...}
[RAG ETL] 任务已提交: task_id_xxx
```

**预期 Toast 提示**:
```
✅ 文档已上传，正在后台进行智能解析...
```

### 3. 观察后端日志

**修复前（错误）**:
```
ERROR [...] NoClassDefFoundError: org/apache/commons/io/input/ChecksumInputStream
```

**修复后（正常）**:
```
INFO [...] Document parsing started: document_id=xxx
INFO [...] Text extracted: 10 pages
INFO [...] Chunks created: 50 chunks
INFO [...] Document ingestion completed successfully
```

---

## 🐛 问题分析

### 为什么前端显示 503 但响应体是 200？

这是 GlobalResponseHandler 的问题：

```java
// GlobalResponseHandler.java
public Object beforeBodyWrite(Object body, ...) {
    // ...
    return Result.success(body);  // 即使是错误，也被包装成 success
}
```

**实际流程**:
1. DocumentIngestionService 抛出异常
2. 异常被异步处理器捕获（打印到日志）
3. 但 HTTP 响应已经返回（可能是 503）
4. GlobalResponseHandler 包装响应为 `Result.success(503)`
5. 前端收到 `{code: 200, message: "success", data: {...}}`

### 前端响应拦截器的问题

```typescript
// index.ts:30-31
if (response.status !== 200) {
    throw new Error(json.message || '请求失败');
}
```

当 HTTP 状态码是 503 时，直接抛出错误，但错误消息是 `json.message`（"success"），导致前端显示 `Error: success`。

### Toast.warn 不存在

```javascript
// DocumentManagerSection.tsx:155
Toast.warn('文档已上传，但智能解析启动失败，请联系管理员');
```

**问题**: `Toast` 组件可能没有 `warn` 方法，应该用 `Toast.warning` 或 `Toast.error`。

---

## 🔧 建议的改进（可选）

### 1. 修复 Toast.warn 调用

```typescript
// DocumentManagerSection.tsx
// 修改前
Toast.warn('文档已上传，但智能解析启动失败，请联系管理员');

// 修改后
Toast.warning('文档已上传，但智能解析启动失败，请联系管理员');
// 或者
Toast.error('文档已上传，但智能解析启动失败，请联系管理员');
```

### 2. 改进前端错误处理

```typescript
// index.ts
responded: async (response) => {
    const json = await response.json();

    console.log('[Alova响应拦截器] 状态码:', response.status, '响应体:', json);

    // 修改前
    if (response.status !== 200) {
        throw new Error(json.message || '请求失败');
    }

    // 修改后（更好的错误提示）
    if (response.status !== 200) {
        const errorMsg = json.message || `请求失败 (${response.status})`;
        console.error('[Alova响应拦截器] HTTP 错误:', response.status, errorMsg);
        throw new Error(errorMsg);
    }

    // ...
}
```

### 3. 改进异步错误处理（Java 后端）

当前问题是异步任务的异常不会反馈给前端。建议：

**方案 A**: 使用 CompletableFuture 返回结果
```java
@Async
public CompletableFuture<Void> ingestAsync(String documentId) {
    try {
        // 处理逻辑
        return CompletableFuture.completedFuture(null);
    } catch (Exception e) {
        return CompletableFuture.failedFuture(e);
    }
}
```

**方案 B**: 使用任务状态表记录错误
```java
@Async
public void ingestAsync(String documentId) {
    try {
        // 处理逻辑
        updateTaskStatus(documentId, "SUCCESS");
    } catch (Exception e) {
        log.error("文档入库失败", e);
        updateTaskStatus(documentId, "FAILED", e.getMessage());
    }
}
```

---

## ⚠️ 常见错误

### 错误 1: 只执行了 `mvn compile`

```bash
# ❌ 错误：只编译不重启
mvn compile
# 后端服务继续运行，依赖未加载
```

**正确做法**:
```bash
# ✅ 正确：停止 → 编译 → 重启
Ctrl+C  # 停止
mvn clean compile
mvn spring-boot:run  # 重启
```

### 错误 2: 使用 IDE 的热重载

```
❌ IntelliJ IDEA 的 "Update classes and resources"
❌ Spring Boot DevTools 的热重载
```

**原因**: 热重载不会重新加载 classpath 中的新依赖。

**正确做法**: 完全重启应用。

### 错误 3: 忘记执行 `mvn clean`

```bash
# ❌ 可能有缓存的旧文件
mvn compile
```

**正确做法**:
```bash
# ✅ 清理后重新构建
mvn clean compile
```

---

## 📋 快速检查清单

在测试之前，请确认：

- [ ] 已停止旧的后端服务（没有 Java 进程在运行）
- [ ] 已执行 `mvn clean compile -DskipTests`
- [ ] 编译成功（BUILD SUCCESS）
- [ ] `mvn dependency:tree | grep commons-io` 显示依赖存在
- [ ] 已启动新的后端服务 `mvn spring-boot:run`
- [ ] 启动日志显示 "Started DrawseeApplication"
- [ ] 没有 ClassNotFoundException 或 NoClassDefFoundError

---

## 💡 记忆技巧

**Maven 依赖三步曲**:
1. 📝 修改 pom.xml
2. 🛠️ `mvn clean compile`
3. 🔄 **重启应用**

**记住**: pom.xml 的修改不是"热更新"的！

---

## 🆘 如果问题仍然存在

### 检查依赖冲突

```bash
mvn dependency:tree -Dverbose | grep commons-io
```

### 强制重新下载

```bash
rm -rf ~/.m2/repository/commons-io/
mvn clean install -U -DskipTests
```

### 检查类路径

```bash
mvn dependency:build-classpath | grep commons-io
```

### 验证 JAR 包完整性

```bash
ls -lh ~/.m2/repository/commons-io/commons-io/2.15.1/
# 应该看到 commons-io-2.15.1.jar (约 500KB)

# 检查 JAR 包内容
jar tf ~/.m2/repository/commons-io/commons-io/2.15.1/commons-io-2.15.1.jar | grep ChecksumInputStream
# 应该看到: org/apache/commons/io/input/ChecksumInputStream.class
```

---

## ✅ 最终验证

成功后，你应该看到：

**前端**:
1. ✅ 上传 PDF 成功
2. ✅ Toast: "文档已上传，正在后台进行智能解析..."
3. ✅ 几秒后 Toast: "文档 RAG 处理完成！提取了 N 个电路"

**后端日志**:
```
INFO  [...] Document parsing started
INFO  [...] Text extracted successfully
INFO  [...] Chunks created
INFO  [...] Document ingestion completed
```

**数据库**（可选验证）:
```sql
-- 检查文档是否入库
SELECT * FROM knowledge_document WHERE id = 'xxx';

-- 检查是否有向量数据（如果有相关表）
```

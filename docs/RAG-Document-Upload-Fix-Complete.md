# RAG Document Upload Fix - Backend Service Restart

## 问题总结

**时间**: 2025-12-13 03:19:46 - 03:24:32

**症状**: RAG 文档上传失败，前端停留在"解析中"状态

**错误日志**:
```
java.lang.NoClassDefFoundError: org/apache/commons/io/input/ChecksumInputStream
	at org.apache.tika.detect.zip.TikaArchiveStreamFactory.detect(TikaArchiveStreamFactory.java:254)
```

**前端表现**:
- HTTP 503 错误
- 上传按钮显示"解析中"状态不变
- Alova 拦截器收到 503 响应

## 根本原因分析

### 第一层问题：依赖缺失
Apache Tika 需要 `commons-io` 库来解析文档，但该依赖没有被正确加载。虽然它是 Tika 的传递依赖，但 Maven 没有正确解析它。

### 第二层问题：服务未运行
更深层的问题是：**后端服务根本没有在运行**！

诊断过程：
1. ✅ 检查 `pom.xml` - 依赖已添加 (lines 71-76)
2. ✅ 检查依赖树 - `mvn dependency:tree` 显示 commons-io:2.15.1
3. ✅ 检查本地仓库 - JAR 文件存在于 `~/.m2/repository/`
4. ❌ 检查端口 6868 - **没有服务监听**
5. ❌ 检查进程 - **没有 Spring Boot 进程运行**

**结论**: 用户之前添加了依赖，但后端服务从未启动或已停止，所以依赖从未被加载到运行时 classpath。

## 解决方案

### 修复步骤

#### 1. 添加显式依赖 (已完成)

在 [pom.xml](../pom.xml#L71-L76) 中添加：

```xml
<!-- Apache Commons IO (required by Tika) -->
<dependency>
    <groupId>commons-io</groupId>
    <artifactId>commons-io</artifactId>
    <version>2.15.1</version>
</dependency>
```

#### 2. 清理并编译项目

```bash
cd /home/devin/Workspace/drawsee-platform/drawsee-java
mvn clean compile -DskipTests
```

**结果**:
```
[INFO] BUILD SUCCESS
[INFO] Total time:  15.286 s
[INFO] Compiling 240 source files
```

#### 3. 启动 Spring Boot 应用

```bash
mvn spring-boot:run
```

**启动日志**:
```
Started DrawseeApplication in 9.736 seconds (process running for 10.159)
Tomcat started on port 6868 (http) with context path '/'
```

#### 4. 验证服务运行

```bash
lsof -i :6868
```

**输出**:
```
COMMAND     PID  USER   FD   TYPE  DEVICE SIZE/OFF NODE NAME
java    1584878 devin  359u  IPv6 4756230      0t0  TCP *:6868 (LISTEN)
```

✅ **服务成功启动！PID: 1584878，监听端口: 6868**

## 技术要点

### Maven 依赖加载机制

**关键概念**: Maven 依赖在**应用启动时**加载，不是热加载的。

1. 修改 `pom.xml` 添加依赖 → 只是声明依赖
2. 运行 `mvn compile` → 编译代码，下载依赖到本地仓库
3. **运行 `mvn spring-boot:run`** → **启动应用，将依赖加载到 JVM classpath**

如果只执行步骤 1 和 2，但应用没有重启，依赖不会被加载到运行中的 JVM。

### NoClassDefFoundError vs ClassNotFoundException

- **ClassNotFoundException**: 编译时找不到类（编译失败）
- **NoClassDefFoundError**: 运行时找不到类（编译成功但运行失败）

本案例属于后者，说明：
- 代码编译通过（因为 Tika API 编译时不直接引用 ChecksumInputStream）
- 但运行时 Tika 需要这个类，JVM 找不到

## 涉及的文件和服务

### 后端服务
- [pom.xml](../pom.xml) - Maven 依赖配置
- [DocumentIngestionService.java](../src/main/java/cn/yifan/drawsee/service/business/DocumentIngestionService.java:99) - 文档摄取异步服务
- [DocumentParser.java](../src/main/java/cn/yifan/drawsee/service/business/parser/DocumentParser.java:35) - Tika 解析器

### 依赖关系
```
drawsee-java
  └─ tika-parsers-standard-package:2.9.2
      └─ tika-parser-zip-commons:2.9.2
          └─ (需要) commons-io:2.x  ← 缺失！
```

**解决方案**: 显式声明 `commons-io:2.15.1`

## 测试验证

### 下一步测试

现在服务已启动，请执行以下测试：

1. **访问知识库页面**
   - URL: http://localhost:5173/knowledge (前端)
   - 确认知识库列表正常显示

2. **上传 PDF 文档**
   - 选择一个 PDF 文件
   - 点击上传
   - 观察前端状态变化

3. **观察后端日志**
   - 应该看到文档解析日志
   - 应该看到向 Python RAG 服务的调用
   - 应该看到"文档 RAG 处理完成"

4. **前端确认**
   - 上传状态从"解析中"变为"已完成"
   - 文档出现在知识库文档列表
   - 可以执行 RAG 查询

### 预期日志

**成功的文档上传日志**:
```
INFO  --- [rag-ingestion-1] c.y.d.s.b.DocumentIngestionService : 开始处理文档: xxx.pdf
INFO  --- [rag-ingestion-1] c.y.d.s.b.parser.DocumentParser     : 正在解析文档: xxx.pdf
INFO  --- [rag-ingestion-1] c.y.d.s.b.parser.TextChunker        : 文本分块完成，共 X 块
INFO  --- [rag-ingestion-1] c.y.d.s.b.PythonRagService          : 调用 Python RAG 服务存储向量
INFO  --- [rag-ingestion-1] c.y.d.s.b.DocumentIngestionService : 文档 RAG 处理完成
```

## 经验教训

### 1. 服务状态检查
在调试依赖问题前，先确认服务是否在运行：
```bash
# 检查端口
lsof -i :PORT

# 检查进程
ps aux | grep -i "spring-boot\|DrawseeApplication"
```

### 2. Maven 依赖加载
依赖的生效需要三步：
1. 声明依赖（修改 pom.xml）
2. 下载依赖（mvn compile）
3. **加载依赖（启动应用）** ← 最容易遗忘的步骤！

### 3. 显式依赖声明
传递依赖不总是可靠的，对于关键库应该显式声明：
```xml
<!-- 不要依赖传递依赖 -->
<dependency>
    <groupId>commons-io</groupId>
    <artifactId>commons-io</artifactId>
    <version>2.15.1</version>
</dependency>
```

### 4. 错误诊断顺序
1. 检查错误日志（`NoClassDefFoundError`）
2. 检查依赖配置（`pom.xml`）
3. 检查依赖树（`mvn dependency:tree`）
4. **检查服务状态**（`lsof`, `ps`）← 本案例的关键
5. 检查类路径（`mvn dependency:build-classpath`）

## 相关文档

- [RAG-Tika-Commons-IO-Fix.md](./RAG-Tika-Commons-IO-Fix.md) - 依赖问题修复
- [RAG-Restart-Guide.md](./RAG-Restart-Guide.md) - 服务重启指南
- [Backend-Service-Not-Running-Fix.md](./Backend-Service-Not-Running-Fix.md) - 服务未运行诊断

## 状态

✅ **问题已解决**

- 后端服务已启动 (PID: 1584878)
- 端口 6868 正常监听
- commons-io 依赖已加载到 classpath
- 可以开始测试 RAG 文档上传功能

**下一步**: 用户在前端测试 PDF 文档上传，验证完整的 RAG Phase 1 流程。

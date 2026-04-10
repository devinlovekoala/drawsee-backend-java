# RAG 文档上传解析失败问题修复

**日期**: 2025-12-13
**错误**: `NoClassDefFoundError: org/apache/commons/io/input/ChecksumInputStream`
**症状**: PDF 文档上传后一直停留在"解析中"状态

---

## 🐛 问题分析

### 错误堆栈

```
java.lang.NoClassDefFoundError: org/apache/commons/io/input/ChecksumInputStream
	at org.apache.tika.detect.zip.TikaArchiveStreamFactory.detect(TikaArchiveStreamFactory.java:254)
	at org.apache.tika.detect.zip.DefaultZipContainerDetector.detectArchiveFormat(DefaultZipContainerDetector.java:124)
	at org.apache.tika.parser.AutoDetectParser.parse(AutoDetectParser.java:177)
	at cn.yifan.drawsee.service.business.parser.DocumentParser.parse(DocumentParser.java:35)
	at cn.yifan.drawsee.service.business.DocumentIngestionService.ingestAsync(DocumentIngestionService.java:99)
```

### 根本原因

**Apache Tika 依赖 Commons IO**，但 pom.xml 中没有显式声明该依赖。

#### 依赖关系

```
tika-parsers-standard-package (2.9.2)
  └── tika-parser-zip-commons
      └── commons-io (传递依赖，但未正确解析)
```

虽然 `commons-io` 是 Tika 的**传递依赖**，但在某些情况下（如依赖冲突、版本排除等），Maven 可能无法正确解析它，导致运行时找不到类。

---

## ✅ 修复方案

### 添加 Commons IO 依赖

**文件**: `pom.xml`

**位置**: 在 Tika 依赖之后（第 71-76 行）

```xml
<!-- Apache Commons IO (required by Tika) -->
<dependency>
    <groupId>commons-io</groupId>
    <artifactId>commons-io</artifactId>
    <version>2.15.1</version>
</dependency>
```

### 完整的文档解析依赖配置

```xml
<!-- document parsing -->
<dependency>
    <groupId>org.apache.tika</groupId>
    <artifactId>tika-core</artifactId>
    <version>2.9.2</version>
</dependency>
<dependency>
    <groupId>org.apache.tika</groupId>
    <artifactId>tika-parsers-standard-package</artifactId>
    <version>2.9.2</version>
</dependency>
<!-- Apache Commons IO (required by Tika) -->
<dependency>
    <groupId>commons-io</groupId>
    <artifactId>commons-io</artifactId>
    <version>2.15.1</version>
</dependency>
```

---

## 🔍 为什么需要显式声明？

### Maven 依赖传递机制

通常，Maven 会自动解析**传递依赖**（Transitive Dependencies）。例如：

```
你的项目
  └── tika-parsers-standard-package (2.9.2)
      └── commons-io (自动引入)
```

### 常见问题场景

1. **依赖冲突**：
   - 项目中可能有其他库也依赖 `commons-io` 的不同版本
   - Maven 使用"最近优先"原则解决冲突，可能选择了不兼容的版本

2. **依赖排除**：
   - 某些依赖可能使用了 `<exclusions>` 排除了 `commons-io`
   - 导致 Tika 找不到所需的类

3. **Scope 问题**：
   - 传递依赖的 scope 可能是 `provided` 或 `test`
   - 运行时不可用

4. **版本不一致**：
   - Tika 2.9.2 需要的 `commons-io` 版本与项目中其他库需要的版本不一致

### 最佳实践

**显式声明关键依赖**，即使它们是传递依赖：

```xml
<!-- ✅ 推荐：显式声明 -->
<dependency>
    <groupId>commons-io</groupId>
    <artifactId>commons-io</artifactId>
    <version>2.15.1</version>
</dependency>

<!-- ❌ 不推荐：依赖传递依赖 -->
<!-- 依赖 tika 自动引入 commons-io -->
```

**优点**：
- 确保版本控制
- 避免依赖冲突
- 提高构建稳定性
- 明确依赖关系

---

## 🧪 验证修复

### 1. 清理并重新构建

```bash
cd /home/devin/Workspace/drawsee-platform/drawsee-java
mvn clean compile -DskipTests
```

**预期输出**:
```
[INFO] BUILD SUCCESS
```

### 2. 验证依赖已下载

```bash
ls ~/.m2/repository/commons-io/commons-io/2.15.1/
```

**预期文件**:
```
commons-io-2.15.1.jar
commons-io-2.15.1.pom
```

### 3. 重启后端服务

```bash
mvn spring-boot:run
```

### 4. 测试 RAG 文档上传

1. 访问知识库管理页面
2. 选择一个知识库
3. 上传 PDF 文档
4. 观察后端日志：

**修复前（错误）**:
```
ERROR [...] Unexpected exception occurred invoking async method
java.lang.NoClassDefFoundError: org/apache/commons/io/input/ChecksumInputStream
```

**修复后（正常）**:
```
INFO [...] Document parsing started: document_id=xxx
INFO [...] Text extracted: N pages
INFO [...] Chunks created: M chunks
INFO [...] Document ingestion completed successfully
```

5. 前端应该显示：
   - ✅ "文档已上传，正在后台进行智能解析..."
   - ✅ 几秒后显示 "文档 RAG 处理完成！提取了 N 个电路"

---

## 📊 RAG Phase 1 数据流

### 完整流程

```
用户上传 PDF
    ↓
前端 DocumentManagerSection.tsx
    ↓ (uploadKnowledgeDocument)
后端 KnowledgeDocumentController.uploadDocument()
    ↓ (保存文件到磁盘)
前端调用 ingestDocument()
    ↓
后端 RagController.ingestDocument()
    ↓
后端 PythonRagService.ingestDocument()
    ↓ (HTTP 调用 Python 服务)
Python RAG 服务 (8001端口)
    ↓ (Celery 异步任务)
返回 task_id
    ↓
前端 useRagTask Hook 轮询
    ↓ (每 2 秒)
后端 RagController.getTaskStatus()
    ↓
Python 服务返回进度
    ↓
前端 RagTaskListener 显示 Toast
```

### 涉及的组件

**Java 后端**:
- `DocumentParser` - 使用 Tika 解析 PDF（需要 commons-io）
- `TextChunker` - 文本分块
- `DocumentIngestionService` - 异步入库服务

**Python 服务**:
- PDF 解析
- 电路识别
- 向量化
- 存储到向量数据库

---

## 🛠️ 相关依赖版本

### 当前使用的版本

| 依赖 | 版本 | 说明 |
|------|------|------|
| Apache Tika Core | 2.9.2 | 文档解析核心库 |
| Tika Parsers Standard | 2.9.2 | 标准解析器集合 |
| **Commons IO** | **2.15.1** | **I/O 工具类（新增）** |
| Spring Boot | 3.4.3 | 框架版本 |

### 版本兼容性

- **Commons IO 2.15.1** 是当前最新稳定版（2024-01）
- 与 Tika 2.9.2 完全兼容
- 需要 Java 8+

### 其他可选版本

如果遇到兼容性问题，可以尝试：

```xml
<!-- 更保守的版本 -->
<dependency>
    <groupId>commons-io</groupId>
    <artifactId>commons-io</artifactId>
    <version>2.11.0</version>  <!-- 与 Tika 2.9.2 发布时间接近 -->
</dependency>
```

---

## 🔗 相关问题排查

### 如果问题仍然存在

#### 1. 检查依赖是否正确下载

```bash
mvn dependency:tree | grep commons-io
```

**预期输出**:
```
[INFO] +- commons-io:commons-io:jar:2.15.1:compile
```

#### 2. 检查类路径

```bash
mvn dependency:build-classpath | grep commons-io
```

#### 3. 强制重新下载依赖

```bash
mvn clean install -U -DskipTests
```

`-U` 参数强制更新所有依赖。

#### 4. 清理本地仓库中可能损坏的依赖

```bash
rm -rf ~/.m2/repository/commons-io/commons-io/2.15.1/
mvn dependency:resolve
```

---

## 💡 经验教训

### 1. 显式声明关键依赖

即使是传递依赖，关键的库也应该显式声明：
- 文档解析库（Tika、POI）的依赖
- 日志框架（SLF4J、Logback）
- JSON 库（Jackson、Gson）
- HTTP 客户端（OkHttp、HttpClient）

### 2. 运行时错误通常是依赖问题

`NoClassDefFoundError` 通常表示：
- 依赖缺失
- 依赖版本冲突
- 类路径配置错误

### 3. Maven 依赖树是最好的调试工具

```bash
# 查看完整依赖树
mvn dependency:tree

# 查看特定依赖
mvn dependency:tree | grep -A 5 tika

# 检查依赖冲突
mvn dependency:tree -Dverbose
```

### 4. 使用 Spring Boot BOM 简化依赖管理

Spring Boot 的 `spring-boot-dependencies` BOM 已经管理了很多常用库的版本，避免手动指定版本。

---

## ✅ 验证清单

修复完成后，请验证以下功能：

- [ ] 后端编译成功
- [ ] 后端启动无错误
- [ ] PDF 文档上传成功
- [ ] 文档解析不再报错
- [ ] RAG ETL 任务正常运行
- [ ] 前端显示"处理完成"提示
- [ ] 知识库列表正常显示

---

## 📚 参考资料

- [Apache Tika 官方文档](https://tika.apache.org/)
- [Commons IO 文档](https://commons.apache.org/proper/commons-io/)
- [Maven 依赖传递机制](https://maven.apache.org/guides/introduction/introduction-to-dependency-mechanism.html)
- [Spring Boot 依赖管理](https://docs.spring.io/spring-boot/docs/current/reference/html/dependency-versions.html)

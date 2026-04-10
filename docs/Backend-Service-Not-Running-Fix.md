# Backend Service Not Running - Fix Guide

## 问题诊断

**时间**: 2025-12-13 03:19:46

**错误**: `java.lang.NoClassDefFoundError: org/apache/commons/io/input/ChecksumInputStream`

## 根本原因

经过诊断，发现后端服务**根本没有在运行**：

1. ✅ `commons-io` 依赖已经添加到 `pom.xml` (line 71-76)
2. ✅ Maven 依赖树显示 `commons-io:2.15.1:compile`
3. ✅ JAR 文件已下载到 `~/.m2/repository/commons-io/commons-io/2.15.1/commons-io-2.15.1.jar`
4. ❌ 但是端口 6868 没有任何服务监听
5. ❌ 没有 Spring Boot 应用进程在运行

**结论**: 后端服务需要启动才能使用新添加的依赖！

## 解决方案

### 步骤 1: 清理并编译项目

```bash
cd /home/devin/Workspace/drawsee-platform/drawsee-java
mvn clean compile -DskipTests
```

### 步骤 2: 启动 Spring Boot 应用

```bash
mvn spring-boot:run
```

或者如果使用 Maven wrapper:

```bash
./mvnw spring-boot:run
```

### 步骤 3: 验证服务启动

检查日志中是否有:
```
Started DrawseeApplication in X.XXX seconds
```

检查端口是否监听:
```bash
lsof -i :6868
# 或
netstat -tlnp | grep 6868
```

### 步骤 4: 测试 RAG 文档上传

1. 前往前端知识库页面
2. 上传 PDF 文档
3. 观察后端日志，应该看到文档解析成功
4. 前端应该显示"文档 RAG 处理完成"

## 为什么之前会失败？

Maven 依赖是在**应用启动时**加载到 classpath 的，不是动态加载的。即使你修改了 `pom.xml` 并运行了 `mvn clean compile`，如果应用没有重启，它仍然使用旧的 classpath，不包含新添加的 `commons-io` 依赖。

## 相关文件

- [pom.xml](../pom.xml#L71-L76) - commons-io 依赖配置
- [DocumentIngestionService.java](../src/main/java/cn/yifan/drawsee/service/business/DocumentIngestionService.java) - 文档摄取服务
- [DocumentParser.java](../src/main/java/cn/yifan/drawsee/service/business/parser/DocumentParser.java) - Tika 文档解析器

## 后续步骤

启动服务后，测试完整的 RAG Phase 1 流程：

1. ✅ 创建知识库
2. ✅ 上传 PDF 文档
3. ✅ 文档解析 (Tika)
4. ✅ 文本分块 (TextChunker)
5. ✅ 向量存储 (Python RAG Service)
6. ✅ RAG 查询测试

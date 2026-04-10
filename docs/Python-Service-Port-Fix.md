# Python RAG 服务端口配置错误修复

## 问题发现

**时间**: 2025-12-13 03:28

**症状**:
1. RAG 文档上传失败
2. 后端日志显示：`Python服务健康检查失败: 连接被拒绝`
3. 仍然出现 `NoClassDefFoundError: org/apache/commons/io/input/ChecksumInputStream`

## 根本原因

用户报告发现：**Python RAG 服务运行在 FastAPI 默认端口 8000，而 Java 后端配置的是 8001！**

### 服务端口不匹配

- **Python RAG 服务实际监听**: `http://localhost:8000`
- **Java 后端配置**: `http://localhost:8001`
- **结果**: Java 无法连接到 Python 服务，所有 RAG 请求失败

### 两个问题同时存在

虽然解决了 commons-io 依赖问题，但由于 Python 服务端口配置错误，文档上传仍然失败：

1. ❌ **端口配置错误** - Java 连接 8001，Python 监听 8000
2. ❌ **NoClassDefFoundError** - 这个错误仍然存在（需要进一步排查）

## 解决方案

### 修复端口配置

修改 [application.yaml](../src/main/resources/application.yaml#L69)：

```yaml
# 修改前
python-service:
  base-url: ${PYTHON_SERVICE_URL:http://localhost:8001}  # ❌ 错误端口

# 修改后
python-service:
  base-url: ${PYTHON_SERVICE_URL:http://localhost:8000}  # ✅ FastAPI 默认端口
  timeout: 60000
  enabled: ${PYTHON_SERVICE_ENABLED:true}
```

### 重启服务

```bash
# 1. 停止旧服务
pkill -f "spring-boot:run"

# 2. 重新启动
mvn spring-boot:run
```

**启动结果**:
```
Started DrawseeApplication in 8.634 seconds (process running for 8.813)
Tomcat started on port 6868 (http) with context path '/'
```

## 验证步骤

### 1. 检查 Python 服务端口

```bash
# 查看 Python 服务监听端口
lsof -i :8000

# 应该看到 uvicorn 进程
```

### 2. 测试健康检查

```bash
# 从 Java 后端测试 Python 服务连接
curl http://localhost:8000/api/v1/rag/health

# 预期响应
{
  "status": "healthy",
  "version": "2.0.0"
}
```

### 3. 测试文档上传

1. 前往知识库管理页面
2. 上传 PDF 文档
3. 观察后端日志应该显示：
   ```
   INFO  --- Python服务健康检查成功
   INFO  --- 开始处理文档: xxx.pdf
   INFO  --- 正在解析文档
   ```

## 仍需解决的问题

### NoClassDefFoundError 仍然存在

尽管我们：
1. ✅ 添加了 `commons-io:2.15.1` 到 pom.xml
2. ✅ 运行了 `mvn clean compile`
3. ✅ 重启了服务
4. ✅ 验证了 JAR 文件存在于 Maven 本地仓库

但 `NoClassDefFoundError: org/apache/commons/io/input/ChecksumInputStream` **仍然出现**！

### 可能的原因

查看启动日志中的 classpath，发现 **commons-io JAR 确实在 classpath 中**：

```bash
/home/devin/.m2/repository/commons-io/commons-io/2.15.1/commons-io-2.15.1.jar
```

这说明问题可能是：

1. **类加载器问题**: Spring Boot 的类加载机制可能有问题
2. **Tika 版本冲突**: Tika 2.9.2 可能与 commons-io 2.15.1 不兼容
3. **传递依赖冲突**: 某个依赖可能排除了 commons-io

### 下一步调试

```bash
# 1. 检查依赖冲突
mvn dependency:tree -Dverbose | grep commons-io

# 2. 检查 Tika 需要的 commons-io 版本
mvn dependency:tree | grep -A 5 tika-parser-zip-commons

# 3. 尝试降级或升级 commons-io 版本
# 编辑 pom.xml 尝试不同版本
```

## 相关文件

- [application.yaml](../src/main/resources/application.yaml#L67-L71) - Python 服务配置
- [PythonRagService.java](../src/main/java/cn/yifan/drawsee/service/base/PythonRagService.java) - Python 服务调用
- [DocumentIngestionService.java](../src/main/java/cn/yifan/drawsee/service/business/DocumentIngestionService.java) - 文档摄取服务

## 经验教训

### 1. 检查服务端口配置

在微服务架构中，**端口配置错误**是常见问题：
- 开发环境可能使用不同的默认端口
- 文档和配置可能不同步
- 始终验证服务实际监听的端口

### 2. 端口检查命令

```bash
# 检查端口是否被监听
lsof -i :<PORT>
netstat -tlnp | grep <PORT>
ss -tlnp | grep <PORT>

# 测试端口连接
curl http://localhost:<PORT>/health
telnet localhost <PORT>
```

### 3. FastAPI 默认端口

FastAPI/Uvicorn 的默认端口是 **8000**，不是 8001：

```python
# FastAPI 默认启动方式
uvicorn app.main:app --reload  # 默认 127.0.0.1:8000

# 指定端口
uvicorn app.main:app --port 8001 --reload
```

### 4. 配置与实际运行的差异

配置文件中的默认值可能与实际运行环境不一致：
- 检查启动脚本
- 检查环境变量
- 检查 Docker Compose 配置
- 查看进程监听的实际端口

## 状态

✅ **端口配置已修复**
- Java 后端现在正确连接到 `http://localhost:8000`
- 服务已重启，配置已生效

❌ **NoClassDefFoundError 仍未解决**
- 需要进一步调查依赖冲突
- 可能需要调整 Tika 或 commons-io 版本

**下一步**: 用户测试文档上传，观察是否还有错误

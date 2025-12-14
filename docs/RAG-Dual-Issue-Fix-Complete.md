# RAG 文档上传双重问题修复完成

## 问题总结

**时间**: 2025-12-13 16:27 - 16:47

**两个并发问题**:
1. ❌ Java 后端 `NoClassDefFoundError: ChecksumInputStream`
2. ❌ Python 服务 MySQL 连接失败

## 问题 1: commons-io 依赖冲突

### 症状
```
java.lang.NoClassDefFoundError: org/apache/commons/io/input/ChecksumInputStream
```

### 根本原因
依赖版本冲突导致 Tika 无法加载 commons-io 类：
- 项目声明：`commons-io:2.15.1`
- commons-compress 需要：`commons-io:2.16.1`
- 用户最终升级到：`commons-io:2.21.0`（最新版本）

### 解决方案
用户在 [pom.xml](../pom.xml#L71-L76) 中将 commons-io 升级到 2.21.0：

```xml
<!-- https://mvnrepository.com/artifact/commons-io/commons-io -->
<dependency>
    <groupId>commons-io</groupId>
    <artifactId>commons-io</artifactId>
    <version>2.21.0</version>
</dependency>
```

## 问题 2: Python 服务 MySQL 连接失败

### 症状
```
(pymysql.err.OperationalError) (2003, "Can't connect to MySQL server on '3205@rm-2ze68xa54uq7agma27o.mysql.rds.aliyuncs.com'")
```

### 根本原因
MySQL 密码 `Funstack@3205` 中的 `@` 符号没有进行 URL 编码，导致连接字符串解析错误：
- 错误格式：`mysql+aiomysql://root:Funstack@3205@host:port/db`
- MySQL 解析为：用户名=`root`, 密码=`Funstack`, 主机=`3205@rm-2ze68xa54uq7agma27o...` ❌

### 解决方案
修改 [config.py](../../../python/drawsee-rag-python/app/config.py#L106-L128) 的 `mysql_url` 属性，使用 `urllib.parse.quote_plus` 进行 URL 编码：

```python
@property
def mysql_url(self) -> str:
    """MySQL异步连接URL"""
    # URL encode password to handle special characters like @
    from urllib.parse import quote_plus
    password = quote_plus(self.MYSQL_PASSWORD)
    return (
        f"mysql+aiomysql://{self.MYSQL_USER}:{password}@"
        f"{self.MYSQL_HOST}:{self.MYSQL_PORT}/{self.MYSQL_DATABASE}"
        f"?charset={self.MYSQL_CHARSET}"
    )
```

**编码结果**:
- 原密码：`Funstack@3205`
- 编码后：`Funstack%403205`
- 正确连接：`mysql+aiomysql://root:Funstack%403205@rm-2ze68xa54uq7agma27o.mysql.rds.aliyuncs.com:3306/drawsee-test`

## 修复步骤

### 1. 升级 Java 依赖（用户完成）
```bash
# 用户修改 pom.xml 中 commons-io 版本
vim pom.xml  # 改为 2.21.0
```

### 2. 修复 Python MySQL 连接
```bash
# 修改 config.py 添加 URL 编码
vim app/config.py
```

### 3. 重启两个服务

#### Python 服务
```bash
# 停止旧进程
kill <PID>

# 重新启动
cd /home/devin/Workspace/python/drawsee-rag-python
source .venv/bin/activate
uvicorn app.main:app --reload
```

**启动日志**:
```
✅ Drawsee RAG Service started successfully
MySQL数据库连接初始化成功
Qdrant客户端初始化成功
MinIO客户端初始化成功
Redis connection initialized for AuthService
```

#### Java 服务
```bash
# 停止旧进程
pkill -f "spring-boot:run"

# 清理编译
mvn clean compile -DskipTests

# 启动服务
mvn spring-boot:run
```

**启动日志**:
```
Started DrawseeApplication in 12.351 seconds
Tomcat started on port 6868 (http) with context path '/'
```

## 验证结果

### ✅ 两个服务都成功启动

**Java 后端**:
- PID: 177965
- 端口: 6868
- commons-io 2.21.0 已加载
- 连接到 Python 服务 http://localhost:8000

**Python RAG 服务**:
- PID: 176347 (主进程)
- 端口: 8000
- MySQL 连接成功（使用 URL 编码密码）
- Qdrant、MinIO、Redis 都已连接

### 预期行为

现在用户上传 PDF 文档应该：
1. ✅ 文件上传到 MinIO
2. ✅ Java 使用 Tika 解析 PDF（不再有 NoClassDefFoundError）
3. ✅ 调用 Python RAG 服务进行向量化
4. ✅ Python 服务存储向量到 Qdrant（不再有 MySQL 连接错误）
5. ✅ 前端显示"文档 RAG 处理完成"

## 技术要点

### 1. URL 编码特殊字符

密码中的特殊字符必须进行 URL 编码：

| 字符 | URL 编码 | 说明 |
|-----|----------|------|
| `@` | `%40` | 分隔用户和主机 |
| `:` | `%3A` | 分隔用户名和密码 |
| `/` | `%2F` | 路径分隔符 |
| `?` | `%3F` | 查询参数开始 |
| `#` | `%23` | URL 片段 |
| `&` | `%26` | 查询参数分隔符 |

### 2. Maven 依赖版本管理

依赖冲突解决策略：
1. 查看依赖树：`mvn dependency:tree -Dverbose | grep <artifact>`
2. 找出冲突：看哪些依赖被 `omitted for conflict with X.X.X`
3. 显式声明版本：在 pom.xml 中明确指定版本
4. 使用最新兼容版本：通常选择最高版本号

### 3. 服务重启的重要性

修改配置文件后必须重启服务：
- **Java**: 重新加载 classpath，加载新的 JAR 依赖
- **Python**: 重新读取配置文件，重新初始化连接

### 4. 错误诊断技巧

**MySQL 连接错误诊断**:
```python
# 打印实际连接字符串（去除密码）
print(f"Connecting to: mysql://{user}:***@{host}:{port}/{db}")

# 验证 URL 编码
from urllib.parse import quote_plus, unquote_plus
encoded = quote_plus("Funstack@3205")
print(f"Encoded: {encoded}")  # Funstack%403205
decoded = unquote_plus(encoded)
print(f"Decoded: {decoded}")  # Funstack@3205
```

## 相关文件

### Java 后端
- [pom.xml](../pom.xml#L71-L76) - 依赖版本管理
- [application.yaml](../src/main/resources/application.yaml#L69) - Python 服务 URL 配置
- [DocumentIngestionService.java](../src/main/java/cn/yifan/drawsee/service/business/DocumentIngestionService.java) - 文档摄取服务
- [DocumentParser.java](../src/main/java/cn/yifan/drawsee/service/business/parser/DocumentParser.java) - Tika 解析器

### Python RAG 服务
- [config.py](../../../python/drawsee-rag-python/app/config.py#L106-L128) - MySQL URL 编码修复
- [database.py](../../../python/drawsee-rag-python/app/models/database.py) - 数据库连接管理

## 状态

✅ **问题 1 已解决**: commons-io 2.21.0 已加载，不再有 NoClassDefFoundError

✅ **问题 2 已解决**: MySQL 密码 URL 编码，连接成功

✅ **两个服务运行正常**:
- Java 后端: PID 177965, 端口 6868
- Python RAG: PID 176347, 端口 8000

**下一步**: 用户测试 PDF 文档上传，验证完整的 RAG Phase 1 流程是否成功。

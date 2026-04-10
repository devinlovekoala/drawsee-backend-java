# 知识库列表为空问题排查指南

**症状**: 创建知识库成功，数据库中有记录，但前端列表显示为空

**日志特征**:
```
[fetchKnowledgeBases] API 原始返回:
Object { code: 0, message: "操作成功", data: [], timestamp: ... }

[fetchKnowledgeBases] 从 data 字段提取，长度: 0
[KnowledgeBasePage] Render - Total: 0 Filtered: 0
```

---

## 🔍 问题分析

从日志可以看出：
1. ✅ API 请求成功（code: 0）
2. ❌ 返回的 data 字段是空数组 `[]`
3. ❌ 前端正确处理了响应，但没有数据可显示

**结论**: 问题出在后端查询逻辑，而非前端显示逻辑

---

## 📋 排查步骤

### 步骤 1: 检查数据库中是否真的有数据

#### 1.1 连接数据库

```bash
# 查看 MySQL 配置
grep -A 5 "drawsee.mysql" src/main/resources/application.yaml

# 连接数据库（根据上面的配置调整）
mysql -h localhost -u root -p drawsee
```

#### 1.2 查询知识库表

```sql
-- 查看所有知识库（包括已删除的）
SELECT
    id,
    name,
    creator_id,
    creator_type,
    is_deleted,
    is_published,
    rag_enabled,
    created_at
FROM knowledge_base
ORDER BY created_at DESC
LIMIT 10;

-- 统计知识库数量
SELECT
    COUNT(*) as total,
    SUM(CASE WHEN is_deleted = 0 THEN 1 ELSE 0 END) as active,
    SUM(CASE WHEN is_deleted = 1 THEN 1 ELSE 0 END) as deleted
FROM knowledge_base;

-- 按创建者统计
SELECT
    creator_id,
    creator_type,
    COUNT(*) as count
FROM knowledge_base
WHERE is_deleted = 0
GROUP BY creator_id, creator_type;
```

**预期结果**:
- 应该能看到最近创建的知识库记录
- `is_deleted` 应该为 `0`（未删除）
- `is_published` 应该为 `1`（已发布，如果勾选了"立即发布"）

**如果没有数据**: 说明创建接口根本没有插入数据，跳到步骤 2

**如果有数据**: 说明查询接口有问题，继续步骤 3

---

### 步骤 2: 检查创建接口是否正常工作

#### 2.1 查看后端日志

```bash
# 实时查看日志
tail -f logs/drawsee.log | grep -i knowledge

# 或者查看最近的日志
tail -100 logs/drawsee.log | grep -i "create.*knowledge"
```

**预期日志**:
```
INFO  [AdminKnowledgeBaseController] 管理员创建知识库: CreateKnowledgeBaseDTO(name=测试知识库, ...)
INFO  [KnowledgeBaseService] 创建知识库成功: id=xxx
```

#### 2.2 测试创建接口

使用浏览器开发者工具或 Postman 测试：

```http
POST http://localhost:6868/api/admin/knowledge-bases?isPublished=true
Authorization: Bearer {你的token}
Content-Type: application/json

{
  "name": "测试知识库DEBUG",
  "description": "用于排查问题的测试知识库",
  "subject": "测试"
}
```

**预期响应**:
```json
{
  "code": 0,
  "message": "操作成功",
  "data": "知识库ID字符串",
  "timestamp": 1234567890
}
```

然后立即查询数据库：
```sql
SELECT * FROM knowledge_base WHERE name = '测试知识库DEBUG';
```

---

### 步骤 3: 检查查询接口的 SQL 执行情况

#### 3.1 启用 MyBatis SQL 日志

确保 `application.yaml` 中有以下配置：

```yaml
logging:
  level:
    cn.yifan.drawsee.mapper : debug
```

#### 3.2 重启后端并查看 SQL 日志

```bash
# 重启后端
# 然后在前端刷新知识库列表页面

# 查看执行的 SQL
tail -50 logs/drawsee.log | grep -i "SELECT.*knowledge_base"
```

**预期日志**:
```
DEBUG [KnowledgeBaseMapper.getAll] ==>  Preparing: SELECT * FROM knowledge_base WHERE is_deleted = ?
DEBUG [KnowledgeBaseMapper.getAll] ==> Parameters: false(Boolean)
DEBUG [KnowledgeBaseMapper.getAll] <==      Total: 5
```

**关键检查**:
- SQL 是否正确执行？
- 参数 `is_deleted` 的值是什么？（应该是 `false` 或 `0`）
- 返回的记录数 `Total` 是多少？

---

### 步骤 4: 检查 `is_deleted` 字段的数据类型问题

这是最常见的问题：**数据库中 `is_deleted` 可能是 `tinyint`，值为 `0`/`1`，但 Mapper 传入的是 `Boolean` 类型**

#### 4.1 检查表结构

```sql
DESCRIBE knowledge_base;

-- 或者
SHOW CREATE TABLE knowledge_base;
```

查看 `is_deleted` 字段的类型：
- 如果是 `tinyint(1)`：存储的是 `0` 或 `1`
- 如果是 `bit(1)`：存储的是 `b'0'` 或 `b'1'`

#### 4.2 检查数据库中的实际值

```sql
SELECT id, name, is_deleted, HEX(is_deleted) as is_deleted_hex
FROM knowledge_base
LIMIT 5;
```

**问题诊断**:
- 如果 `is_deleted` 显示为 `0` 或 `1`：说明类型正确
- 如果 SQL 查询时使用 `WHERE is_deleted = false`，MySQL 会自动转换

#### 4.3 手动测试 SQL

```sql
-- 测试不同的查询方式
SELECT COUNT(*) FROM knowledge_base WHERE is_deleted = 0;
SELECT COUNT(*) FROM knowledge_base WHERE is_deleted = false;
SELECT COUNT(*) FROM knowledge_base WHERE is_deleted = FALSE;
```

如果结果不同，说明存在类型转换问题。

---

### 步骤 5: 检查权限过滤逻辑

#### 5.1 检查当前登录用户信息

在浏览器控制台执行：
```javascript
console.log('User ID:', sessionStorage.getItem('Auth:UserId'));
console.log('User Role:', sessionStorage.getItem('Auth:UserRole'));
console.log('Token:', localStorage.getItem('Authorization'));
```

#### 5.2 检查后端是否过滤了结果

查看 `KnowledgeBaseService.convertToVOList` 方法：

```java
private List<KnowledgeBaseVO> convertToVOList(List<KnowledgeBase> knowledgeBases) {
    return knowledgeBases.stream()
        .map(this::convertToVO)
        .collect(Collectors.toList());
}
```

确保没有在转换过程中过滤掉数据。

#### 5.3 添加调试日志

临时修改 `KnowledgeBaseService.getAllKnowledgeBases` 方法：

```java
public List<KnowledgeBaseVO> getAllKnowledgeBases() {
    List<KnowledgeBase> knowledgeBases = knowledgeBaseMapper.getAll(false);
    log.info("查询到的知识库数量: {}", knowledgeBases.size());
    if (!knowledgeBases.isEmpty()) {
        log.info("第一个知识库: id={}, name={}",
            knowledgeBases.get(0).getId(),
            knowledgeBases.get(0).getName());
    }
    List<KnowledgeBaseVO> result = convertToVOList(knowledgeBases);
    log.info("转换后的VO数量: {}", result.size());
    return result;
}
```

---

## 🔧 常见问题与解决方案

### 问题 1: `is_deleted` 字段类型不匹配

**症状**: SQL 日志显示 `Total: 0`，但数据库中有数据

**原因**: MyBatis 传入的 `Boolean` 值与数据库 `tinyint` 不匹配

**解决方案 A**: 修改 Mapper 接口参数类型

```java
// KnowledgeBaseMapper.java
List<KnowledgeBase> getAll(@Param("isDeleted") Integer isDeleted);

// KnowledgeBaseService.java
public List<KnowledgeBaseVO> getAllKnowledgeBases() {
    List<KnowledgeBase> knowledgeBases = knowledgeBaseMapper.getAll(0);  // 传入 0 而非 false
    return convertToVOList(knowledgeBases);
}
```

**解决方案 B**: 修改 XML 中的 SQL（推荐）

```xml
<!-- KnowledgeBaseMapper.xml -->
<select id="getAll" resultMap="BaseResultMap">
    SELECT * FROM knowledge_base WHERE is_deleted = 0
</select>
```

### 问题 2: 缓存问题

**症状**: 日志显示 `HitCache /api/admin/knowledge-bases/all`

**原因**: Alova 使用了缓存，返回的是旧数据

**解决方案**: 清除缓存并禁用缓存

修改前端 API 调用，添加缓存控制：

```typescript
// src/api/methods/knowledge-base.methods.ts
export const getAllKnowledgeBases = async () => {
  try {
    const result = await alova.Get<KnowledgeBase[]>(`${BASE_PATHS.admin}/all`, {
      meta: {
        cacheMode: 'restore'  // 禁用缓存
      }
    });
    return result;
  } catch (error) {
    handleApiError(error);
    throw error;
  }
};
```

或者在浏览器控制台强制刷新：
```javascript
// 清除所有缓存
localStorage.clear();
sessionStorage.clear();
location.reload();
```

### 问题 3: 响应拦截器未正确解包

**症状**: 前端收到完整的 `{ code, message, data }` 对象

**原因**: 响应拦截器逻辑有问题或被缓存绕过

**解决方案**: 已在 [src/api/index.ts](../drawsee-admin-web/src/api/index.ts) 中修复

查看控制台是否有以下日志：
```
[Alova响应拦截器] 状态码: 200 响应体: {...}
[Alova响应拦截器] 解包成功，返回 data: [...]
```

### 问题 4: 数据库字段不存在

**症状**: 后端抛出 SQL 异常

**原因**: 表结构与代码不一致

**解决方案**: 检查并更新表结构

```sql
-- 检查是否缺少字段
SELECT
    COLUMN_NAME,
    DATA_TYPE,
    IS_NULLABLE
FROM INFORMATION_SCHEMA.COLUMNS
WHERE TABLE_SCHEMA = 'drawsee'
    AND TABLE_NAME = 'knowledge_base';

-- 如果缺少 is_deleted 字段
ALTER TABLE knowledge_base
ADD COLUMN is_deleted TINYINT(1) DEFAULT 0 COMMENT '是否删除：0-否，1-是';

-- 如果缺少 is_published 字段
ALTER TABLE knowledge_base
ADD COLUMN is_published TINYINT(1) DEFAULT 1 COMMENT '是否发布：0-否，1-是';
```

---

## ✅ 快速修复方案

如果以上步骤太复杂，可以尝试以下快速修复：

### 方案 1: 硬编码 SQL 参数

修改 `KnowledgeBaseMapper.xml`:

```xml
<select id="getAll" resultMap="BaseResultMap">
    SELECT * FROM knowledge_base WHERE is_deleted = 0
</select>
```

### 方案 2: 禁用前端缓存

在浏览器开发者工具的 Application 标签中：
1. 清除 Local Storage
2. 清除 Session Storage
3. 硬刷新页面（Ctrl+Shift+R）

### 方案 3: 添加测试数据

直接在数据库中插入测试数据：

```sql
INSERT INTO knowledge_base (
    id, name, description, subject,
    creator_id, creator_type,
    is_deleted, is_published,
    created_at, updated_at
) VALUES (
    UUID(),
    '测试知识库',
    '用于排查问题',
    '测试',
    1,  -- 替换为你的用户ID
    'ADMIN',
    0,  -- 未删除
    1,  -- 已发布
    NOW(),
    NOW()
);
```

然后刷新前端页面。

---

## 📞 获取更多帮助

如果问题仍未解决，请提供：

1. **数据库查询结果**:
   ```sql
   SELECT * FROM knowledge_base LIMIT 5;
   ```

2. **后端 SQL 日志**:
   ```bash
   tail -50 logs/drawsee.log | grep "knowledge_base"
   ```

3. **完整的浏览器控制台日志**（包括 Network 标签中的请求详情）

4. **表结构**:
   ```sql
   SHOW CREATE TABLE knowledge_base;
   ```

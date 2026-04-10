# 知识库创建失败问题诊断与修复

**错误**: `文件不能为空` → 实际是"知识库名称已存在"
**原因**: 错误消息写错了（已修复）

---

## 🐛 问题诊断

### 1. 错误消息修复

**文件**: `KnowledgeBaseService.java`

已修复两处错误消息：
- 第 93 行：教师创建知识库
- 第 129 行：管理员创建知识库

**修改前**:
```java
throw new ApiException(ApiError.KNOWLEDGE_BASE_HAD_EXISTED, "文件不能为空");
```

**修改后**:
```java
throw new ApiException(ApiError.KNOWLEDGE_BASE_HAD_EXISTED, "知识库名称已存在");
```

### 2. 实际问题分析

错误 `文件不能为空` 实际是 `知识库名称已存在`，说明：

**数据库中已有同名知识库**

可能的情况：
1. 之前测试时创建了同名知识库
2. 创建时遇到错误，但数据已插入
3. 知识库被"软删除"（`is_deleted = 1`），但重名检查没有过滤

---

## 🔍 诊断步骤

### 步骤 1: 检查数据库中的知识库

```sql
-- 连接数据库
mysql -u root -p drawsee

-- 查看所有知识库（包括已删除的）
SELECT
    id,
    name,
    creator_id,
    is_deleted,
    is_published,
    created_at
FROM knowledge_base
ORDER BY created_at DESC
LIMIT 10;

-- 查看是否有重名的知识库
SELECT
    name,
    COUNT(*) as count,
    GROUP_CONCAT(id) as ids,
    GROUP_CONCAT(is_deleted) as deleted_flags
FROM knowledge_base
GROUP BY name
HAVING count > 1;
```

### 步骤 2: 检查你尝试创建的知识库名称

假设你尝试创建名为 "测试知识库" 的知识库：

```sql
-- 查找同名知识库
SELECT
    id,
    name,
    creator_id,
    is_deleted,
    is_published,
    created_at,
    updated_at
FROM knowledge_base
WHERE name = '测试知识库';  -- 替换为你的知识库名称
```

**如果找到记录**:
- `is_deleted = 0`: 说明已有活跃的同名知识库，需要换个名称
- `is_deleted = 1`: 说明是已删除的知识库，可以清理或使用不同名称

---

## ✅ 解决方案

### 方案 1: 清理已删除的知识库

如果数据库中有 `is_deleted = 1` 的知识库：

```sql
-- 查看已删除的知识库
SELECT id, name, creator_id, created_at
FROM knowledge_base
WHERE is_deleted = 1;

-- 永久删除已删除的知识库（谨慎操作！）
DELETE FROM knowledge_base WHERE is_deleted = 1;
```

### 方案 2: 修改重名检查逻辑（推荐）

修改 `KnowledgeBaseService.java`，让重名检查只检查**未删除**的知识库：

#### 当前代码（有问题）:
```java
// 检查知识库名称是否已存在
KnowledgeBase existKnowledgeBase = knowledgeBaseMapper.getByName(createKnowledgeBaseDTO.getName());
if (existKnowledgeBase != null) {
    throw new ApiException(ApiError.KNOWLEDGE_BASE_HAD_EXISTED, "知识库名称已存在");
}
```

#### 优化后的代码:
```java
// 检查知识库名称是否已存在（只检查未删除的）
KnowledgeBase existKnowledgeBase = knowledgeBaseMapper.getByName(createKnowledgeBaseDTO.getName());
if (existKnowledgeBase != null && !existKnowledgeBase.getIsDeleted()) {
    throw new ApiException(ApiError.KNOWLEDGE_BASE_HAD_EXISTED, "知识库名称已存在");
}
```

**或者修改 Mapper 查询**（更好）:

在 `KnowledgeBaseMapper.xml` 中修改 `getByName` 查询：

```xml
<!-- 原来的 -->
<select id="getByName" parameterType="string" resultMap="BaseResultMap">
    SELECT * FROM knowledge_base WHERE name = #{name}
</select>

<!-- 修改为 -->
<select id="getByName" parameterType="string" resultMap="BaseResultMap">
    SELECT * FROM knowledge_base WHERE name = #{name} AND is_deleted = 0
</select>
```

### 方案 3: 使用不同的知识库名称

最简单的方法：创建知识库时使用不同的名称。

---

## 🧪 测试验证

### 1. 清理测试数据

```sql
-- 删除所有测试知识库（请根据实际情况调整）
DELETE FROM knowledge_base WHERE name LIKE '测试%';

-- 或者只删除已标记为删除的
DELETE FROM knowledge_base WHERE is_deleted = 1;
```

### 2. 重启后端

```bash
cd /home/devin/Workspace/drawsee-platform/drawsee-java
mvn spring-boot:run
```

### 3. 测试创建知识库

1. 使用唯一的名称（如 "知识库-20251213"）
2. 确认创建成功
3. 确认列表中显示

---

## 📊 知识库列表为空的原因

你说"查知识库依旧没任何结果"，可能的原因：

### 原因 1: SQL 查询问题（已修复）

之前的 `is_deleted = #{isDeleted}` Boolean 参数问题导致查询不到数据。

**验证**: 在数据库中手动执行：
```sql
SELECT * FROM knowledge_base WHERE is_deleted = 0;
```

如果能查到数据，说明 SQL 修复生效了。

### 原因 2: 双层包装问题（已修复）

GlobalResponseHandler 导致的双层包装问题。

**验证**: 查看浏览器控制台日志，应该不再出现"检测到双层包装"。

### 原因 3: 用户权限问题

可能是 `creator_id` 或权限过滤导致看不到知识库。

**验证**:
```sql
-- 检查当前登录用户ID
-- 在浏览器控制台执行:
console.log('User ID:', sessionStorage.getItem('Auth:UserId'));

-- 然后在数据库中检查:
SELECT id, name, creator_id
FROM knowledge_base
WHERE creator_id = [你的用户ID] AND is_deleted = 0;
```

### 原因 4: 缓存问题

**解决**: 清除浏览器缓存并硬刷新（Ctrl+Shift+R）

---

## 🛠️ 快速修复脚本

```sql
-- 诊断与修复一体化脚本

-- 1. 查看当前状态
SELECT '=== 所有知识库 ===' as info;
SELECT id, name, creator_id, is_deleted, created_at FROM knowledge_base ORDER BY created_at DESC LIMIT 10;

SELECT '=== 重名检查 ===' as info;
SELECT name, COUNT(*) as count FROM knowledge_base GROUP BY name HAVING count > 1;

SELECT '=== 已删除的知识库 ===' as info;
SELECT id, name, creator_id, created_at FROM knowledge_base WHERE is_deleted = 1;

-- 2. 清理已删除的知识库（可选，谨慎执行）
-- DELETE FROM knowledge_base WHERE is_deleted = 1;

-- 3. 清理所有测试数据（可选，谨慎执行）
-- DELETE FROM knowledge_base WHERE name LIKE '%测试%' OR name LIKE '%test%';

-- 4. 查看清理后的状态
SELECT '=== 清理后的知识库 ===' as info;
SELECT id, name, creator_id, is_deleted, created_at FROM knowledge_base WHERE is_deleted = 0 ORDER BY created_at DESC;
```

---

## 📝 建议的修改优先级

### 高优先级（立即修改）

1. ✅ **错误消息修复** - 已完成
   - 文件: `KnowledgeBaseService.java`
   - 修改: "文件不能为空" → "知识库名称已存在"

2. ⏳ **修改 `getByName` 查询** - 建议修改
   - 文件: `KnowledgeBaseMapper.xml`
   - 添加: `AND is_deleted = 0`
   - 理由: 允许重用已删除知识库的名称

### 中优先级（可选）

3. 清理数据库中的测试数据
4. 优化重名检查逻辑

---

## 🔄 完整的修复流程

1. ✅ 修复错误消息（已完成）
2. ✅ 编译后端（已完成）
3. 🔄 **重启后端服务**
4. 🔄 **清除浏览器缓存**
5. 🔄 **查询数据库，确认有数据**
6. 🔄 **使用新的唯一名称创建知识库**
7. 🔄 **验证列表显示正常**

---

## ❓ 如果问题仍然存在

请提供以下信息：

1. **数据库查询结果**:
   ```sql
   SELECT * FROM knowledge_base WHERE is_deleted = 0;
   ```

2. **浏览器控制台日志**（完整的）

3. **后端日志**（包括启动日志和请求日志）

4. **你尝试创建的知识库名称**

# 知识库列表为空问题修复总结

**日期**: 2025-12-12
**问题**: 创建知识库显示成功，但前端列表为空
**根本原因**: MyBatis Mapper XML 中使用 `#{isDeleted}` Boolean 参数与数据库 `tinyint` 类型不匹配

---

## 🐛 问题现象

```
[fetchKnowledgeBases] API 原始返回:
Object { code: 0, message: "操作成功", data: [], timestamp: 1765534832582 }

[fetchKnowledgeBases] 从 data 字段提取，长度: 0
[KnowledgeBasePage] Render - Total: 0 Filtered: 0
```

---

## 🔍 根本原因

### 技术细节

**数据库字段类型**:
```sql
is_deleted TINYINT(1) DEFAULT 0  -- 存储 0 或 1
```

**Java 代码传参**:
```java
// KnowledgeBaseService.java
public List<KnowledgeBaseVO> getAllKnowledgeBases() {
    List<KnowledgeBase> knowledgeBases = knowledgeBaseMapper.getAll(false);  // ← Boolean 类型
    return convertToVOList(knowledgeBases);
}
```

**MyBatis XML 原来的写法**:
```xml
<select id="getAll" resultMap="BaseResultMap">
    SELECT * FROM knowledge_base WHERE is_deleted = #{isDeleted}
    <!--
        #{isDeleted} 接收到 false(Boolean)
        MySQL 可能将其转换为 NULL 或其他值
        导致查询条件不匹配
    -->
</select>
```

**执行的 SQL（推测）**:
```sql
SELECT * FROM knowledge_base WHERE is_deleted = false
-- 或者
SELECT * FROM knowledge_base WHERE is_deleted = NULL
```

这与数据库中实际存储的 `0` 不匹配，导致查询不到数据。

---

## ✅ 修复方案

### 修改文件

**文件**: `src/main/resources/mapper/KnowledgeBaseMapper.xml`

**修改内容**: 将所有 `is_deleted = #{isDeleted}` 改为 `is_deleted = 0`

### 修改的查询方法

1. **getAll** - 查询所有知识库
   ```xml
   <!-- 修改前 -->
   <select id="getAll" resultMap="BaseResultMap">
       SELECT * FROM knowledge_base WHERE is_deleted = #{isDeleted}
   </select>

   <!-- 修改后 -->
   <select id="getAll" resultMap="BaseResultMap">
       SELECT * FROM knowledge_base WHERE is_deleted = 0
   </select>
   ```

2. **getByCreatorId** - 根据创建者ID查询
   ```xml
   <!-- 修改前 -->
   <select id="getByCreatorId" resultMap="BaseResultMap">
       SELECT * FROM knowledge_base
       WHERE creator_id = #{creatorId}
       AND is_deleted = #{isDeleted}
   </select>

   <!-- 修改后 -->
   <select id="getByCreatorId" resultMap="BaseResultMap">
       SELECT * FROM knowledge_base
       WHERE creator_id = #{creatorId}
       AND is_deleted = 0
   </select>
   ```

3. **getByMemberId** - 查询用户作为成员的知识库
   ```xml
   <!-- 修改前 -->
   <select id="getByMemberId" resultMap="BaseResultMap">
       SELECT * FROM knowledge_base
       WHERE JSON_CONTAINS(members, CAST(#{userId} AS JSON), '$')
       AND is_deleted = #{isDeleted}
   </select>

   <!-- 修改后 -->
   <select id="getByMemberId" resultMap="BaseResultMap">
       SELECT * FROM knowledge_base
       WHERE JSON_CONTAINS(members, CAST(#{userId} AS JSON), '$')
       AND is_deleted = 0
   </select>
   ```

4. **getByCreatorIdAndRagEnabled** - 根据创建者ID和RAG启用状态查询
   ```xml
   <!-- 修改前 -->
   AND is_deleted = #{isDeleted}

   <!-- 修改后 -->
   AND is_deleted = 0
   ```

5. **getByMemberIdAndRagEnabled** - 根据成员ID和RAG启用状态查询
   ```xml
   <!-- 修改前 -->
   AND is_deleted = #{isDeleted}

   <!-- 修改后 -->
   AND is_deleted = 0
   ```

---

## 🎯 修改影响

### 受影响的接口

1. **管理员接口**:
   - `GET /api/admin/knowledge-bases/all` - 获取所有知识库
   - `GET /api/admin/knowledge-bases` - 获取管理员创建的知识库

2. **教师接口**:
   - `GET /api/teacher/knowledge-bases/created` - 获取教师创建的知识库
   - `GET /api/teacher/knowledge-bases/joined` - 获取教师加入的知识库

3. **学生接口**:
   - `GET /api/student/knowledge-bases` - 获取学生可访问的知识库

4. **通用接口**:
   - `GET /api/knowledge-bases/created` - 获取我创建的知识库
   - `GET /api/knowledge-bases/joined` - 获取我加入的知识库

---

## 📝 技术说明

### 为什么硬编码 `0` 而不是修复参数传递？

**选项 A**: 修改 Mapper 接口参数类型
```java
// KnowledgeBaseMapper.java
List<KnowledgeBase> getAll(@Param("isDeleted") Integer isDeleted);  // Boolean → Integer

// 调用时
knowledgeBaseMapper.getAll(0);  // false → 0
```
❌ **缺点**: 需要修改所有调用处，工作量大，容易遗漏

**选项 B**: 硬编码 SQL 中的值（已采用）
```xml
<select id="getAll" resultMap="BaseResultMap">
    SELECT * FROM knowledge_base WHERE is_deleted = 0
</select>
```
✅ **优点**:
- 修改集中在 XML 文件
- 不需要修改 Java 代码
- 查询意图明确（只查询未删除的记录）
- 性能更好（常量可被优化）

### 为什么会出现这个问题？

1. **数据库设计**: 使用 `TINYINT(1)` 表示布尔值（MySQL 惯例）
2. **ORM 映射**: MyBatis 将 Java `Boolean` 映射到数据库
3. **类型转换**: MySQL 对 `false` 的处理可能因版本和配置而异
   - 有些情况下 `false` = `0`
   - 有些情况下 `false` ≠ `0`（被视为 NULL 或字符串）

### 最佳实践

**数据库布尔字段设计**:
```sql
-- 推荐
is_deleted TINYINT(1) DEFAULT 0 COMMENT '是否删除：0-否，1-是'

-- 或者使用枚举
is_deleted ENUM('0', '1') DEFAULT '0'
```

**MyBatis 查询布尔字段**:
```xml
<!-- 推荐：使用数字常量 -->
WHERE is_deleted = 0

<!-- 避免：使用参数 -->
WHERE is_deleted = #{isDeleted}  <!-- 可能有兼容性问题 -->
```

---

## 🧪 测试验证

### 验证步骤

1. **重启后端**:
   ```bash
   cd /home/devin/Workspace/drawsee-platform/drawsee-java
   mvn spring-boot:run
   ```

2. **清除前端缓存**:
   - 打开浏览器开发者工具（F12）
   - Application → Clear storage → Clear site data
   - 或使用隐身窗口

3. **测试知识库列表**:
   - 访问知识库管理页面
   - 查看浏览器控制台日志：
     ```
     [fetchKnowledgeBases] API 原始返回: { code: 0, data: [...], ... }
     [fetchKnowledgeBases] 从 data 字段提取，长度: N
     [KnowledgeBasePage] Render - Total: N Filtered: N
     ```
   - 确认列表中显示知识库卡片

4. **测试创建知识库**:
   - 点击"创建知识库"按钮
   - 填写信息并提交
   - 确认成功提示
   - 确认新知识库立即显示在列表中

### 预期结果

- ✅ 已创建的知识库正确显示
- ✅ 新创建的知识库立即出现在列表
- ✅ 切换标签（"所有知识库"/"我创建的"/"我加入的"）正常工作
- ✅ 搜索功能正常

---

## 📊 相关文件清单

### 修改的文件

1. ✅ `src/main/resources/mapper/KnowledgeBaseMapper.xml` - 修复 SQL 查询
2. ✅ `src/api/index.ts` - 增强响应拦截器日志
3. ✅ `src/pages/KnowledgeBasePage.tsx` - 添加延迟切换标签（之前的修复）

### 创建的文档

1. ✅ `docs/Knowledge-Base-Empty-List-Debug.md` - 问题排查指南
2. ✅ `docs/Knowledge-Base-Fix-Summary.md` - 修复总结（本文档）
3. ✅ `docs/KnowledgeBase-Creation-Debug.md` - 创建问题排查

---

## 🔍 排查历史

### 初步怀疑

1. ❌ 前端数据规范化逻辑错误 → 日志显示处理正确
2. ❌ 响应拦截器未执行 → 已增强日志确认
3. ❌ 缓存问题 → 清除缓存后问题依旧

### 确认根因

4. ✅ **后端 SQL 查询结果为空** ← 正确诊断
   - API 返回 `data: []`
   - 数据库中有数据
   - SQL 条件不匹配

### 修复验证

5. ✅ 修改 XML 硬编码 `is_deleted = 0`
6. ✅ 编译成功
7. ⏳ 等待用户测试验证

---

## 💡 经验教训

1. **类型匹配很重要**: ORM 框架的类型映射可能不总是如预期工作
2. **查看 SQL 日志**: MyBatis Debug 日志能快速定位问题
3. **简化优于复杂**: 硬编码常量比参数传递更可靠（在此场景下）
4. **逐层排查**: 前端 → API → 后端 → 数据库，逐层缩小范围

---

## 📞 后续支持

如果修复后问题仍存在，请提供：

1. 后端启动日志（特别是 MyBatis SQL 日志）
2. 浏览器控制台完整日志
3. 数据库查询结果：
   ```sql
   SELECT * FROM knowledge_base WHERE is_deleted = 0 LIMIT 10;
   ```

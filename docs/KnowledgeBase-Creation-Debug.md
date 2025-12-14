# 知识库创建后不显示问题排查指南

**日期**: 2025-12-12
**问题**: 在后台管理界面创建新知识库后，显示创建成功，但界面刷新后没有看到新创建的知识库

---

## 🔍 问题排查步骤

### 步骤 1: 检查浏览器控制台

打开浏览器开发者工具（F12），切换到 Console 标签，查看是否有错误信息：

```
预期的日志输出:
1. Creating knowledge base with data: {name: "xxx", ...}
2. Admin create result: {知识库ID}
3. Knowledge base created successfully, refresh triggered
4. Fetching knowledge bases for role: ADMIN activeTab: created
5. API result: [...]
6. Normalized knowledge bases: N [...]
```

**检查要点**:
- 是否有红色错误信息？
- `API result` 的内容是什么？
- `Normalized knowledge bases` 的数量是多少？

### 步骤 2: 检查网络请求

切换到 Network 标签，过滤 XHR 请求，查看以下请求：

#### 创建知识库请求
```
请求: POST /api/admin/knowledge-bases?isPublished=true
请求体: {
  "name": "测试知识库",
  "description": "描述",
  "subject": "学科"
}

预期响应:
{
  "code": 200,
  "data": "知识库ID字符串",
  "message": "success"
}
```

#### 查询知识库列表请求
```
请求: GET /api/admin/knowledge-bases
(切换到"我创建的"标签时)

预期响应:
{
  "code": 200,
  "data": [
    {
      "id": "xxx",
      "name": "测试知识库",
      ...
    }
  ],
  "message": "success"
}
```

**检查要点**:
- 创建请求的响应码是否为 200？
- 创建响应的 `data` 字段是否包含知识库 ID？
- 列表查询响应的 `data` 字段是否是数组？
- 列表查询响应的数组中是否包含刚创建的知识库？

### 步骤 3: 检查后端日志

查看 Java 后端日志文件：

```bash
tail -f /home/devin/Workspace/drawsee-platform/drawsee-java/logs/drawsee.log | grep -i "knowledge"
```

**预期日志**:
```
INFO  [AdminKnowledgeBaseController] 管理员创建知识库: name=xxx
INFO  [KnowledgeBaseService] 创建知识库成功: id=xxx
INFO  [AdminKnowledgeBaseController] 获取管理员创建的知识库列表
```

### 步骤 4: 检查数据库

连接到 MySQL 数据库，查询 `knowledge_base` 表：

```sql
-- 查询最近创建的知识库
SELECT
    id,
    name,
    creator_id,
    creator_type,
    is_published,
    created_at
FROM knowledge_base
ORDER BY created_at DESC
LIMIT 10;

-- 检查创建者信息
SELECT
    kb.id,
    kb.name,
    kb.creator_id,
    kb.creator_type,
    u.username AS creator_name
FROM knowledge_base kb
LEFT JOIN user u ON kb.creator_id = u.id
ORDER BY kb.created_at DESC
LIMIT 10;
```

**检查要点**:
- 知识库是否真的插入到数据库中？
- `creator_id` 是否与当前登录用户的 ID 匹配？
- `creator_type` 是否为 `ADMIN`？
- `is_published` 是否为 `1`（如果勾选了"立即发布"）？

---

## 🐛 常见问题与解决方案

### 问题 1: 创建请求返回 500 错误

**原因**: 后端服务异常

**排查**:
```bash
# 查看详细错误日志
tail -200 /home/devin/Workspace/drawsee-platform/drawsee-java/logs/drawsee.log | grep -A 10 "ERROR"
```

**可能的原因**:
- 数据库连接失败
- 必填字段缺失
- 唯一性约束冲突（如知识库名称重复）

### 问题 2: 列表查询返回空数组

**原因**: 查询条件不匹配当前用户

**排查**:
1. 检查 sessionStorage 中的用户信息：
```javascript
// 在浏览器控制台执行
console.log('User ID:', sessionStorage.getItem('Auth:UserId'));
console.log('User Role:', sessionStorage.getItem('Auth:UserRole'));
```

2. 检查后端查询逻辑是否正确过滤了 creator_id

**解决方案**:
```java
// 确保 KnowledgeBaseService.getMyCreatedKnowledgeBases() 正确获取当前用户ID
String userId = StpUtil.getLoginIdAsString();
List<KnowledgeBase> knowledgeBases = knowledgeBaseMapper.selectByCreatorId(userId);
```

### 问题 3: 响应数据格式不符合预期

**原因**: 响应拦截器解包逻辑问题

**排查**:
检查前端 `src/api/index.ts` 的响应拦截器：

```typescript
responded: async (response) => {
    const json = await response.json();
    if (response.status !== 200) {
        throw new Error(json.message);
    } else {
        return json.data;  // ← 确保这里返回 json.data
    }
}
```

**检查后端响应格式**:
```java
// 确保统一返回 R<T> 格式
@PostMapping
public R<String> createKnowledgeBase(...) {
    String id = knowledgeBaseService.create(...);
    return R.ok(id);  // ← 正确格式
}
```

### 问题 4: 权限校验失败

**原因**: Sa-Token 未正确识别用户角色

**排查**:
```java
// 在 AdminKnowledgeBaseController 中添加日志
@PostMapping
public R<String> createKnowledgeBase(...) {
    String userId = StpUtil.getLoginIdAsString();
    String role = StpUtil.getLoginInfo().getExtra("role").toString();
    log.info("创建知识库 - 用户ID: {}, 角色: {}", userId, role);
    ...
}
```

---

## ✅ 验证修复效果

修复后，按以下步骤验证：

1. **清空浏览器缓存**
   - Ctrl+Shift+Delete
   - 清除缓存和 Cookie
   - 重新登录

2. **创建测试知识库**
   - 填写知识库名称: `测试知识库 ${当前时间戳}`
   - 勾选"立即发布"
   - 点击"创建"

3. **观察预期行为**
   - ✅ 显示 Toast: "创建成功"
   - ✅ 模态框关闭
   - ✅ 自动切换到"我创建的"标签
   - ✅ 列表中显示新创建的知识库
   - ✅ 控制台输出: `Knowledge base created successfully, refresh triggered`
   - ✅ 控制台输出: `Normalized knowledge bases: 1 [...]`

---

## 🔧 临时解决方案（如果问题仍未解决）

如果问题持续存在，可以尝试以下临时方案：

### 方案 1: 强制刷新整个页面

修改 `CreateKnowledgeBaseModal.tsx` 第 78 行：

```typescript
// 旧代码
onSuccess();

// 新代码（临时）
window.location.reload();  // 强制刷新页面
```

### 方案 2: 增加延迟刷新

修改 `CreateKnowledgeBaseModal.tsx` 第 75-78 行：

```typescript
// 触发刷新事件
emitRefreshEvent('knowledge-base');

// 增加延迟
setTimeout(() => {
  onSuccess();
}, 500);  // 延迟 500ms 再切换标签
```

### 方案 3: 手动添加到列表

修改 `KnowledgeBasePage.tsx` 第 154-163 行：

```typescript
const handleCreateSuccess = async () => {
  // 重新获取知识库列表
  await fetchKnowledgeBases();

  // 切换到"我创建的"标签
  if (userRole === USER_ROLE.ADMIN || userRole === USER_ROLE.TEACHER) {
    setActiveTab('created');
  }
};
```

---

## 📞 如需进一步帮助

如果以上步骤无法解决问题，请提供以下信息：

1. 浏览器控制台的完整日志截图
2. Network 标签中创建和查询请求的完整响应
3. 后端日志中与知识库相关的错误信息
4. 数据库查询结果截图

**联系方式**: 项目 GitHub Issues

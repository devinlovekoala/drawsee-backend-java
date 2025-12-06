# 修复文档上传 file_url 字段过长问题

## 问题描述

上传实验文档时报错：`Data truncation: Data too long for column 'file_url' at row 1`

原因：MinIO 返回的预签名 URL 包含大量查询参数（过期时间、签名等），长度通常超过 500 字符，而数据库 `file_url` 字段定义为 `VARCHAR(500)`。

## 解决方案

采用**动态生成预签名 URL** 的方案：

1. **数据库不存储预签名 URL**，只存储 `object_path`（对象路径）
2. **运行时动态生成预签名 URL**，在返回给前端时临时生成

### 优势

- **安全性更好**：预签名 URL 有过期时间（24小时），动态生成确保 URL 始终有效
- **节省空间**：不需要在数据库存储很长的 URL
- **灵活性强**：可以根据需要调整 URL 过期时间和权限

## 代码修改

### 1. MinioService.uploadFile()

修改返回值从**完整 URL** 改为**对象路径**：

```java
// 返回对象路径，不返回预签名URL（避免URL过长且会过期）
return objectName;
```

### 2. UserDocumentService.uploadDocument()

上传时不存储 URL，返回时动态生成：

```java
// Do not store presigned URL as it will expire - generate it on demand
document.setFileUrl(null);
document.setObjectPath(objectName);

userDocumentMapper.insert(document);

// Generate presigned URL after saving to database
String presignedUrl = minioService.getObjectUrl(objectName);
document.setFileUrl(presignedUrl);
```

### 3. 查询方法添加动态 URL 生成

在所有获取文档的方法中添加动态生成逻辑：

- `getUserDocuments()` - 批量生成
- `getDocumentById()` - 单个生成
- `getDocumentByUuid()` - 单个生成

## 数据库迁移

**无需修改数据库表结构**，现有的 `VARCHAR(500)` 字段保持不变（存储 NULL）。

如果之前有存储过长 URL 导致的脏数据，可以执行：

```sql
-- 清理可能存在的脏数据（可选）
UPDATE user_document SET file_url = NULL;
```

## 测试建议

1. 上传新文档，验证可以正常存储
2. 获取文档列表，验证返回的 `file_url` 包含有效的预签名 URL
3. 使用返回的 URL 访问文件，验证可以正常下载/预览
4. 等待 24 小时后重新获取文档，验证 URL 会自动更新

## 影响范围

- ✅ 文档上传功能
- ✅ 文档列表查询
- ✅ 文档详情查询（ID/UUID）
- ✅ 文档下载功能

## 注意事项

- 前端无需修改，API 返回格式保持不变
- `file_url` 字段在数据库中保持为 NULL，仅在返回时填充
- 每次查询都会动态生成新的预签名 URL，确保 URL 始终有效

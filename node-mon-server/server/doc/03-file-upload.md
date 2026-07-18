# 3. 文件上传

使用 node-mon-server 测试客户端 multipart/form-data 文件上传，支持单文件、多文件、混合字段上传。

## 适用条件

- 服务器已启动
- 上传目录 `server/uploads/` 可写（服务启动时自动创建）
- 单文件最大 10MB，单次最多 5 个文件
- 无 MIME 类型限制（可通过配置开启，见 [配置参考](./06-configuration.md)）

## 端点一览

| 方法 | 路径 | field 名称 | Multer 方法 | 说明 |
| --- | --- | --- | --- | --- |
| POST | `/upload/single` | `file` | `.single('file')` | 上传 1 个文件，可附带额外表单字段 |
| POST | `/upload/multiple` | `files` | `.array('files', 5)` | 上传 1-5 个文件 |
| POST | `/upload/mixed` | 任意 | `.any()` | 文件 + 文本 + JSON + 表单混合上传 |

## 推荐做法

```text
调试单文件上传   → /upload/single（最常用，OkHttp MultipartBody 直接兼容）
调试多图/多附件  → /upload/multiple（field 名称统一为 files）
调试复杂请求体   → /upload/mixed（模拟真实业务：图片+描述+元数据 JSON）
```

## 可复制 Demo

### 单文件上传

```bash
# 基本上传
curl -X POST http://localhost:3000/upload/single \
  -F "file=@/path/to/photo.png"

# 带额外字段
curl -X POST http://localhost:3000/upload/single \
  -F "file=@/path/to/photo.png" \
  -F "description=头像照片"
```

响应：

```json
{
  "code": 200,
  "message": "File uploaded successfully",
  "data": {
    "filename": "1752835200000-123456789-photo.png",
    "originalName": "photo.png",
    "size": 204800,
    "mimetype": "image/png",
    "path": "/absolute/path/to/server/uploads/1752835200000-123456789-photo.png"
  }
}
```

### 多文件上传

```bash
curl -X POST http://localhost:3000/upload/multiple \
  -F "files=@/path/to/file1.zip" \
  -F "files=@/path/to/file2.png" \
  -F "files=@/path/to/file3.pdf"
```

响应中 `data.files` 为数组，每个元素包含 `filename`, `originalName`, `size`, `mimetype`。

### 混合上传（文件 + 文本 + JSON + 表单）

```bash
curl -X POST http://localhost:3000/upload/mixed \
  -F "files=@/path/to/photo.png" \
  -F "content=这是文本内容" \
  -F 'jsonData={"userId":123,"action":"upload"}' \
  -F "extraKey=extraValue"
```

响应中 `data` 包含四个部分：

```json
{
  "code": 200,
  "message": "Mixed multipart data received successfully",
  "data": {
    "files": [{ "fieldname": "files", "filename": "...", "originalName": "photo.png", ... }],
    "formFields": { "content": "这是文本内容", "jsonData": "{\"userId\":123...}", "extraKey": "extraValue" },
    "content": "这是文本内容",
    "jsonData": { "userId": 123, "action": "upload" }
  }
}
```

- `formFields`：所有非文件字段的原始值（字符串形式）
- `content`：`content` 字段的纯文本值（若存在）
- `jsonData`：`jsonData` 字段的 JSON 解析结果（解析失败则为 null）

## 关键说明

- **文件名防冲突**：服务端使用 `时间戳-随机数-原始文件名` 格式重命名，例如 `1752835200000-987654321-photo.png`。
- **field 名称必须匹配**：单文件上传的字段名是 `file`（单数），多文件是 `files`（复数）。客户端拼错字段名会导致 `No file uploaded` 错误。
- **上传大小限制**：超过 10MB 返回 `413 Payload Too Large`，修改 `server/config/index.js` 中的 `upload.limits.fileSize`。
- **Multer 错误类型**：
  - `LIMIT_FILE_SIZE` — 单个文件超限
  - `LIMIT_FILE_COUNT` — 文件数量超限
  - `LIMIT_UNEXPECTED_FILE` — 字段名不匹配
- **文件不会自动清理**：上传的文件持久保存在 `server/uploads/`，需手动清理。

## 常见错误

### 400 No file uploaded

字段名错误。单文件上传字段名必须是 `file`：

```bash
# ❌ 错误：字段名不匹配
curl -X POST http://localhost:3000/upload/single -F "avatar=@photo.png"

# ✅ 正确
curl -X POST http://localhost:3000/upload/single -F "file=@photo.png"
```

### 413 Payload Too Large

文件超过 10MB 限制。修改 `server/config/index.js`：

```javascript
upload: {
    limits: {
        fileSize: 50 * 1024 * 1024, // 改为 50MB
    }
}
```

## 验证方式

```bash
# 创建测试文件并上传
echo "test content" > /tmp/test.txt
curl -s -X POST http://localhost:3000/upload/single -F "file=@/tmp/test.txt" | python3 -m json.tool

# 验证文件已保存
ls -la server/uploads/ | grep test.txt
```

[返回 README](../README.md)

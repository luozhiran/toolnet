# 4. 文件下载

测试文件下载功能，包括完整下载、断点续传（Range 请求）、分片下载合并、以及静态文件服务。

## 适用条件

- 服务器已启动
- `server/uploads/` 目录中存在文件（通过上传接口上传，或手动放入）

## 端点一览

| 方法 | 路径 | 功能 | 响应状态码 |
| --- | --- | --- | --- |
| GET | `/download/files` | 获取 uploads/ 目录文件列表 | 200，JSON |
| GET | `/download/:filename` | 下载文件（支持 Range） | 200（完整）/ 206（分片）/ 416（Range 无效）|
| GET | `/download/static/:filename` | 下载 public/ 目录静态文件（也支持 Range） | 同上有 200/206 |

## 推荐做法

```text
获取可下载文件列表     → GET /download/files
下载完整文件           → GET /download/:filename（浏览器直接打开或 curl -O）
测试断点续传           → 先 HEAD 获取文件大小，再发 Range: bytes=START-END
测试分片并发下载       → 前端发送多个 Range 请求，合并 Uint8Array
提供预设文件下载       → 把文件放到 public/，通过 /download/static/ 访问
```

## 可复制 Demo

### 获取文件列表

```bash
curl http://localhost:3000/download/files
```

响应：

```json
{
  "code": 200,
  "files": [
    {
      "name": "1752835200000-123456789-photo.png",
      "size": 204800,
      "modified": "2026-07-18T10:00:00.000Z"
    }
  ]
}
```

### 完整下载

```bash
# 方式一：curl 直接下载
curl -O http://localhost:3000/download/1752835200000-123456789-photo.png

# 方式二：浏览器直接访问
# http://localhost:3000/download/1752835200000-123456789-photo.png
```

响应头包含：

```text
Content-Length: 204800
Content-Type: application/octet-stream
Accept-Ranges: bytes
```

### 断点续传（Range 请求）

```bash
# 1. 先查看文件大小
curl -I http://localhost:3000/download/1752835200000-123456789-photo.png

# 2. 下载前 1MB
curl -H "Range: bytes=0-1048575" \
     http://localhost:3000/download/1752835200000-123456789-photo.png \
     -o chunk1

# 3. 下载剩余部分
curl -H "Range: bytes=1048576-" \
     http://localhost:3000/download/1752835200000-123456789-photo.png \
     -o chunk2

# 4. 合并
cat chunk1 chunk2 > restored.png
```

成功时返回 `206 Partial Content`，响应头包含：

```text
Content-Range: bytes 0-1048575/204800
Content-Length: 1048576
```

### Range 请求错误处理

```bash
# Range 超出文件大小 → 416 Range Not Satisfiable
curl -H "Range: bytes=999999999-" http://localhost:3000/download/small-file.txt
```

### 静态文件下载

将文件放入 `public/` 目录：

```bash
# 假设 public/ 下有 example.zip
curl -O http://localhost:3000/download/static/example.zip
```

## 关键说明

- **Range 头格式**：`bytes=START-END`，`END` 可选（表示到文件末尾）。服务端解析后返回 `206` + `Content-Range` 头。
- **416 错误**：当 `start >= fileSize` 或 `end >= fileSize` 或 `start > end` 时返回。
- **文件名需精确匹配**：下载端点使用 `upload/` 目录中的实际文件名（包含时间戳前缀）。从 `/download/files` 获取准确的 `name`。
- **全部文件以 `application/octet-stream` 返回**，浏览器会触发下载而非预览。
- **静态文件路径**：`/download/static/` 映射到项目根目录的 `public/` 文件夹，支持 `acceptRanges: true`，也可用于分片下载测试。

## 分片下载示例（JavaScript 前端）

```javascript
async function downloadWithChunks(filename, chunkCount = 3) {
  // 1. 先获取文件大小
  const headRes = await fetch(`/download/${filename}`, { method: 'HEAD' });
  const totalSize = parseInt(headRes.headers.get('Content-Length'));

  // 2. 计算每片大小
  const chunkSize = Math.ceil(totalSize / chunkCount);

  // 3. 并发下载所有分片
  const chunks = await Promise.all(
    Array.from({ length: chunkCount }, (_, i) => {
      const start = i * chunkSize;
      const end = i === chunkCount - 1 ? totalSize - 1 : start + chunkSize - 1;
      return fetch(`/download/${filename}`, {
        headers: { Range: `bytes=${start}-${end}` }
      }).then(r => r.arrayBuffer());
    })
  );

  // 4. 合并
  const merged = new Uint8Array(totalSize);
  let offset = 0;
  for (const chunk of chunks) {
    merged.set(new Uint8Array(chunk), offset);
    offset += chunk.byteLength;
  }
  return merged;
}
```

## 验证方式

```bash
# 1. 检查文件列表接口
curl -s http://localhost:3000/download/files | python3 -m json.tool

# 2. 检查完整下载（对比文件哈希）
curl -s http://localhost:3000/download/test.txt | md5sum
md5sum server/uploads/test.txt

# 3. 检查 Range 返回 206
curl -s -o /dev/null -w "%{http_code}" -H "Range: bytes=0-99" http://localhost:3000/download/test.txt
# 应输出 206
```

[返回 README](../README.md)

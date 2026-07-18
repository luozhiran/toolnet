# 5. 调试日志

使用 `debug` 模块按命名空间分级输出日志，排查请求内容、响应数据、上传过程、性能瓶颈。

## 适用条件

- 开发环境（生产环境建议关闭或仅输出 `app:main`）
- Node.js 环境支持 `DEBUG` 环境变量

## 命名空间一览

| 命名空间 | 输出内容 | 典型用途 |
| --- | --- | --- |
| `app:main` | 服务器启动/关闭事件、全局错误、文件下载日志 | 确认服务状态 |
| `app:request` | 每个请求的 Method、URL、Headers、Query、Body | 排查客户端请求格式 |
| `app:response` | res.json/res.send 的响应体、res.download 的文件信息 | 确认服务端返回内容 |
| `app:upload` | Multer 文件过滤、目标目录、文件名生成、上传结果 | 排查上传问题 |
| `app:perf` | 每个请求耗时、Multer 处理耗时、慢请求警告 | 定位性能瓶颈 |

定义位置：`server/middleware/debug.js`（请求日志）、`server/middleware/responseInterceptor.js`（响应日志）、`server/middleware/performance.js`（性能日志）、各路由中的 `logger.upload()` 调用。

## 推荐做法

```text
开发调试全部功能   → DEBUG=app:*
只看请求/响应      → DEBUG=app:request,app:response
排查上传问题       → DEBUG=app:upload,app:perf
生产环境最小日志   → DEBUG=app:main  或不设置
排查慢请求         → DEBUG=app:perf
```

## 可复制 Demo

### 启动全部调试日志

```bash
DEBUG=app:* node server.js
```

### 按需组合

```bash
# 只看上传过程
DEBUG=app:upload node server.js

# 请求 + 响应
DEBUG=app:request,app:response node server.js

# 排除性能日志（减少输出噪音）
DEBUG=app:main,app:request,app:response,app:upload node server.js
```

### Windows 兼容

```bash
# PowerShell
$env:DEBUG="app:*"; node server.js

# cmd（需要 cross-env）
npx cross-env DEBUG=app:* node server.js
```

## 日志输出示例

### 一次单文件上传的完整日志

```text
app:request 📨 [2026-07-18T10:00:00.000Z] POST /upload/single
app:request 📡 Headers: { "content-type": "multipart/form-data; boundary=...", ... }
app:request 🔗 Query params: {}
app:upload 🎯 开始处理文件上传 (字段: file)
app:upload 🔍 文件过滤检查:
app:upload    - 字段名: file
app:upload    - 文件名: photo.png
app:upload    - MIME类型: image/png
app:upload    - 大小: 204800 bytes
app:upload 📂 目标目录: D:\WorkSpace\toolnet\node-mon-server\server\uploads
app:upload 📄 生成文件名: 1752835200000-987654321-photo.png
app:upload    - 原始名: photo.png
app:upload ✅ 文件上传成功:
app:upload    - 原始名: photo.png
app:upload    - 大小: 204800 bytes
app:perf ⏱️  multer.single("file") 处理耗时: 12ms
app:response 📤 响应状态: 200
app:response 📦 响应体 (JSON): {
  "code": 200,
  "message": "File uploaded successfully",
  "data": {
    "filename": "1752835200000-987654321-photo.png",
    "originalName": "photo.png",
    "size": 204800,
    "mimetype": "image/png",
    "path": "..."
  }
}
app:perf POST /upload/single - 23ms - 200
```

### 慢请求告警

当请求耗时超过 `config.perf.slowRequest`（默认 1000ms）时：

```text
⚠️  慢请求警告: GET /download/large-file.zip 耗时 2340ms
```

当 Multer 处理耗时超过 `config.perf.slowMulter`（默认 500ms）时：

```text
⚠️  multer 处理较慢: 780ms
```

## 关键说明

- **日志输出到 stderr**（`debug` 模块默认行为），不影响 stdout 的正常输出。
- **彩色输出**：终端支持时自动着色，不同命名空间有不同颜色便于区分。
- **性能开销极低**：当 `DEBUG` 未设置时，`debug()` 调用几乎是空操作。
- **`app:main` 还包含**：文件下载日志（来自 `server/utils/logger.js` 中的 `logger.main()` 调用）。
- **响应拦截注意事项**：`responseInterceptor` 中间件会拦截 `res.json` 和 `res.send`，因此所有 JSON 响应都会被完整打印。大响应体可能导致日志刷屏。

## 底层实现

日志系统位于 `server/utils/logger.js`，封装了 `debug` 模块：

```javascript
const debug = require('debug');

// 预定义快捷方法
module.exports = {
    main: (...args) => debug('app:main')(...args),
    request: (...args) => debug('app:request')(...args),
    response: (...args) => debug('app:response')(...args),
    upload: (...args) => debug('app:upload')(...args),
    perf: (...args) => debug('app:perf')(...args)
};
```

各中间件和路由通过 `require('../utils/logger')` 引用统一的 logger 实例。

## 验证方式

```bash
# 启动全部日志，发送一个请求，确认有输出
DEBUG=app:* node server.js &
sleep 2
curl -s http://localhost:3000/api/data > /dev/null
# 观察 stderr 输出应包含 app:request、app:response、app:perf 日志
```

[返回 README](../README.md)

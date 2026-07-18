# 6. 配置参考

所有可配置项集中管理，支持通过配置文件和环境变量调整。

## 配置文件

`server/config/index.js` — 所有配置的唯一定义位置。

## 配置项一览

| 配置项 | 默认值 | 环境变量 | 说明 |
| --- | --- | --- | --- |
| `port` | `3000` | `PORT` | 服务器监听端口 |
| `host` | `'0.0.0.0'` | `HOST` | 绑定地址，`0.0.0.0` 表示接受所有网卡连接 |
| `upload.dest` | `path.join(__dirname, '../uploads')` | — | 上传文件存储目录（绝对路径） |
| `upload.limits.fileSize` | `10 * 1024 * 1024`（10MB） | — | 单个文件最大字节数 |
| `upload.limits.files` | `5` | — | 单次请求最大文件数 |
| `upload.allowedMimeTypes` | `['image/png', 'image/jpeg', 'application/zip', 'application/pdf']` | — | 允许的 MIME 类型列表（当前未强制校验，仅用于文档说明） |
| `staticDir` | `path.join(__dirname, '../public')` | — | 静态文件目录（`/download/static/` 映射位置） |
| `perf.slowRequest` | `1000`（1秒） | — | 慢请求告警阈值（毫秒） |
| `perf.slowMulter` | `500` | — | Multer 处理慢告警阈值（毫秒） |
| `debugNamespaces.main` | `'app:main'` | — | 主日志命名空间 |
| `debugNamespaces.request` | `'app:request'` | — | 请求日志命名空间 |
| `debugNamespaces.response` | `'app:response'` | — | 响应日志命名空间 |
| `debugNamespaces.upload` | `'app:upload'` | — | 上传日志命名空间 |
| `debugNamespaces.perf` | `'app:perf'` | — | 性能日志命名空间 |

## 推荐做法

```text
修改监听端口       → 设置环境变量 PORT=4000
修改上传大小限制   → 编辑 config/index.js 的 upload.limits.fileSize
修改慢请求阈值     → 编辑 config/index.js 的 perf.slowRequest
生产环境绑定地址   → 保持 0.0.0.0（默认）或改为具体 IP
```

## 可复制 Demo

### 改端口

```bash
# 方式一：环境变量（推荐，不改文件）
PORT=4000 node server.js

# 方式二：修改配置文件
# 编辑 server/config/index.js：
#   port: process.env.PORT || 4000
```

### 改上传限制

编辑 `server/config/index.js`：

```javascript
upload: {
    dest: path.join(__dirname, '../uploads'),
    limits: {
        fileSize: 50 * 1024 * 1024, // 50MB
        files: 10                    // 最多 10 个文件
    },
    allowedMimeTypes: [
        'image/png',
        'image/jpeg',
        'application/zip',
        'application/pdf',
        'video/mp4'                  // 新增 mp4
    ]
},
```

修改后重启服务生效。

### 改性能阈值

```javascript
perf: {
    slowRequest: 2000,  // 超过 2 秒才告警
    slowMulter: 1000    // Multer 超过 1 秒才告警
},
```

## 关键说明

- **环境变量优先级**：`PORT` 和 `HOST` 通过 `process.env` 读取，会覆盖配置文件中的默认值。其他配置项目前不通过环境变量覆盖，需直接修改 `config/index.js`。
- **`upload.dest` 使用相对路径**：基于 `__dirname`（即 `server/config/`）解析为 `server/uploads/`。如果移动 config 文件位置需相应调整。
- **静态文件目录**：`staticDir` 指向 `server/public/` 而不是 `server/../public/`（项目根目录的 public），但 `server.js` 中静态文件路由实际使用了 `path.join(__dirname, '../public')`，这是硬编码的，与 config 文件中的 `staticDir` 不一致。实际生效的是 `server.js` 中的路径。
- **修改配置后需重启服务**：没有热加载机制。
- **`allowedMimeTypes` 未强制校验**：当前 `fileFilter` 函数对所有文件调用 `cb(null, true)`，MIME 白名单仅为文档声明。如需启用过滤，修改 `server/routes/upload.js` 中的 `fileFilter` 函数。

## 配置结构总览

```javascript
module.exports = {
    port: process.env.PORT || 3000,
    host: process.env.HOST || '0.0.0.0',
    upload: {
        dest: path.join(__dirname, '../uploads'),
        limits: { fileSize: 10 * 1024 * 1024, files: 5 },
        allowedMimeTypes: ['image/png', 'image/jpeg', 'application/zip', 'application/pdf']
    },
    staticDir: path.join(__dirname, '../public'),
    perf: { slowRequest: 1000, slowMulter: 500 },
    debugNamespaces: {
        main: 'app:main',
        request: 'app:request',
        response: 'app:response',
        upload: 'app:upload',
        perf: 'app:perf'
    }
};
```

## 验证方式

```bash
# 验证端口配置生效
PORT=4567 node server.js
# 输出应显示：🚀 服务器已启动，访问 http://localhost:4567

# 验证上传大小限制生效
dd if=/dev/zero of=/tmp/11mb.bin bs=1M count=11
curl -s -X POST http://localhost:3000/upload/single -F "file=@/tmp/11mb.bin"
# 应返回 413 错误
```

[返回 README](../README.md)

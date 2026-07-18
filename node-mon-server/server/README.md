## 快速开始

### 1. 克隆/创建项目

按照项目架构文档创建目录和文件，或直接下载源码包。

### 2. 安装依赖

```bash
npm install
```

依赖列表：\`express\`, \`multer\`, \`cors\`, \`debug\`。

### 3. 启动服务器

```bash
# 普通模式
node server.js

# 调试模式（输出所有 app:* 日志）
DEBUG=app:* node server.js

# 使用 nodemon 自动重启（开发推荐）
npm install -g nodemon
DEBUG=app:* nodemon server.js
```

默认监听 \`http://0.0.0.0:3000\`，局域网其他设备可通过 \`http://<本机IP>:3000\` 访问。

### 4. 访问测试页面

将 \`test.html\` 保存到项目 \`public/\` 目录下（如果目录不存在，手动创建）。

启动服务器（确保 \`config.staticDir\` 指向 \`public\` 目录，已在配置中设置）：

```bash
DEBUG=app:* node server.js
```

浏览器访问测试页面：

```
http://localhost:3000/download/static/test.html
```

（静态路由挂载在 \`/download/static\`，直接访问上述地址即可。）

## 功能端点一览

| 功能 | 方法 | 端点 | 说明 |
|------|------|------|------|
| JSON 示例 | GET | \`/api/data\` | 返回固定 JSON |
| 提交 JSON | POST | \`/api/submit\` | 接收 JSON body，回显数据 |
| 单文件上传 | POST | \`/upload/single\` | field 名称为 \`file\`，支持额外字段 |
| 多文件上传 | POST | \`/upload/multiple\` | field 名称为 \`files\`，最多 5 个 |
| 获取文件列表 | GET | \`/download/files\` | 返回 uploads 目录中的文件信息 |
| 下载文件 | GET | \`/download/:filename\` | 下载 uploads 中的文件 |
| 静态文件 | GET | \`/download/static/:filename\` | 访问 public 目录中的文件（如 zip/png） |

## 测试页面（Web UI）

\`test.html\` 提供了一个美观、响应式的可视化测试面板，覆盖所有功能：

- **JSON API**：\`GET /api/data\` 和 \`POST /api/submit\`，可自定义 JSON 提交。
- **单文件上传**：调用 \`/upload/single\`，支持附加字段（key=value 或自动作为 description）。
- **多文件上传**：调用 \`/upload/multiple\`，最多 5 个文件。
- **文件列表**：\`GET /download/files\`，展示上传目录所有文件，并提供直接下载链接。
- **文件下载**：手动输入文件名或点击列表中的下载链接，调用 \`/download/:filename\`。
- **静态文件下载**（如 zip/png 放在 \`public/\`）：可通过 \`/download/static/文件名\` 访问，测试页面中也有说明。

该测试页面可直接用于接口调试和功能验证，无需额外工具。

## 详细使用示例

### 1. 测试 JSON API

```bash
# GET 请求
curl http://localhost:3000/api/data

# POST JSON
curl -X POST http://localhost:3000/api/submit \\
  -H "Content-Type: application/json" \\
  -d '{"name":"test","value":123}'
```

### 2. 上传文件

**单文件上传（类似 OkHttp 的 multipart）**

```bash
curl -X POST http://localhost:3000/upload/single \\
  -F "file=@/path/to/your/image.png" \\
  -F "description=测试图片"
```

**多文件上传**

```bash
curl -X POST http://localhost:3000/upload/multiple \\
  -F "files=@/path/to/file1.zip" \\
  -F "files=@/path/to/file2.png"
```

### 3. 查看和下载文件

```bash
# 获取已上传文件列表
curl http://localhost:3000/download/files

# 下载文件（文件名为列表中的 name）
curl -O http://localhost:3000/download/1705314600000-123-test.png
```

### 4. 静态文件服务

将文件（如 \`example.zip\` 或 \`photo.png\`）放入 \`public/\` 目录后：

```
http://localhost:3000/download/static/example.zip
```

可直接在浏览器下载。

## 配置修改

编辑 \`config/index.js\`：

- 修改 \`port\` / \`host\` 改变监听地址
- 修改 \`upload.limits.fileSize\` 调整上传大小限制
- 修改 \`perf.slowRequest\` 调整慢请求阈值（毫秒）

## 调试与日志

启用调试模式后，控制台会输出彩色分类日志：

- \`app:main\` - 启动信息、全局错误
- \`app:request\` - 每个请求的 URL、Headers、Query
- \`app:upload\` - multer 处理的细节（字段、文件名、大小等）
- \`app:response\` - 返回的 JSON 或下载信息
- \`app:perf\` - 请求耗时、multer 处理耗时

**示例输出**

```
app:main 服务器启动在端口 3000 +0ms
app:request 📨 [2025-01-16T10:00:00.000Z] POST /upload/single
app:upload 🎯 开始处理文件上传 (字段: file)
app:upload 🔍 文件过滤检查:
app:upload    - 字段名: file
app:upload    - 文件名: test.png
app:upload 📂 目标目录: ./uploads
app:upload 📄 生成文件名: 1705314600000-123456789-test.png
app:upload ✅ 文件上传成功:
app:upload    - 原始名: test.png
app:upload    - 大小: 1024 bytes
app:response 📤 响应状态: 200
app:response 📦 响应体 (JSON): { "code":200, ... }
app:perf ⏱️  multer.single("file") 处理耗时: 45ms
app:perf POST /upload/single - 67ms - 200
```

## 常见问题

### 1. 上传文件提示 \`413 Payload Too Large\`

修改 \`config/index.js\` 中的 \`upload.limits.fileSize\` 值（单位字节），增大限制后重启服务。

### 2. 局域网其他设备无法访问

确保 \`config/index.js\` 中 \`host\` 为 \`'0.0.0.0'\`，并关闭防火墙或允许 3000 端口。

### 3. 文件上传后没有出现在列表？

检查 \`uploads/\` 目录权限，确保 Node 进程有写入权限。服务器启动时会自动创建目录。

### 4. 如何测试 OkHttp 的 multipart 上传？

OkHttp 的 \`MultipartBody.Builder\` 构建的请求与 HTML 表单 \`multipart/form-data\` 完全兼容，直接使用 \`/upload/single\` 端点即可，字段名必须为 \`file\`。

## 项目维护

- 主入口：\`server.js\`
- 添加新功能请遵循现有模块划分（路由、中间件、工具函数）
- 所有配置集中在 \`config/index.js\`，避免硬编码

## 许可证

MIT
`;

// 创建下载函数
function downloadFile(content, filename) {
    const blob = new Blob([content], { type: 'text/markdown' });
    const url = URL.createObjectURL(blob);
    const a = document.createElement('a');
    a.href = url;
    a.download = filename;
    document.body.appendChild(a);
    a.click();
    document.body.removeChild(a);
    URL.revokeObjectURL(url);
}

// 下载两个文件
downloadFile(archContent, 'arch.md');
downloadFile(readmeContent, 'README.md');

console.log('✅ arch.md 和 README.md 已开始下载');
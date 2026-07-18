# 测试服务器 - 完整使用教程（React 版）

一个基于 Express + Multer 后端 + React 前端的现代化测试服务器，支持：
- REST API（GET/POST JSON、表单、路径参数、动态 Query）
- 单/多文件上传（multipart/form-data）
- 混合上传（文件 + 文本 + JSON + 表单字段）
- 文件下载（支持断点续传、分片下载演示）
- 每个功能附带 **Android OkHttp 示例代码**（一键查看）

---

## 目录

1. [快速开始](#快速开始)
2. [项目结构](#项目结构)
3. [功能端点一览](#功能端点一览)
4. [启动开发环境](#启动开发环境)
5. [详细使用示例](#详细使用示例)
6. [查看 OkHttp 示例](#查看-okhttp-示例)
7. [生产环境部署](#生产环境部署)
8. [常见问题](#常见问题)

---

## 快速开始

### 1. 克隆/创建项目

按照项目架构文档创建目录和文件，或直接下载源码包。

### 2. 安装依赖

#### 后端依赖
```bash
cd server
npm init -y
npm install express multer cors debug
npm install -D nodemon   # 可选
```

#### 前端依赖
```bash
cd frontend
npm install
```

### 3. 启动开发环境

#### 方式一：分别启动（推荐）
```bash
# 终端1：后端
cd server
DEBUG=app:* nodemon server.js

# 终端2：前端
cd frontend
npm run dev
```
浏览器访问 \`http://localhost:5173\`。

#### 方式二：一键启动（根目录配置后）
```bash
npm run dev
```

---

## 功能端点一览

| 功能 | 方法 | 端点 | 说明 | 前端测试组件 |
|------|------|------|------|--------------|
| 无参 GET | GET | \`/api/data\` | 返回固定 JSON | \`ApiTest\` - 获取数据 |
| Query 参数 GET | GET | \`/api/echo\` | 接收任意 Query 参数并回显 | \`ApiTest\` - Echo 请求 |
| 路径参数 + Query | GET | \`/api/user/:id\` | 演示路径参数和可选 Query | \`ApiTest\` - 获取用户信息 |
| 提交 JSON | POST | \`/api/json\` | 接收 JSON body，回显数据 | \`ApiTest\` - 提交 JSON |
| 提交表单 | POST | \`/api/form\` | 接收 \`application/x-www-form-urlencoded\` | \`ApiTest\` - 提交表单 |
| 单文件上传 | POST | \`/upload/single\` | field 名称 \`file\`，支持额外字段 | \`FileUpload\` - 单文件上传 |
| 多文件上传 | POST | \`/upload/multiple\` | field 名称 \`files\`，最多 5 个 | \`FileUpload\` - 多文件上传 |
| 混合上传 | POST | \`/upload/mixed\` | 同时上传文件、文本、JSON、表单字段 | \`MixedUpload\` |
| 获取文件列表 | GET | \`/download/files\` | 返回 \`uploads/\` 目录中的文件信息 | \`FileManager\` - 刷新列表 |
| 下载文件 | GET | \`/download/:filename\` | 支持断点续传（Range） | \`FileManager\` - 直接下载 / 列表下载 |
| 静态文件服务 | GET | \`/download/static/:filename\` | 访问 \`public/\` 目录中的文件（如 zip/png） | 直接 URL 访问 |
| 分片下载演示 | - | (前端实现) | 利用 Range 请求分片下载并合并，模拟断点续传 | \`ChunkDownload\` |

---

## 启动开发环境

### 配置前端代理

\`frontend/vite.config.js\` 内容：

```javascript
import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'

export default defineConfig({
  plugins: [react()],
  server: {
    proxy: {
      '/api': 'http://localhost:3000',
      '/upload': 'http://localhost:3000',
      '/download': 'http://localhost:3000'
    }
  }
})
```

### 运行

```bash
# 后端终端
cd server
DEBUG=app:* nodemon server.js

# 前端终端
cd frontend
npm run dev
```
访问 \`http://localhost:5173\`。

---

## 详细使用示例

### API 测试示例

#### 1. GET /api/data（无参数）
**curl**：
```bash
curl http://localhost:3000/api/data
```
**前端操作**：点击“获取数据”按钮。

#### 2. GET /api/echo（带 Query 参数）
**curl**：
```bash
curl "http://localhost:3000/api/echo?name=张三&age=25"
```
**前端操作**：动态添加参数名/值，点击“发送 Echo 请求”。

#### 3. GET /api/user/:id（路径参数 + Query）
**curl**：
```bash
curl "http://localhost:3000/api/user/123?name=李四"
```
**前端操作**：输入用户ID和可选 Query 参数，点击“获取用户信息”。

#### 4. POST /api/json（发送 JSON）
**curl**：
```bash
curl -X POST http://localhost:3000/api/json \\
  -H "Content-Type: application/json" \\
  -d '{"message":"Hello"}'
```
**前端操作**：编辑 JSON 文本框，点击“提交 JSON”。

#### 5. POST /api/form（表单）
**curl**：
```bash
curl -X POST http://localhost:3000/api/form \\
  -d "username=alice&password=123456"
```
**前端操作**：动态添加表单字段，点击“提交表单”。

### 文件上传示例

#### 6. 单文件上传
**curl**：
```bash
curl -X POST http://localhost:3000/upload/single \\
  -F "file=@/path/to/test.png" \\
  -F "description=测试图片"
```
**前端操作**：选择文件，填写额外字段，点击“上传文件”。

#### 7. 多文件上传
**curl**：
```bash
curl -X POST http://localhost:3000/upload/multiple \\
  -F "files=@/path/to/file1.zip" \\
  -F "files=@/path/to/file2.png"
```
**前端操作**：选择多个文件，点击“上传多文件”。

#### 8. 混合上传（文件+文本+JSON+表单）
**curl**：
```bash
curl -X POST http://localhost:3000/upload/mixed \\
  -F "files=@/path/to/photo.png" \\
  -F "content=Hello World" \\
  -F 'jsonData={"action":"upload"}' \\
  -F "extraKey=extraValue"
```
**前端操作**：选择文件，填写文本、JSON 和表单字段，点击“混合上传”。

### 文件下载示例

#### 9. 获取文件列表
**curl**：
```bash
curl http://localhost:3000/download/files
```
**前端操作**：点击“刷新文件列表”。

#### 10. 下载完整文件
**curl**：
```bash
curl -O http://localhost:3000/download/1705314600000-123-test.png
```
**前端操作**：点击文件列表中的“下载”链接或手动输入文件名后下载。

### 断点续传演示

#### 11. 分片下载并合并（模拟断点续传）
**原理**：前端发送多个 \`Range\` 请求（如 \`Range: bytes=0-1048575\`），服务端返回 \`206 Partial Content\`，前端收集分片后合并下载。

**curl 模拟分片下载**：
```bash
# 下载第一片
curl -H "Range: bytes=0-1048575" http://localhost:3000/download/example.zip -o chunk1
# 下载第二片
curl -H "Range: bytes=1048576-2097151" http://localhost:3000/download/example.zip -o chunk2
# 合并
cat chunk1 chunk2 > example.zip
```
**前端操作**：选择已上传的文件，设置分片数量，点击“分片下载并合并”。

---

## 查看 OkHttp 示例

在每个功能区域旁边都有一个绿色的 **“📱 示例”** 按钮，点击后弹出模态框，显示对应的 Android OkHttp 代码（Kotlin）。示例涵盖所有上述接口，包括文件上传和断点续传。

> 代码中的 IP 地址 \`192.168.1.100\` 请替换为您的服务器实际 IP。

---

## 生产环境部署

### 1. 构建前端
```bash
cd frontend
npm run build
```
产物位于 \`frontend/dist\`。

### 2. 启动后端（生产模式）
```bash
cd server
NODE_ENV=production node server.js
```
访问 \`http://<服务器IP>:3000\` 即可使用完整应用。

---

## 常见问题

### 1. 前端启动失败，提示 \`Module not found\`
进入 \`frontend\` 目录执行 \`npm install\`，若仍有问题则删除 \`node_modules\` 重装。

### 2. 点击示例按钮无反应或报错
检查 \`frontend/src/examples.js\` 是否存在且语法正确（注意模板字符串中的 \`\\${}\` 已转义）。查看浏览器控制台错误。

### 3. 上传文件提示 \`413 Payload Too Large\`
修改 \`server.js\` 中的 \`config.upload.limits.fileSize\`（单位字节），重启后端。

### 4. 局域网其他设备无法访问前端
- 后端已监听 \`0.0.0.0\`。
- 前端开发服务器默认仅监听 \`localhost\`，可在 \`vite.config.js\` 中添加 \`server.host: '0.0.0.0'\`。
- 防火墙开放端口 3000 和 5173。

### 5. 分片下载合并后文件损坏
检查后端是否返回 \`206\` 状态码，以及前端合并顺序是否正确（按索引合并 \`Uint8Array\`）。

---

## 许可证

MIT

---

<div align="center">
🎉 祝您使用愉快！
</div>`;
  const blob = new Blob([content], { type: 'text/markdown' });
  const a = document.createElement('a');
  const url = URL.createObjectURL(blob);
  a.href = url;
  a.download = '测试服务器使用教程.md';
  document.body.appendChild(a);
  a.click();
  document.body.removeChild(a);
  URL.revokeObjectURL(url);
})();
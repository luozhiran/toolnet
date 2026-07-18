# 1. 快速启动

在本地启动后端测试服务器，几分钟内即可开始调试客户端请求。

## 适用条件

- Node.js v16+
- 端口 3000 未被占用（可通过环境变量 `PORT` 修改）

## 推荐做法

```bash
# 进入 server 目录
cd server

# 安装依赖
npm install

# 启动服务器（带完整调试日志）
DEBUG=app:* node server.js

# 或使用 nodemon 自动重启
DEBUG=app:* npx nodemon server.js
```

启动成功后输出：

```text
🚀 服务器已启动，访问 http://localhost:3000
📁 上传目录: D:\WorkSpace\toolnet\node-mon-server\server\uploads
💡 开发模式请运行前端开发服务器: cd frontend && npm run dev
📊 调试输出: 设置环境变量 DEBUG=app:* 启用详细日志
```

## 可复制 Demo

### Demo 1：验证服务器是否正常

```bash
curl http://localhost:3000/api/data
```

预期响应：

```json
{
  "code": 200,
  "message": "success",
  "data": {
    "id": 1,
    "name": "Test Data",
    "timestamp": 1752835200000
  }
}
```

### Demo 2：从根目录一键启动前后端

```bash
# 在项目根目录
npm install       # 安装后端依赖 + 构建前端（首次）
npm run dev       # 同时启动后端 (3000) 和前端 (5173)
```

`npm run dev` 等价于：

```bash
concurrently "npm run dev:server" "npm run dev:client"
# dev:server → cd server && cross-env DEBUG=app:* nodemon server.js
# dev:client → cd frontend && npm run dev
```

## 关键说明

- **默认监听 `0.0.0.0`**，局域网内其他设备可通过 `http://<本机IP>:3000` 访问。
- **upload 目录自动创建**：首次上传文件时自动在 `server/uploads/` 下创建存储目录。
- **Windows 用户**：`DEBUG=app:*` 语法在 cmd 中不生效，请使用 Git Bash 或 PowerShell 的 `cross-env`（根目录 `npm run dev:server` 已处理）。
- **端口冲突**：设置 `PORT=4000 node server.js` 改为其他端口。
- 若只想用后端（不用 React UI），直接用 curl 或 Postman 请求各端点即可。

## 常见问题

### 启动报错 `EADDRINUSE`

端口 3000 被占用，使用其他端口：

```bash
PORT=4000 node server.js
```

### `DEBUG=app:*` 不输出日志（Windows cmd）

使用 `cross-env`：

```bash
npx cross-env DEBUG=app:* node server.js
```

或者切换到 Git Bash / PowerShell。

### 局域网设备无法访问

- 确认 `HOST` 为 `0.0.0.0`（默认已是）。
- 检查防火墙是否允许 Node.js 通过 3000 端口。

## 验证方式

```bash
# 健康检查
curl -s http://localhost:3000/api/data | grep -q '"code":200' && echo "✅ 服务正常" || echo "❌ 服务异常"

# 确认上传目录存在
ls server/uploads/ && echo "✅ 上传目录已就绪"
```

[返回 README](../README.md)

# 7. 生产部署

将 node-mon-server 部署到生产环境，后端托管前端构建产物，单一端口对外服务。

## 适用条件

- 项目源码完整
- Node.js v16+ 已安装
- 服务器防火墙允许目标端口（默认 3000）

## 推荐做法

```text
内部测试/临时使用 → NODE_ENV=production node server/server.js
长期运行           → 使用 PM2 或 systemd 守护进程
需要 HTTPS         → 前置 Nginx 反向代理
```

## 可复制 Demo

### 构建并启动

```bash
# 1. 构建前端（生成 frontend/dist/）
npm run build:client

# 2. 生产模式启动
NODE_ENV=production node server/server.js
```

启动成功后：

```text
🚀 服务器已启动，访问 http://localhost:3000
📁 上传目录: /path/to/server/uploads
📦 前端静态文件: /path/to/frontend/dist
```

访问 `http://<服务器IP>:3000` 即可使用完整应用。

### 使用 PM2 守护

```bash
# 安装 PM2
npm install -g pm2

# 构建前端
npm run build:client

# 启动（PM2 管理）
NODE_ENV=production pm2 start server/server.js --name node-mon-server

# 查看状态
pm2 status

# 查看日志
pm2 logs node-mon-server

# 设置开机自启
pm2 startup
pm2 save
```

### Nginx 反向代理（可选）

```nginx
server {
    listen 80;
    server_name test-server.example.com;

    location / {
        proxy_pass http://127.0.0.1:3000;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;

        # 上传大文件支持
        client_max_body_size 50m;
    }
}
```

## 生产模式 vs 开发模式

| 行为 | 开发模式 (`NODE_ENV != 'production'`) | 生产模式 (`NODE_ENV=production`) |
| --- | --- | --- |
| 前端 | 需单独启动 `npm run dev:client`（Vite 端口 5173） | 后端托管 `frontend/dist/` 静态文件 |
| 访问入口 | `http://localhost:5173`（前端代理到后端） | `http://服务器IP:3000`（直接访问） |
| API 代理 | Vite proxy：`/api → localhost:3000` | 无需代理，前后端同一端口 |
| 静态文件 | Vite HMR 热更新 | 构建产物，无热更新 |
| 日志 | 建议开启 `DEBUG=app:*` | 建议关闭或仅 `DEBUG=app:main` |

## 关键说明

- **生产模式的前端必须预构建**：服务启动时检查 `frontend/dist/` 是否存在，不存在则打印警告并跳过静态文件托管。
- **所有非 API 路由返回 `index.html`**：支持 SPA 前端路由（`react-router` 等），访问任意路径都会回退到 React 应用。
- **`/api`、`/upload`、`/download` 开头的路径不受 SPA 回退影响**，由 Express 路由正常处理。
- **上传文件持久化**：`uploads/` 目录中的文件不会自动清理，需定期手动清理或设置 cron 任务。
- **安全性**：
  - 没有内置认证机制，建议生产环境通过 Nginx basic auth 或 IP 白名单限制访问。
  - 上传文件无病毒扫描，不要在生产环境对公网开放上传。
  - CORS 默认允许所有来源（`app.use(cors())`），如需限制请修改 `server/server.js`。

## 构建产物结构

```text
frontend/dist/
├── index.html
├── assets/
│   ├── index-<hash>.js
│   └── index-<hash>.css
└── ...
```

## 验证方式

```bash
# 1. 构建前端
npm run build:client
# 确认 frontend/dist/ 存在
ls frontend/dist/index.html && echo "✅ 构建成功"

# 2. 启动生产模式
NODE_ENV=production node server/server.js &

# 3. 验证
curl -s http://localhost:3000/api/data | grep -q '"code":200' && echo "✅ API 正常"
curl -s http://localhost:3000/ | head -20  # 应返回 React SPA 的 HTML

# 4. 验证 404 回退到 SPA
curl -s http://localhost:3000/some-spa-route | grep -q '<div id="root">' && echo "✅ SPA 回退正常"
```

[返回 README](../README.md)

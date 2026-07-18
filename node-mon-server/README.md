# node-mon-server — 测试服务器

一个基于 Express + Multer 后端 + React 前端的测试服务器，供客户端（Android/iOS/Web）开发调试使用。支持 REST API、文件上传/下载、断点续传，每个端点附带 OkHttp 示例代码。

## 使用场景总览

| 使用场景 | 推荐做法 | 适用条件/支持范围 | 什么情况下使用 | 为什么可以用 |
| --- | --- | --- | --- | --- |
| [快速启动服务](./server/doc/01-quick-start.md) | `npm run dev` 一键启动，或分别启动前后端 | 开发环境，Node.js v16+ | 第一次使用，想快速看到效果 | 根 package.json 已配置 concurrently 并行启动脚本 |
| [测试 REST API](./server/doc/02-api-testing.md) | `GET /api/data`, `GET /api/echo`, `GET /api/user/:id`, `POST /api/json`, `POST /api/form` | 所有平台，无需认证 | 客户端需要调试 HTTP 请求（GET/POST JSON、表单、Query、路径参数） | Express 路由 + JSON/urlencoded 中间件，返回标准 `{code, message, data}` 格式 |
| [测试文件上传](./server/doc/03-file-upload.md) | `POST /upload/single`, `POST /upload/multiple`, `POST /upload/mixed` | 所有平台，最大 10MB/文件，最多 5 个文件 | 客户端需要调试 multipart/form-data 上传，包括文件+文本+JSON 混合上传 | Multer 中间件处理 multipart 解析，diskStorage 持久化到 uploads/ 目录 |
| [测试文件下载](./server/doc/04-file-download.md) | `GET /download/files`（列表），`GET /download/:filename`（下载，支持 Range 断点续传），`GET /download/static/:filename`（静态文件） | 所有平台，Range 请求返回 206，完整请求返回 200 | 客户端需要测试下载、断点续传、分片下载合并 | Express 静态文件中间件 + 手动 Range 解析，`fs.createReadStream` 流式返回 |
| [使用调试日志](./server/doc/05-debug-logging.md) | `DEBUG=app:* node server.js` 或 `DEBUG=app:request,app:upload node server.js` | 开发环境，所有路由 | 排查请求/响应内容、上传过程、性能瓶颈 | `debug` 模块按命名空间分级输出，零侵入，生产环境关闭 |
| [修改配置](./server/doc/06-configuration.md) | 编辑 `server/config/index.js` 或设置环境变量 `PORT`, `HOST` | 所有环境 | 需要改端口、上传限制、性能阈值 | 配置集中在 config/index.js，环境变量可覆盖 |
| [部署到生产环境](./server/doc/07-deployment.md) | `npm run build:client && NODE_ENV=production node server/server.js` | 生产环境，需预先构建前端 | 需要在服务器上对外提供测试服务 | Express 在生产模式下托管前端构建产物，单一端口对外 |
| [使用 React 测试 UI](./frontend/doc/01-test-ui.md) | `cd frontend && npm run dev`，浏览器访问 `http://localhost:5173` | 开发环境，需要浏览器 | 想要可视化界面测试所有端点，而非命令行 curl | Vite + React 构建，通过代理转发 API 请求到后端 |
| [查看 OkHttp 示例代码](./frontend/doc/02-code-examples.md) | 在 React UI 中点击绿色"📱 示例"按钮，或直接查看 `frontend/src/examples.js` | 所有平台 | Android 开发者需要 OkHttp/Kotlin 参考代码 | 每个端点有对应的 OkHttp 示例，涵盖 GET/POST/上传/下载/断点续传 |
| [扩展新接口](./server/doc/02-api-testing.md#扩展新接口) | 在 `server/routes/` 对应文件添加路由，在 `frontend/src/api.js` 添加调用方法 | 开发环境，需了解 Express 和 React | 需要新增自定义测试端点 | 路由按功能拆分，模块化组织，添加新路由不影响现有功能 |

## 快速开始

### 环境要求

- Node.js v16+
- npm 或 yarn

### 安装

```bash
# 克隆项目后
cd node-mon-server
npm install          # 安装后端依赖 + 构建前端
```

### 启动开发环境

**方式一：一键启动（推荐）**

```bash
npm run dev
```

后端启动在 `http://localhost:3000`，前端启动在 `http://localhost:5173`。

**方式二：分别启动**

```bash
# 终端1：后端（带调试日志）
cd server
DEBUG=app:* npx nodemon server.js

# 终端2：前端
cd frontend
npm run dev
```

打开浏览器访问 `http://localhost:5173` 即可使用测试面板。

### 只用后端（不用前端 UI）

```bash
cd server
DEBUG=app:* node server.js
```

然后用 curl 或其他 HTTP 客户端直接请求 `http://localhost:3000` 的各个端点。

## 文档目录

| 文档 | 内容 |
| --- | --- |
| [arch.md](./arch.md) | 项目架构文档：技术栈、目录结构、模块职责、调试命名空间、扩展指南 |
| [server/doc/01. 快速启动](./server/doc/01-quick-start.md) | 后端环境准备、安装依赖、启动方式、环境变量说明、健康检查 |
| [server/doc/02. REST API 测试](./server/doc/02-api-testing.md) | 全部 API 端点详解、curl 示例、请求/响应格式、扩展新接口 |
| [server/doc/03. 文件上传](./server/doc/03-file-upload.md) | 单文件/多文件/混合上传、字段名规范、大小限制、Multer 错误处理 |
| [server/doc/04. 文件下载](./server/doc/04-file-download.md) | 文件列表、完整下载、Range 断点续传、静态文件服务、分片下载 |
| [server/doc/05. 调试日志](./server/doc/05-debug-logging.md) | DEBUG 命名空间、日志输出格式、按模块过滤、性能监控日志 |
| [server/doc/06. 配置参考](./server/doc/06-configuration.md) | 全部配置项说明、环境变量覆盖、上传限制、性能阈值、MIME 过滤 |
| [server/doc/07. 生产部署](./server/doc/07-deployment.md) | 构建前端、生产模式启动、静态文件托管、防火墙配置 |
| [frontend/doc/01. React 测试 UI](./frontend/doc/01-test-ui.md) | 前端启动、代理配置、组件说明、测试面板操作指南 |
| [frontend/doc/02. OkHttp 示例代码](./frontend/doc/02-code-examples.md) | 所有 OkHttp 示例索引、如何替换 IP 地址、代码结构说明 |

## 项目结构

```text
node-mon-server/
├── server/                  # 后端
│   ├── server.js            # 应用入口
│   ├── config/index.js      # 集中配置
│   ├── routes/              # 路由（api, upload, download）
│   ├── middleware/           # 中间件（debug, errorHandler, performance, responseInterceptor）
│   ├── utils/               # 工具（fileHelper, logger）
│   └── uploads/             # 上传文件存储（自动创建）
├── frontend/                # React 前端
│   ├── src/
│   │   ├── components/      # ApiTest, FileUpload, MixedUpload, FileManager, ChunkDownload 等
│   │   ├── api.js           # API 调用封装
│   │   ├── examples.js      # OkHttp 示例代码
│   │   └── App.jsx          # 主应用
│   └── vite.config.js       # Vite 配置（含 API 代理）
├── public/                  # 静态文件（test.html 等）
└── package.json             # 根工程脚本
```

# 测试服务器 - 项目架构文档

## 技术栈

- **Node.js** (v16+)
- **Express** - Web 框架
- **Multer** - 文件上传处理
- **Debug** - 分级调试日志
- **CORS** - 跨域支持
- **React** + **Vite** - 前端界面

## 目录结构
test-server/
├── server/ # 后端代码
│ ├── server.js # 应用入口（包含路由、中间件、配置）
│ ├── uploads/ # 上传文件存储目录（自动创建）
│ └── package.json
├── frontend/ # React 前端项目
│ ├── public/
│ ├── src/
│ │ ├── components/
│ │ │ ├── ApiTest.jsx
│ │ │ ├── FileUpload.jsx
│ │ │ ├── MixedUpload.jsx
│ │ │ ├── FileManager.jsx
│ │ │ ├── ChunkDownload.jsx
│ │ │ ├── DynamicParams.jsx
│ │ │ └── ExampleModal.jsx
│ │ ├── api.js
│ │ ├── examples.js
│ │ ├── App.jsx
│ │ ├── main.jsx
│ │ └── index.css
│ ├── index.html
│ ├── package.json
│ └── vite.config.js
└── package.json # 根目录统一脚本（可选）

text

## 后端模块职责

| 模块 | 文件 | 职责 |
|------|------|------|
| 配置 | 内置于 `server.js` | 端口、上传限制、性能阈值 |
| 日志 | `debug` 模块 | 分级输出请求、响应、上传、性能日志 |
| 请求日志 | 中间件 | 打印请求方法、URL、Headers、Query |
| 性能监控 | 中间件 | 记录请求耗时、multer 处理耗时 |
| 响应拦截 | 中间件 | 拦截 `res.json/send/download`，输出响应内容 |
| 错误处理 | 中间件 | 捕获 Multer 错误及其他异常 |
| API 路由 | 内置于 `server.js` | `/api/data`, `/api/echo`, `/api/user/:id`, `/api/json`, `/api/form` |
| 上传路由 | 内置于 `server.js` | `/upload/single`, `/upload/multiple`, `/upload/mixed` |
| 下载路由 | 内置于 `server.js` | `/download/files`, `/download/:filename`, `/download/static/*` |

## 前端组件结构

| 组件 | 功能 |
|------|------|
| `ApiTest.jsx` | GET/POST JSON、表单、动态参数 |
| `FileUpload.jsx` | 单文件、多文件上传 |
| `MixedUpload.jsx` | 混合上传（文件+文本+JSON+表单） |
| `FileManager.jsx` | 文件列表、下载 |
| `ChunkDownload.jsx` | 分片下载合并（演示断点续传） |
| `DynamicParams.jsx` | 可复用动态键值对输入 |
| `ExampleModal.jsx` | 模态框，展示 OkHttp 示例代码 |

## 调试命名空间

通过环境变量 `DEBUG` 控制输出：

- `app:main`   - 启动信息、重要事件
- `app:request` - 请求详情
- `app:response` - 响应内容
- `app:upload`  - 文件上传过程（字段、文件名、大小等）
- `app:perf`    - 性能数据（请求耗时、multer 耗时）

示例：`DEBUG=app:* node server.js` 输出所有调试信息。

## 扩展指南

- **添加新 API**：在 `server.js` 中添加新路由，或在 `frontend/src/api.js` 中增加调用方法。
- **修改上传限制**：修改 `server.js` 中的 `config.upload.limits.fileSize`。
- **增加文件类型过滤**：在 `server.js` 的 `fileFilter` 函数中增加 MIME 类型判断。
- **新增示例代码**：在 `frontend/src/examples.js` 中添加新的键值对。

## 许可证

MIT

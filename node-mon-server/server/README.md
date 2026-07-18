# node-mon-server — 后端

Express + Multer 测试服务器后端，提供 REST API、文件上传、文件下载端点，支持断点续传。

## 快速开始

```bash
cd server
npm install
DEBUG=app:* node server.js
```

服务启动在 `http://localhost:3000`。

## 文档

- [快速启动](./doc/01-quick-start.md) — 环境准备、启动方式、健康检查
- [REST API 测试](./doc/02-api-testing.md) — 全部 API 端点详解与 curl 示例
- [文件上传](./doc/03-file-upload.md) — 单文件 / 多文件 / 混合上传
- [文件下载](./doc/04-file-download.md) — 完整下载、Range 断点续传、分片下载
- [调试日志](./doc/05-debug-logging.md) — DEBUG 命名空间、日志过滤
- [配置参考](./doc/06-configuration.md) — 全部配置项、环境变量
- [生产部署](./doc/07-deployment.md) — 构建产物、PM2、Nginx 反向代理

## 核心依赖

| 依赖 | 用途 |
| --- | --- |
| `express` | Web 框架 |
| `multer` | multipart/form-data 解析 |
| `cors` | 跨域支持 |
| `debug` | 分级调试日志 |
| `morgan` | HTTP 请求日志（已安装，待启用） |

## 目录结构

```text
server/
├── server.js            # 应用入口
├── config/index.js      # 集中配置
├── routes/              # 路由模块
│   ├── api.js           # /api/* — REST API
│   ├── upload.js        # /upload/* — 文件上传
│   └── download.js      # /download/* — 文件下载
├── middleware/           # 中间件
│   ├── debug.js         # 请求详情日志
│   ├── errorHandler.js  # 全局错误处理
│   ├── performance.js   # 性能监控
│   └── responseInterceptor.js  # 响应内容拦截
├── utils/               # 工具函数
│   ├── fileHelper.js    # 目录创建、文件列表、唯一文件名
│   └── logger.js        # debug 模块封装
├── uploads/             # 上传文件存储（自动创建）
└── doc/                 # 详细文档
```

## 端点速查

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| GET | `/api/data` | 固定 JSON 响应 |
| GET | `/api/echo` | Query 参数回显 |
| GET | `/api/user/:id` | 路径参数 + Query |
| POST | `/api/json` | JSON body 回显 |
| POST | `/api/form` | 表单字段回显 |
| POST | `/upload/single` | 单文件上传（field: `file`） |
| POST | `/upload/multiple` | 多文件上传（field: `files`） |
| POST | `/upload/mixed` | 混合上传（任意字段） |
| GET | `/download/files` | 文件列表 |
| GET | `/download/:filename` | 下载文件（支持 Range） |
| GET | `/download/static/:filename` | 静态文件 |

[返回项目 README](../README.md)

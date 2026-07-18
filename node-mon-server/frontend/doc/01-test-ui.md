# 1. React 测试 UI

使用 React 前端测试面板，以可视化方式调试所有后端端点，无需手写 curl 命令。

## 适用条件

- Node.js v16+
- 后端已启动在 `http://localhost:3000`（或通过代理配置的地址）
- 浏览器访问

## 组件一览

| 组件 | 文件 | 功能 |
| --- | --- | --- |
| `ApiTest` | `src/components/ApiTest.jsx` | GET/POST 测试：无参 GET、Query 参数、路径参数、JSON、表单 |
| `FileUpload` | `src/components/FileUpload.jsx` | 单文件上传 + 多文件上传（最多 5 个） |
| `MixedUpload` | `src/components/MixedUpload.jsx` | 混合上传：文件 + 文本 + JSON + 表单字段 |
| `FileManager` | `src/components/FileManager.jsx` | 文件列表查看 + 手动输入文件名下载 |
| `ChunkDownload` | `src/components/ChunkDownload.jsx` | 分片下载演示（模拟断点续传） |
| `DynamicParams` | `src/components/DynamicParams.jsx` | 可复用的动态键值对输入组件 |
| `ExampleModal` | `src/components/ExampleModal.jsx` | 模态框展示 OkHttp 示例代码 |

## 推荐做法

```text
首次使用       → 启动前后端，浏览器打开 http://localhost:5173，直接点击各区域按钮
测试 API       → ApiTest 区域：切换"获取数据"/"Echo"/"用户信息"/"JSON"/"表单"
测试上传       → FileUpload + MixedUpload 区域
测试下载       → FileManager 查看列表，ChunkDownload 测试分片
查看示例代码   → 点击各区域旁边的 "📱 示例" 绿色按钮
```

## 启动

```bash
# 在 frontend 目录
npm install
npm run dev
```

或从项目根目录一键启动：

```bash
npm run dev
```

打开 `http://localhost:5173`。

## 代理配置

前端 Vite 开发服务器将 API 请求代理到后端，配置在 `frontend/vite.config.js`：

```javascript
export default defineConfig({
  plugins: [react()],
  server: {
    proxy: {
      '/api': 'http://localhost:3000',
      '/upload': 'http://localhost:3000',
      '/download': 'http://localhost:3000'
    }
  }
});
```

前端代码中的 fetch 请求使用相对路径（如 `/api/data`），Vite 自动代理到 `http://localhost:3000`。

## 关键说明

- **前端默认监听 localhost:5173**，仅本机访问。如需局域网访问，在 `vite.config.js` 中添加 `server: { host: '0.0.0.0' }`。
- **代理只影响开发模式**，生产模式下前后端同一端口，无需代理。
- **文件上传组件限制**：单文件/多文件上传组件中的字段名（`file`/`files`）与后端 Multer 配置绑定，不可在前端 UI 修改。
- **OkHttp 示例中的 IP**：`frontend/src/examples.js` 中的 `192.168.1.100` 是占位符，需替换为实际服务器 IP。

## 界面操作流程

### API 测试

1. 在 "API 测试" 区域选择请求类型（数据/Echo/用户/JSON/表单）
2. 填写参数（Query 参数 / JSON body / 表单字段）
3. 点击发送按钮
4. 查看响应区域显示的 JSON 结果

### 文件上传

1. 点击选择文件（或拖拽）
2. 可选：填写额外描述字段
3. 点击上传
4. 查看上传结果（文件名、大小、MIME）

### 分片下载

1. 先通过 FileManager 查看可下载文件列表
2. 在 ChunkDownload 中选择文件
3. 设置分片数量（如 3 片）
4. 点击"分片下载并合并"
5. 浏览器自动下载合并后的完整文件

## 验证方式

```bash
# 启动前端
cd frontend && npm run dev

# 浏览器打开 http://localhost:5173
# 确认页面正确渲染，点击"获取数据"按钮有响应
```

[返回 README](../../README.md)

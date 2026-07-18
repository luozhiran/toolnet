# 2. REST API 测试

使用 node-mon-server 提供的 REST API 端点调试客户端的 GET/POST 请求，覆盖 JSON body、表单、Query 参数、路径参数等常见场景。

## 适用条件

- 服务器已启动（参考 [快速启动](./01-quick-start.md)）
- 客户端能访问服务器地址

## 端点一览

| 方法 | 路径 | 功能 | Content-Type |
| --- | --- | --- | --- |
| GET | `/api/data` | 返回固定 JSON，无参数 | — |
| GET | `/api/echo` | 接收任意 Query 参数并回显 | — |
| GET | `/api/user/:id` | 路径参数 `:id` + 可选 Query | — |
| POST | `/api/json` | 接收 JSON body 并回显 | `application/json` |
| POST | `/api/form` | 接收表单字段并回显 | `application/x-www-form-urlencoded` |
| POST | `/api/submit` | 旧版兼容端点，功能同 `/api/json` | `application/json` |

所有端点返回统一结构：

```json
{
  "code": 200,
  "message": "...",
  "data": { ... },       // 或 received: { ... }
  "timestamp": "..."
}
```

## 推荐做法

```text
对于客户端开发调试：
  GET 类请求 → /api/echo（验证 Query 参数拼装）、/api/user/:id（验证路径参数拼接）
  POST 类请求 → /api/json（验证 JSON 序列化）、/api/form（验证表单编码）

请求格式正确性验证：服务端将收到的参数原样回显在响应中，
客户端对比发送值与 `received` 字段即可确认。
```

## 可复制 Demo

### GET /api/data — 无参数

```bash
curl http://localhost:3000/api/data
```

### GET /api/echo — Query 参数

```bash
# 单个参数
curl "http://localhost:3000/api/echo?name=张三"

# 多个参数
curl "http://localhost:3000/api/echo?name=张三&age=25&city=北京"
```

响应中 `received` 字段回显所有 Query 参数：

```json
{
  "code": 200,
  "message": "Query parameters received",
  "received": { "name": "张三", "age": "25", "city": "北京" },
  "timestamp": "2026-07-18T10:00:00.000Z"
}
```

### GET /api/user/:id — 路径参数 + Query

```bash
# 仅路径参数
curl "http://localhost:3000/api/user/123"

# 路径参数 + Query
curl "http://localhost:3000/api/user/123?name=李四"
```

响应：

```json
{
  "code": 200,
  "message": "User info",
  "data": {
    "userId": "123",
    "query": { "name": "李四" },
    "timestamp": 1752835200000
  }
}
```

### POST /api/json — JSON body

```bash
curl -X POST http://localhost:3000/api/json \
  -H "Content-Type: application/json" \
  -d '{"message":"Hello Server","items":[1,2,3]}'
```

响应中 `received` 字段回显发送的 JSON：

```json
{
  "code": 200,
  "message": "JSON received",
  "received": { "message": "Hello Server", "items": [1, 2, 3] },
  "timestamp": "2026-07-18T10:00:00.000Z"
}
```

### POST /api/form — 表单

```bash
curl -X POST http://localhost:3000/api/form \
  -d "username=alice&password=123456"
```

## 关键说明

- **Query 参数区分大小写**：`?Name=张三` 和 `?name=张三` 是不同的参数。
- **JSON body 必须带正确的 Content-Type**：服务端使用 `express.json()` 中间件，只有 `Content-Type: application/json` 才会解析 body。若忘记设置，`req.body` 为空对象。
- **表单使用 `express.urlencoded({ extended: true })`**，支持嵌套对象语法如 `user[name]=alice`。
- **路径参数 `:id` 匹配任意不含 `/` 的字符串**，中文等特殊字符需客户端做 URL 编码。
- `/api/submit` 是旧版兼容端点，新代码推荐使用 `/api/json`。

## 扩展新接口

在 `server/routes/api.js` 中添加新路由：

```javascript
// GET /api/status — 自定义状态检查
router.get('/status', (req, res) => {
    res.json({
        code: 200,
        message: 'Server is running',
        data: { uptime: process.uptime() }
    });
});
```

然后在 `frontend/src/api.js` 中添加对应的前端调用方法。

## 验证方式

```bash
# 依次验证所有 API 端点
echo "=== GET /api/data ===" && curl -s http://localhost:3000/api/data | head -c 200
echo ""
echo "=== GET /api/echo ===" && curl -s "http://localhost:3000/api/echo?test=1"
echo ""
echo "=== GET /api/user/42 ===" && curl -s "http://localhost:3000/api/user/42"
echo ""
echo "=== POST /api/json ===" && curl -s -X POST http://localhost:3000/api/json -H "Content-Type: application/json" -d '{"ok":true}'
echo ""
echo "=== POST /api/form ===" && curl -s -X POST http://localhost:3000/api/form -d "key=value"
```

[返回 README](../README.md)

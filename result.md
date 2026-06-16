# net 库审查与修正结果

## 源码结构说明

`net` 模块是一个基于 OkHttp 的 Android 网络库，主要分为普通请求和下载两条链路。

- 普通请求入口：`Net` 创建 Get/Post 等 `ParamsBuilder`，最终由 `SendTool` 组装 `Request` 并 `enqueue`。
- OkHttp 配置：`NetConfig` 保存全局参数、拦截器、缓存、日志路径和 UI Handler；`OkhttpManager` 创建或复用 `OkHttpClient`。
- 下载入口：`Download.taskBuilder()` 创建 `Task`，通过 `DispatchTool` 调度等待队列和运行队列。
- 下载执行：`DirectRequest` 处理普通下载，`BreakpointContinuationRequest` 处理断点续传，公共响应流写入逻辑在 `BaseRequest`。
- 生命周期与监听器：`PrincipalLife` 负责 Activity 销毁时取消普通网络请求，`HoldActivityCallbackMap` 保存下载任务的 Activity 相关监听器，`GlobalDownloadProgressCache` 保存全局下载监听器。

## 发现的问题与原因

1. 下载监听器存在并发安全和泄露风险
   - `HoldActivityCallbackMap` 使用全局 `MutableMap<String, MutableList<IProgressCallback>>`，读写没有同步。
   - 下载回调、Activity 销毁移除、任务结束移除可能来自不同线程，遍历时增删会触发 `ConcurrentModificationException`。
   - 同一个 callback 可重复加入，可能造成重复回调和 Activity 引用滞留。

2. Activity 生命周期取消存在内存泄露和竞态
   - `PrincipalLife` 用 `LinkedHashMap<Activity, MutableList<Call>>` 强引用 Activity。
   - `containsKey`、写入、移除没有放在同一个同步区间，多线程请求可能重复创建列表或丢失 Call。
   - 业务回调抛异常时，Call 可能无法从生命周期表移除。

3. 下载响应流和 Response 关闭不完整
   - `BaseRequest.saveNetStream()` 只关闭输出流，没有直接关闭输入流。
   - `DirectRequest` 和 `BreakpointContinuationRequest` 在非 200/206 分支没有关闭 Response。
   - 断点续传的第一次探测请求读取 `contentLength()` 后没有关闭 body，可能占用连接池连接。

4. 主动取消下载语义不完整
   - `Download.cancel()` 先移除运行队列再取消 OkHttp Call，但没有给 `Task` 写入取消标记。
   - OkHttp 取消后的失败回调仍可能走普通失败/重试路径，取消结果不稳定。

5. 下载队列状态不是自同步的
   - `TaskState` 内部等待队列、运行队列和 URL 索引列表是普通 `MutableList`。
   - 外部取消、OkHttp 回调、下载 HandlerThread 都可能访问这些列表，容易出现状态不一致。
   - 快速删除逻辑使用 `position > 0`，队列首元素索引 0 无法被快速删除。

6. MD5 校验逻辑有错误
   - `BaseRequest.lastOneCheck()` 条件写反：MD5 校验通过时反而进入失败分支。
   - `CheckTools.getMD5Three()` 使用 `BigInteger.toString(16)` 会丢失前导 0，导致部分合法 MD5 被判定失败。
   - 文件流手动 close，异常时可能未关闭。

7. 断点续传进度计算和 Range 生成错误
   - Range 结束位表达式 `${fileSize ?: 0 - 1}` 优先级错误。
   - 续传响应 body 的 `contentLength()` 是剩余长度，原代码直接当总长度使用，可能导致进度过早到 100% 并提前重命名。

8. 空值强制解包可能崩溃
   - `ThreadTool.postDelayed/removeCallback` 对可空 Runnable 使用 `!!`。
   - `NetConfig.uiHandler` 在未配置 Application 时使用 `application!!`。
   - 日志文件创建直接访问 `parentFile`，自定义路径无父目录时有空指针风险。

## 修正结果

- `HoldActivityCallbackMap`
  - 增加锁保护所有 map/list 读写。
  - 遍历前复制 callback 快照，避免并发修改异常。
  - callback 去重，业务 callback 异常使用 `Log.w` 隔离，避免单个监听器影响整个下载通知链。

- `PrincipalLife` 和 `SendTool`
  - 将 Activity 到 Call 的映射改为 `WeakHashMap`。
  - 注册、添加、移除 Call 时统一加锁。
  - Activity 销毁时取出 Call 快照后取消，并移除 Lifecycle observer。
  - `SendTool` 注册生命周期后立即清空成员 Activity 引用。
  - 普通请求回调使用 `try/finally`，确保 Response 关闭、Call 移除。

- `BaseRequest`、`DirectRequest`、`BreakpointContinuationRequest`
  - 下载输入流和输出流都使用 `use` 自动关闭。
  - Response 关闭放入 `finally`，失败分支也关闭。
  - 修正 MD5 通过/失败判断。
  - 断点续传总长度改为本地已下载长度 + 响应剩余长度。
  - 修正 Range 起止位生成。

- `Download`、`DispatchTool`、`TaskState`
  - 主动取消运行中任务前写入 `cancelUrl`。
  - 结果处理优先识别主动取消，直接回调取消错误，不再进入重试。
  - `TaskState` 对核心队列操作增加同步。
  - 修复队列索引 0 不能快速删除的问题。

- `CheckTools`、`ThreadTool`、`NetConfig`
  - MD5 输出改为稳定的 32 位小写十六进制，并补空路径保护。
  - `ThreadTool` 对空 Runnable 直接返回。
  - `NetConfig.uiHandler` 未配置 Application 时回退到主 Looper。
  - 日志文件创建统一走安全 helper，处理父目录为空和目录不存在的情况。

## 修改文件

- `net/src/main/java/com/itg/net/Download.kt`
- `net/src/main/java/com/itg/net/download/DispatchTool.kt`
- `net/src/main/java/com/itg/net/download/operations/HoldActivityCallbackMap.kt`
- `net/src/main/java/com/itg/net/download/operations/PrincipalLife.kt`
- `net/src/main/java/com/itg/net/download/operations/TaskState.kt`
- `net/src/main/java/com/itg/net/download/request/BaseRequest.kt`
- `net/src/main/java/com/itg/net/download/request/BreakpointContinuationRequest.kt`
- `net/src/main/java/com/itg/net/download/request/DirectRequest.kt`
- `net/src/main/java/com/itg/net/okhttp/NetConfig.kt`
- `net/src/main/java/com/itg/net/reqeust/SendTool.kt`
- `net/src/main/java/com/itg/net/tools/CheckTools.kt`
- `net/src/main/java/com/itg/net/tools/ThreadTool.kt`

## 验证结果

- 已执行 `.\gradlew.bat :net:compileDebugKotlin`：通过。
- 已执行 `.\gradlew.bat :net:testDebugUnitTest`：通过。
- 剩余提示为项目既有警告：
  - `com.orhanobut:logger:2.2.0` 依赖旧 support annotations。
  - `Environment.getExternalStorageDirectory()` 已弃用。
  - Gradle/AGP 版本存在未来兼容性弃用提示。

## 未处理但建议后续关注

- `NetConfig` 的外部存储日志路径仍使用已弃用 API，建议迁移到 app 私有目录或 MediaStore/SAF。
- 下载完成后如果目标文件已存在，`renameTo` 可能失败；如需覆盖语义，应明确删除旧文件或提供覆盖配置。
- 现有测试覆盖较少，建议补充下载取消、断点续传、MD5 校验、Activity 销毁自动取消的单元或集成测试。

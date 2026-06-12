# net 下载逻辑与运行性能审查结果

## 审查范围

本次只审查并修正 `net` 模块下载链路：

- 下载入口：`Download`、`TaskBuilder`
- 下载调度：`DispatchTool`、`TaskState`
- 下载执行：`BaseRequest`、`DirectRequest`、`BreakpointContinuationRequest`
- 下载通知：`DownloadEndNotify`、`HoldActivityCallbackMap`、`GlobalDownloadProgressCache`

## 发现的问题与原因

1. 断点续传多发一次完整 GET 探测请求
   - 原逻辑先 GET 一次拿 `contentLength()`，关闭响应后再发 Range 请求。
   - 这会增加一次网络 RTT 和服务端负载，大文件或弱网下启动续传更慢。
   - 若服务端不支持 Range，第二次请求才失败，浪费一次请求。

2. 下载写入循环存在不必要的磁盘元数据读取
   - 原逻辑每写入 4KB 调一次 `file.length()` 更新进度。
   - `file.length()` 会读文件系统元数据，频率很高时会拖慢下载线程。
   - 4KB buffer 对大文件吞吐偏小，系统调用次数较多。

3. EOF 时未达到 100% 会导致任务卡住
   - 如果 `contentLength <= 0`、服务端未返回长度、或实际读取长度小于声明长度，循环结束后原逻辑没有统一收尾。
   - 结果是既不成功也不失败，运行队列里的任务可能长期占位，后续等待任务不能执行。

4. 断点续传任务的局部监听器可能丢失
   - `TaskBuilder.start()` 只在普通下载分支添加 `HoldActivityCallbackMap` 监听器。
   - append 下载分支直接启动，导致通过 `setDownloadListener()` 设置的监听器收不到进度。

5. 取消运行中任务后等待队列补位不及时
   - `Download.cancel()` 删除运行队列并取消 OkHttp Call 后，没有主动触发下一任务调度。
   - 需要等 OkHttp 取消失败回调回来才会继续执行，极端情况下如果没有匹配到 Call，队列会空转等待。

6. 调度器忽略运行队列添加失败
   - `DispatchTool` 调用 `addRunningTask()` 后不检查返回值。
   - 如果相同 URL 已在运行队列，仍可能继续发起请求，造成重复下载或状态不一致。

7. `maxDownloadNum <= 0` 会让下载队列永远不执行
   - `TaskState` 直接使用配置值。
   - 当调用方误传 0 或负数时，`runningQueueCanAcceptTask()` 永远为 false，任务只会进入等待队列。

8. 调试日志影响运行性能和日志质量
   - `TaskBuilder` 中有硬编码 `Log.e("MainActivity", ...)`。
   - 下载任务频繁创建时会产生无意义 error 日志，影响性能诊断。

9. 完成文件重命名逻辑不够稳
   - 原逻辑用 `replace(".tmp", "")` 生成目标路径，可能替换路径中间的 `.tmp`。
   - 目标文件已存在时 `renameTo()` 可能失败，导致重复下载同一文件时失败。

## 修正结果

- `BreakpointContinuationRequest`
  - 移除首次 GET 探测请求。
  - 直接按本地 `.tmp` 文件长度发送 `Range: bytes=<start>-`。
  - 服务端返回 `206` 时按续传处理；本地长度为 0 且返回 `200` 时按完整下载处理；本地已有数据但服务端忽略 Range 返回 `200` 时按不支持断点续传失败。

- `BaseRequest`
  - 下载 buffer 从 4KB 提升到 32KB，减少读写系统调用。
  - 使用内存中的 `writtenSize` 累加下载大小，不再每个分片调用 `file.length()`。
  - 循环 EOF 后统一收尾：长度未知或已写满时执行成功校验；长度不足时回调 `"下载数据不完整"`，允许调度器按重试策略处理。
  - 完成重命名使用 `removeSuffix(".tmp")`，只移除文件后缀。
  - 目标文件存在时先删除，避免重复下载同一目标文件时 `renameTo()` 失败。

- `TaskBuilder`
  - 在普通下载和 append 下载之前统一注册局部监听器，修复 append 下载监听丢失。
  - `tryAgainCount()` 对小于 1 的值兜底为 1，避免配置错误导致重试计数语义异常。
  - 增加 `overwrite(Boolean)` 显式控制目标文件已存在时是否覆盖，默认不覆盖。
  - 移除硬编码 error 日志和无用 import。

- `DispatchTool`
  - 检查 `addRunningTask()` 返回值，添加失败时不再继续启动请求。
  - 等待队列添加成功后才发送调度消息。
  - 修复消息对象非 `Task` 时强转崩溃风险。
  - 增加 `continueDownload()`，给取消入口主动触发队列补位。

- `Download`
  - 取消运行中任务后主动调用 `dispatchTool.continueDownload()`，等待队列能更快补位。

- `TaskState`
  - `maxDownloadNum` 使用 `coerceAtLeast(1)` 兜底，避免错误配置导致下载完全停摆。

## 修改文件

- `net/src/main/java/com/itg/net/Download.kt`
- `net/src/main/java/com/itg/net/download/DispatchTool.kt`
- `net/src/main/java/com/itg/net/download/TaskBuilder.kt`
- `net/src/main/java/com/itg/net/download/data/Tag.kt`
- `net/src/main/java/com/itg/net/download/data/Task.kt`
- `net/src/main/java/com/itg/net/download/operations/TaskState.kt`
- `net/src/main/java/com/itg/net/download/request/BaseRequest.kt`
- `net/src/main/java/com/itg/net/download/request/BreakpointContinuationRequest.kt`

## 验证结果

- 已执行 `.\gradlew.bat :net:compileDebugKotlin`：通过。
- 已执行 `.\gradlew.bat :net:testDebugUnitTest`：通过。

剩余输出为项目既有警告：

- `com.orhanobut:logger:2.2.0` 依赖旧 support annotations。
- Gradle/AGP 存在未来版本兼容性弃用提示。

## 覆盖策略使用方式

- 默认行为：目标文件已存在时不覆盖，直接回调 `ERROR_TARGET_FILE_EXISTS = "目标文件已存在，未开启覆盖"`。
- 允许覆盖：调用 `Download.instance.taskBuilder().overwrite(true)`，下载完成后会删除旧目标文件再重命名 `.tmp` 文件。
- 明确不覆盖：调用 `overwrite(false)`，与默认行为一致。

## 仍建议后续补充

- 增加真实下载集成测试：普通下载、断点续传、Range 不支持、长度未知、长度不足、取消后队列补位。
- 如果下载文件很大并需要更平滑 UI，可把进度回调从“百分比变化”改为“百分比变化或固定时间间隔”，避免小文件回调过少、大文件回调过密。

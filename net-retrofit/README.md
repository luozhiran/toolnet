# net-retrofit

`net-retrofit` 基于 Retrofit 提供声明式接口能力，并复用 `Net.instance` 的 OkHttpClient。它适合接口数量多、希望通过注解定义 API 的 Kotlin 项目。

## 使用场景总览

| 使用场景 | 推荐 API | 适用条件 | 什么时候使用 | 关键约束 |
| --- | --- | --- | --- | --- |
| [声明式 API](./doc/01-retrofit-basics.md) | `Net.instance.retrofit.baseUrl(...).build().create<T>()` | 已使用 Retrofit 注解 | 接口多、路径和参数适合写在 Service 中 | `baseUrl` 必须以 `/` 结尾 |
| [Flow 结果模型](./doc/02-retrofit-advanced.md) | `Flow<T>` / `Flow<NetResponse<T>>` / `Flow<NetResult>` / `Flow<BusinessResult>` / `Flow<TypedBusinessResult<T>>` | Kotlin Flow | 需要和 `net-flow` 保持一致的错误模型 | 普通 `suspend` 仍按 Retrofit 默认行为工作 |

## 文档目录

| 文档 | 内容 |
| --- | --- |
| [01. Retrofit 基础](./doc/01-retrofit-basics.md) | 构建 Service、复用 Net 配置、普通接口 |
| [02. Retrofit 进阶](./doc/02-retrofit-advanced.md) | Flow 适配、结果模型、自定义 Converter 和 CallAdapter |

## 最小示例

```kotlin
interface UserService {
    @GET("user/profile")
    fun profile(): Flow<NetResult>
}

val service = Net.instance.retrofit
    .baseUrl("https://api.example.com/")
    .build()
    .create<UserService>()
```

`NetRetrofit` 默认会：

- 使用 `Net.instance.okhttpManager.okHttpClient`。
- 添加自定义 Converter 后，仍保留 Gson 兜底，除非你自己已经添加了 `GsonConverterFactory`。
- 添加自定义 CallAdapter 后，仍保留 `NetFlowCallAdapterFactory`，除非你自己已经添加了它。

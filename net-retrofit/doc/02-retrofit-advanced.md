# 10. Retrofit 高级配置

如何自定义 Retrofit 的 Converter、CallAdapter，使用多个 Base URL，以及管理多个 Service 实例。

## 适用条件

- 已完成 Retrofit 基础配置（见 [01-Retrofit 基础](./01-retrofit-basics.md)）
- 需要使用 Gson 以外的序列化库（Moshi、Jackson、kotlinx.serialization）
- 需要同时对接多个不同 Base URL 的后端服务
- 需要使用 RxJava 等自定义 CallAdapter

## 推荐做法

### 自定义 Converter

#### 使用 Moshi 代替 Gson

```kotlin
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import retrofit2.converter.moshi.MoshiConverterFactory

val moshi = Moshi.Builder()
    .addLast(KotlinJsonAdapterFactory())
    .build()

val service = Net.instance.retrofit
    .baseUrl("https://api.example.com/")
    .addConverterFactory(MoshiConverterFactory.create(moshi))
    .build()
    .create<UserService>()
```

> 需要添加依赖：`com.squareup.retrofit2:converter-moshi:2.9.0`

#### 自定义 Gson 实例

```kotlin
val gson = GsonBuilder()
    .setDateFormat("yyyy-MM-dd HH:mm:ss")
    .setFieldNamingPolicy(FieldNamingPolicy.LOWER_CASE_WITH_UNDERSCORES)
    .serializeNulls()
    .create()

// 注意：调用了 addConverterFactory 后，默认的 GsonConverterFactory 不会自动注册
val retrofit = Net.instance.retrofit
    .baseUrl("https://api.example.com/")
    .addConverterFactory(GsonConverterFactory.create(gson))
    .build()
```

#### 添加 Scalars Converter（支持纯文本/String 返回值）

当接口返回纯文本而非 JSON 时需要：

```kotlin
Net.instance.retrofit
    .baseUrl("https://api.example.com/")
    .addConverterFactory(ScalarsConverterFactory.create())   // 先匹配 String/Int 等基本类型
    .addConverterFactory(GsonConverterFactory.create())      // 再匹配 JSON→对象
    .build()
```

> `converter-scalars:2.9.0` 已内置在 `net-retrofit` 模块依赖中。

> Retrofit 会按添加顺序依次尝试 Converter，找到第一个能处理的为止。

### 自定义 CallAdapter

#### 添加 RxJava 适配器

```kotlin
val retrofit = Net.instance.retrofit
    .baseUrl("https://api.example.com/")
    .addCallAdapterFactory(RxJava3CallAdapterFactory.create())
    .build()
```

#### 保持 NetFlowCallAdapterFactory

如果添加自定义 CallAdapter 后仍想使用 Flow 返回类型，需要**同时添加**：

```kotlin
val retrofit = Net.instance.retrofit
    .baseUrl("https://api.example.com/")
    .addCallAdapterFactory(NetFlowCallAdapterFactory())       // 支持 Flow<T>/NetResponse/NetResult/BusinessResult/TypedBusinessResult
    .addCallAdapterFactory(RxJava3CallAdapterFactory.create()) // 支持 RxJava
    .build()
```

> **注意**：一旦调用了 `addCallAdapterFactory()`，默认的 `NetFlowCallAdapterFactory` 不会被自动注册，需要手动添加，否则 `Flow<T>`、`Flow<NetResult>`、`Flow<BusinessResult>`、`Flow<TypedBusinessResult<T>>` 等返回类型都不可用。

### 多个 Service 实例管理

#### 同一 Base URL 共享实例（推荐）

```kotlin
val retrofit = Net.instance.retrofit
    .baseUrl("https://api.example.com/")
    .build()

val userService = retrofit.create<UserService>()
val orderService = retrofit.create<OrderService>()
val productService = retrofit.create<ProductService>()
```

#### 不同 Base URL 的多个实例

```kotlin
val apiRetrofit = Net.instance.retrofit
    .baseUrl("https://api.example.com/")
    .build()

val cdnRetrofit = Net.instance.retrofit
    .baseUrl("https://cdn.example.com/")
    .build()

val userService = apiRetrofit.create<UserService>()
val fileService = cdnRetrofit.create<FileService>()
```

#### 独立 OkHttpClient

某些 Service 需要独立的超时或拦截器配置：

```kotlin
val customClient = OkHttpClient.Builder()
    .connectTimeout(30, TimeUnit.SECONDS)
    .readTimeout(60, TimeUnit.SECONDS)
    .addInterceptor(specialInterceptor)
    .build()

val service = Net.instance.retrofit
    .baseUrl("https://api.example.com/")
    .client(customClient)  // 使用独立 OkHttpClient，覆盖全局配置
    .build()
    .create<UserService>()
```

## 与 Net 全局配置的继承关系

| 配置项 | 自动继承 | 说明 |
|---|---|---|
| OkHttpClient（拦截器、超时、缓存） | ✅ | 通过 `Net.instance.okhttpManager.okHttpClient` |
| globalParams | ❌ | 全局参数只影响 Builder 模式请求，Retrofit 接口需自行传参 |
| Base URL（NetConfig.url） | ❌ | Retrofit 需要独立的 `baseUrl()` 设置 |
| HTTP 日志 | ✅ | 通过共享 OkHttpClient 的拦截器生效 |
| 加密拦截器 | ✅ | EncryptInterceptor 在 OkHttpClient 中生效 |
| 监控拦截器 | ✅ | MonitorInterceptor 在 OkHttpClient 中生效 |

## NetRetrofit.Builder 完整 API

```kotlin
// 两种入口等价
val builder1 = NetRetrofit.builder()        // 静态工厂方法
val builder2 = Net.instance.retrofit        // Net 扩展属性（推荐）
```

| 方法 | 说明 |
|---|---|
| `NetRetrofit.builder()` | 静态工厂，创建 Builder 实例 |
| `Net.instance.retrofit` | 扩展属性，等价于 `NetRetrofit.builder()`（推荐日常使用） |
| `baseUrl(url: String)` | 设置 Base URL（**必选**，须以 `/` 结尾） |
| `client(client: OkHttpClient)` | 自定义 OkHttpClient（默认使用 Net 库全局 client） |
| `addConverterFactory(factory)` | 添加 Converter.Factory（默认 `GsonConverterFactory`） |
| `addCallAdapterFactory(factory)` | 添加 CallAdapter.Factory（默认 `NetFlowCallAdapterFactory`） |
| `build(): NetRetrofit` | 构建 `NetRetrofit` 实例 |
| `NetRetrofit.create<T>(): T` | 创建 Service 接口的动态代理实现（在 `NetRetrofit` 上，不在 `Builder` 上） |

## ProGuard / R8 规则

如果启用了代码混淆，`net-retrofit` 模块的 `consumer-rules.pro` 已默认包含必要规则：

```proguard
# Retrofit
-keepattributes Signature
-keepattributes *Annotation*
-keep,allowobfuscation interface * {
    @retrofit2.http.* <methods>;
}

# Gson
-keepattributes Signature
-keepattributes *Annotation*
-dontwarn sun.misc.**
-keep class com.google.gson.** { *; }

# 保持你的数据类不被混淆
-keep class com.yourpackage.model.** { *; }
```

## 关键说明

- `addConverterFactory` 和 `addCallAdapterFactory` 会追加到列表末尾（不是替换），Retrofit 按顺序匹配
- 调用了 `addConverterFactory` 或 `addCallAdapterFactory` 后，对应的默认工厂不会自动注册
- 自定义 `OkHttpClient` 完全覆盖全局配置，不会合并
- `baseUrl` 必须以 `/` 结尾，否则 Retrofit 会抛异常
- 多个 Retrofit 实例共享连接池不会造成资源浪费

## 验证方式

- 使用自定义 Gson 配置（如日期格式）发请求，检查反序列化结果
- 使用 Moshi Converter 确认可正常工作
- 创建多个 Base URL 的 Service，确认各自独立工作

[返回 README](../../README.md)

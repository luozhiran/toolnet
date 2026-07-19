package com.itg.net.retrofit

import com.itg.net.Net
import com.itg.net.flow.NetResponse
import okhttp3.OkHttpClient
import retrofit2.CallAdapter
import retrofit2.Converter
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

/**
 * Retrofit 声明式 API 构建器
 *
 * 基于 [Net.instance.okhttpManager.okHttpClient] 提供与现有网络配置一致的
 * Retrofit 实例，支持通过 Kotlin 接口 + 注解的方式定义网络 API。
 *
 * ## 使用示例
 * ```
 * // 1. 定义 Service 接口
 * interface UserService {
 *     @GET("user/{id}")
 *     suspend fun getUser(@Path("id") id: String): User
 *
 *     @POST("user/login")
 *     suspend fun login(@Body body: LoginRequest): NetResponse<LoginResponse>
 *
 *     @GET("events")
 *     fun eventStream(): Flow<Event>
 * }
 *
 * // 2. 构建实例
 * val userService = Net.retrofit
 *     .baseUrl("https://api.example.com")
 *     .build()
 *     .create<UserService>()
 *
 * // 3. 使用
 * lifecycleScope.launch {
 *     val user = userService.getUser("123")
 * }
 * ```
 */
class NetRetrofit private constructor(
    val retrofit: Retrofit
) {

    companion object {
        /**
         * 创建 [Builder] 实例
         */
        @JvmStatic
        fun builder(): Builder = Builder()
    }

    /**
     * NetRetrofit 构建器
     *
     * 默认行为：
     * - 使用 [Net.instance.okhttpManager.okHttpClient] 作为 OkHttp 客户端
     * - 注册 [GsonConverterFactory] 作为 JSON 转换器
     * - 注册 [NetFlowCallAdapterFactory] 支持 Flow 返回类型
     */
    class Builder {
        private var baseUrl: String? = null
        private var okHttpClient: OkHttpClient? = null
        private val converterFactories = mutableListOf<Converter.Factory>()
        private val callAdapterFactories = mutableListOf<CallAdapter.Factory>()

        /**
         * 设置 Base URL，必须以 `/` 结尾
         */
        fun baseUrl(url: String): Builder {
            this.baseUrl = url
            return this
        }

        /**
         * 自定义 OkHttpClient，不设置则使用 [Net.instance.okhttpManager.okHttpClient]
         */
        fun client(client: OkHttpClient): Builder {
            this.okHttpClient = client
            return this
        }

        /**
         * 添加自定义 [Converter.Factory]（如 MoshiConverterFactory）
         */
        fun addConverterFactory(factory: Converter.Factory): Builder {
            converterFactories.add(factory)
            return this
        }

        /**
         * 添加自定义 [CallAdapter.Factory]（如 RxJava3CallAdapterFactory）
         */
        fun addCallAdapterFactory(factory: CallAdapter.Factory): Builder {
            callAdapterFactories.add(factory)
            return this
        }

        /**
         * 构建 [NetRetrofit] 实例
         *
         * @throws IllegalStateException 如果未设置 baseUrl
         */
        fun build(): NetRetrofit {
            val client = okHttpClient ?: Net.instance.okhttpManager.okHttpClient

            val retrofitBuilder = Retrofit.Builder()
                .baseUrl(baseUrl ?: throw IllegalStateException("baseUrl must be set"))
                .client(client)

            // Converter：用户自定义优先，否则默认 Gson
            converterFactories.forEach { retrofitBuilder.addConverterFactory(it) }
            if (converterFactories.none { it is GsonConverterFactory }) {
                retrofitBuilder.addConverterFactory(GsonConverterFactory.create())
            }

            // CallAdapter：用户自定义优先，否则默认 NetFlowCallAdapterFactory + Retrofit 内置
            callAdapterFactories.forEach { retrofitBuilder.addCallAdapterFactory(it) }
            if (callAdapterFactories.none { it is NetFlowCallAdapterFactory }) {
                retrofitBuilder.addCallAdapterFactory(NetFlowCallAdapterFactory())
            }

            return NetRetrofit(retrofitBuilder.build())
        }
    }

    /**
     * 创建 Service 接口的代理实现
     *
     * @param T Service 接口类型
     * @return Retrofit 动态代理实例
     */
    inline fun <reified T> create(): T {
        return retrofit.create(T::class.java)
    }
}

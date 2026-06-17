package com.itg.net.retrofit

import com.itg.net.Net

/**
 * Retrofit 模块便捷入口
 *
 * 通过扩展属性在 [Net] 单例上提供 Retrofit 构建器入口，
 * 无需直接依赖 Retrofit 相关类即可开始配置。
 *
 * ## 使用示例
 * ```
 * val api = Net.retrofit
 *     .baseUrl("https://api.example.com")
 *     .build()
 *     .create<ApiService>()
 * ```
 */
val Net.retrofit: NetRetrofit.Builder
    get() = NetRetrofit.builder()

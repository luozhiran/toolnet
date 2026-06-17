package com.itg.net.flow.converter

import com.google.gson.Gson
import java.lang.reflect.Type

/**
 * 基于 Gson 的 JSON 转换器
 *
 * ## 使用示例
 * ```
 * Net.instance.get()
 *     .url("https://api.example.com/user/1")
 *     .flowResponse(GsonNetConverter<User>(type = User::class.java))
 *     .collect { response -> ... }
 * ```
 *
 * @param T 目标类型
 * @property gson Gson 实例，默认使用 [Gson] 无参构造
 * @property type 目标类型的 [Type] 令牌
 */
class GsonNetConverter<T>(
    private val gson: Gson = Gson(),
    private val type: Type
) : NetConverter<T> {

    override fun convert(raw: String?): T? {
        return try {
            raw?.let { gson.fromJson(it, type) }
        } catch (e: Exception) {
            null
        }
    }
}

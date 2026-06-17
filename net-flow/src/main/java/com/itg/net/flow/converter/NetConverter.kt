package com.itg.net.flow.converter

/**
 * 响应体字符串 → 业务对象转换器
 *
 * 用于将原始 JSON/XML 等字符串反序列化为类型 [T]，
 * 内置提供 [GsonNetConverter]。
 *
 * @param T 目标类型
 */
interface NetConverter<T> {

    /**
     * 将原始响应字符串转换为类型 [T]
     *
     * @param raw 响应体字符串，可能为 null
     * @return 反序列化后的对象，转换失败时返回 null
     */
    fun convert(raw: String?): T?
}

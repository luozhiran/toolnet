package com.itg.net.request.business

import com.google.gson.Gson
import java.lang.reflect.Type

/**
 * 基于 Gson 的默认业务数据转换器。
 */
class GsonBusinessDataConverter(
    private val gson: Gson = Gson()
) : BusinessDataConverter {

    override fun convert(raw: String?, type: Type): Any? {
        if (raw.isNullOrBlank()) return null
        return gson.fromJson<Any?>(raw, type)
    }
}

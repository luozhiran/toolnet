package com.itg.net.request.business

import java.lang.reflect.Type

/**
 * 业务数据转换器。
 *
 * 用于把 [ApiEnvelope.dataRaw] 转换成调用方需要的数据模型。
 */
interface BusinessDataConverter {
    fun convert(raw: String?, type: Type): Any?
}

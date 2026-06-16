package com.itg.net.util

import android.net.Uri

object UrlTools {

    const val POUND_SIGN_TRUNCATION_TAG = "#"
    const val DOLLAR_TRUNCATION_TAG = "$"

    /**
     * 把参数和url拼接在一起
     * @param pastMap HashMap<String, Any?>?
     * @param paramsStr String?
     * @param url String url不能为null
     * @return String?
     */
    @JvmStatic
    fun getSpliceUrl(pastMap: MutableMap<String, Any?>?, url: String): String {
        val urlBuilder = Uri.parse(url).buildUpon()
        pastMap?.forEach { entry ->
            urlBuilder.appendQueryParameter(entry.key, entry.value.toString())
        }
        return urlBuilder.build().toString()
    }

    /**
     * 把[pastMap]集合中的数据合并到str的前面
     * @param pastMap HashMap<String, Any?>?
     * @param urlParams String?
     * @return String?
     */
    @JvmStatic
    fun mapKeyValueToStrAppend(pastMap: Map<String, Any?>?, urlParams: String?): String? {
        if (pastMap == null) return urlParams
        val resultParams = StringBuilder()
        pastMap.forEach { entry ->
            val paramEntry = "${entry.key}$POUND_SIGN_TRUNCATION_TAG${entry.value}"
            if (!resultParams.contains(paramEntry)) {
                resultParams.append(paramEntry).append(DOLLAR_TRUNCATION_TAG)
            }
        }
        resultParams.append(urlParams.orEmpty())
        return resultParams.toString()
    }

    /**
     * 把[urlParams]分割成键值对[MutableMap]形式，入参必须是[mapKeyValueToStrAppend]
     * 和[appendUrlParamsToStr]方法产生的字符串
     * @param urlParams String?
     * @return MutableMap<String, Any?>?
     */
    @JvmStatic
    fun cutOffStrToMap(urlParams: String?): MutableMap<String, Any?>? {
        if (urlParams.isNullOrBlank()) return null
        val resultMap = mutableMapOf<String, Any?>()
        urlParams.split(DOLLAR_TRUNCATION_TAG).forEach { param ->
            val keyValue = param.split(POUND_SIGN_TRUNCATION_TAG, limit = 2)
            if (keyValue.size == 2) {
                resultMap[keyValue[0]] = keyValue[1]
            }
        }
        return resultMap
    }

     /**
     * 把[key] 和 [value]键值对以及分隔符[POUND_SIGN_TRUNCATION_TAG]拼接到
     * [parentStringBuilder]上并返回
     * @param parentStringBuilder StringBuilder
     * @param key String?
     * @param value String?
     * @return StringBuilder
     */
    @JvmStatic
    fun appendUrlParamsToStr(parentStringBuilder: StringBuilder, key: String?, value: String?): StringBuilder {
        if (key.isNullOrBlank() || value == null) return parentStringBuilder
        parentStringBuilder
            .append(key)
            .append(POUND_SIGN_TRUNCATION_TAG)
            .append(value)
            .append(DOLLAR_TRUNCATION_TAG)
        return parentStringBuilder
    }
}

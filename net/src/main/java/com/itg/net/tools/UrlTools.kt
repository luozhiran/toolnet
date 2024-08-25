package com.itg.net.tools

import android.net.Uri
import java.lang.StringBuilder
import java.util.HashMap

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
    fun getSpliceUrl(pastMap:MutableMap<String, Any?>?,url:String): String {
        val urlBuild = Uri.parse(url).buildUpon()
        pastMap?.forEach{ entry: Map.Entry<String, Any?> ->
            urlBuild.appendQueryParameter(entry.key,entry.value.toString())
        }
        return urlBuild.build().toString()

    }

    /**
     * 把[pastMap]集合中的数据合并到str的前面
     * @param pastMap HashMap<String, Any?>?
     * @param urlParams String?
     * @return String?
     */
    @JvmStatic
    fun mapKeyValueToStrAppend(pastMap: HashMap<String, Any?>?, urlParams:String?): String?{
        if (pastMap == null) return urlParams
        val resultUrl = StringBuilder()
        val iterator = pastMap.entries.iterator()
        var entry : MutableMap.MutableEntry<String,Any?> ? = null
        var tempStr : String? = null
        while (iterator.hasNext()) {
            entry = iterator.next()
            tempStr = "${entry.key}$POUND_SIGN_TRUNCATION_TAG${entry.value}"
            if (!resultUrl.contains(tempStr)) {
                resultUrl.append(tempStr).append(DOLLAR_TRUNCATION_TAG)
            }
        }
        resultUrl.append(urlParams)
        return resultUrl.toString()
    }

    /**
     * 把[urlParams]分割成键值对[MutableMap]形式，入参必须是[mapKeyValueToStrAppend]
     * 和[appendUrlParamsToStr]方法产生的字符串
     * @param urlParams String?
     * @return MutableMap<String, Any?>?
     */
    @JvmStatic
    fun cutOffStrToMap(urlParams:String?): MutableMap<String, Any?>? {
        if (urlParams.isNullOrBlank()) return null
        val firstSplit = urlParams.split(DOLLAR_TRUNCATION_TAG)
        if (firstSplit.isNullOrEmpty()) return null
        val resultMap = mutableMapOf<String,Any?>()
        var tempArray:List<String>? = null
        firstSplit.forEach { secondStr->
            tempArray = secondStr.split(POUND_SIGN_TRUNCATION_TAG)
            val notExec = tempArray.isNullOrEmpty() || tempArray?.size != 2
            if (!notExec){
                resultMap[tempArray!![0]] = tempArray!![1]
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
    fun appendUrlParamsToStr(parentStringBuilder:StringBuilder,key: String?, value: String?) :StringBuilder{
        if (key.isNullOrBlank() || value == null) return parentStringBuilder
        parentStringBuilder.append(key).append(POUND_SIGN_TRUNCATION_TAG).append(value).append(DOLLAR_TRUNCATION_TAG)
        return parentStringBuilder
    }


}
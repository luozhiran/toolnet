package com.itg.net.reqeust.base

import java.io.File

/**
 * Post特有的参数
 */
interface PostBuilder : Builder {
    fun addFile(file: File?): Builder
    fun addFile(fileName: String?, file: File?): Builder
    fun addFile(fileName: String?, mediaType: String?, file: File?): Builder
    fun addContent(content: String?, mediaType: String?): Builder
    fun addContent(content: String?, contentFlag: String?, mediaType: String?): Builder
    fun addJson(key:String?,value:Any?): Builder
}
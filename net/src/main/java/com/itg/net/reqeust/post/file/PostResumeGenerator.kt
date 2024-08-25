package com.itg.net.reqeust.post.file

import com.itg.net.reqeust.body.IntervalBody
import com.itg.net.reqeust.base.ParamsBuilder
import okhttp3.RequestBody
import java.io.File

abstract class PostResumeGenerator: PostFileBuilder() {

    fun addFile(file: File?): ParamsBuilder {
        return super.addFile1(file)

    }
    fun addResumeFileOffset(intervalOffset:Long) = super.addResumeFileOffset1(intervalOffset)

    override fun getRequestBody(): RequestBody? {
        getFile(0)?.let {
            return IntervalBody(it, getResumeFileOffset1())
        }
        return null

    }
}
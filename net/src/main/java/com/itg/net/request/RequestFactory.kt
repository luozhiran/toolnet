package com.itg.net.request

import com.itg.net.ModeType
import com.itg.net.request.get.Get
import com.itg.net.request.post.multipart.PostMul
import com.itg.net.request.base.ParamsBuilder
import com.itg.net.request.post.content.PostContent
import com.itg.net.request.post.file.PostFile
import com.itg.net.request.post.file.PostResumeFile
import com.itg.net.request.post.form.PostForm
import com.itg.net.request.post.json.PostJson

fun create(type: ModeType): ParamsBuilder {
    return when (type) {
        ModeType.Get -> Get()
        ModeType.PostMul -> PostMul()
        ModeType.PostJson -> PostJson()
        ModeType.PostForm -> PostForm()
        ModeType.PostFile -> PostFile()
        ModeType.PostContent -> PostContent()
        ModeType.PostResume -> PostResumeFile()
    }
}

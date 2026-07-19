package com.itg.net.request.post

import com.itg.net.request.post.file.PostFile
import com.itg.net.request.post.json.PostJson
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test

class PostBuilderEdgeTest {

    @Test
    fun postJsonInvalidJsonStringFailsFast() {
        assertThrows(IllegalArgumentException::class.java) {
            PostJson().addJsonStr("{invalid")
        }
    }

    @Test
    fun postFileWithoutFileDoesNotBuildGetRequest() {
        val call = PostFile()
            .url("https://example.com/upload")
            .buildCall()

        assertNull(call)
    }
}

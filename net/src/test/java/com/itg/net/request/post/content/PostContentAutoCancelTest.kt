package com.itg.net.request.post.content

import android.app.Activity
import org.junit.Assert.assertSame
import org.junit.Test

class PostContentAutoCancelTest {

    @Test
    fun autoCancelStoresActivityForLifecycleBinding() {
        val activity = Activity()
        val request = PostContent()

        request.autoCancel(activity)

        assertSame(activity, request.autoCancelActivity)
    }
}

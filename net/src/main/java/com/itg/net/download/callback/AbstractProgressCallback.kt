package com.itg.net.download.callback

import com.itg.net.download.data.Task

/**
 * IProgressCallback 的默认空实现适配器
 *
 * 为所有回调方法提供空方法体，调用方（尤其是 Java）只需覆写关心的回调即可。
 *
 * ## Java 使用示例
 * ```java
 * builder.listener(new AbstractProgressCallback() {
 *     @Override
 *     public void onProgress(@NonNull Task task, boolean complete) {
 *         // 只处理进度
 *     }
 * });
 * ```
 *
 * ## Kotlin 使用示例
 * ```kotlin
 * builder.listener(object : AbstractProgressCallback() {
 *     override fun onProgress(task: Task, complete: Boolean) {
 *         // 只处理进度
 *     }
 * })
 * ```
 */
abstract class AbstractProgressCallback : IProgressCallback {

    override fun onConnecting(task: Task) {
        // 空实现，子类按需覆写
    }

    override fun onProgress(task: Task, complete: Boolean) {
        // 空实现，子类按需覆写
    }

    override fun onFail(error: String?, task: Task) {
        // 空实现，子类按需覆写
    }

    override fun onFinish(task: Task) {
        // 空实现，子类按需覆写
    }
}

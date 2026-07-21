package com.itg.net.download.operations

import android.content.ComponentName
import android.content.Intent
import android.os.Build
import android.util.Log
import com.itg.net.BROAD_ACTION
import com.itg.net.Net
import com.itg.net.download.callback.IProgressCallback
import com.itg.net.download.data.DOWNLOAD_DEBUG_TAG
import com.itg.net.download.data.ERROR_DOWNLOAD_CANCELED
import com.itg.net.download.data.Task

internal class DownloadEventDispatcher(
    private val listenerRegistry: DownloadListenerRegistry
) {

    fun dispatch(event: DownloadEvent) {
        val task = event.task
        val listeners = listenerRegistry.listenersFor(task)
        if (event is DownloadEvent.Failed && task.contentLength > 0L && event.error != ERROR_DOWNLOAD_CANCELED) {
            dispatchProgress(listeners, task, complete = false)
        }
        when (event) {
            is DownloadEvent.Connecting -> dispatchConnecting(listeners, task)
            is DownloadEvent.Progress -> {
                dispatchProgress(listeners, task, event.complete)
                if (event.complete) {
                    sendBroadcast(task)
                }
            }
            is DownloadEvent.Failed -> dispatchFailed(listeners, task, event.error)
            is DownloadEvent.Finished -> dispatchFinished(listeners, task)
        }
    }

    private fun dispatchConnecting(listeners: List<IProgressCallback>, task: Task) {
        listeners.forEachSafely("Download connecting callback failed") {
            it.onConnecting(task)
        }
    }

    private fun dispatchProgress(listeners: List<IProgressCallback>, task: Task, complete: Boolean) {
        listeners.forEachSafely("Download progress callback failed") {
            it.onProgress(task, complete)
        }
    }

    private fun dispatchFailed(listeners: List<IProgressCallback>, task: Task, error: String?) {
        listeners.forEachSafely("Download failure callback failed") {
            it.onFail(error, task)
        }
    }

    private fun dispatchFinished(listeners: List<IProgressCallback>, task: Task) {
        listeners.forEachSafely("Download finish callback failed") {
            it.onFinish(task)
        }
    }

    private inline fun List<IProgressCallback>.forEachSafely(
        message: String,
        action: (IProgressCallback) -> Unit
    ) {
        for (listener in this) {
            try {
                action(listener)
            } catch (e: Exception) {
                Log.w(DOWNLOAD_DEBUG_TAG, message, e)
            }
        }
    }

    private fun sendBroadcast(task: Task) {
        val intent = if (task.customBroadcast.orEmpty().isNotBlank()) {
            Intent(task.customBroadcast)
        } else if (task.broad) {
            Intent(BROAD_ACTION)
        } else {
            null
        }
        intent?.let {
            if (Build.VERSION.SDK_INT >= 26 && task.componentName.orEmpty().isNotBlank()) {
                it.addFlags(Intent.FLAG_ACTIVITY_PREVIOUS_IS_TOP)
                it.component = Net.instance.ddNetConfig.pkgName?.let { pkgName ->
                    task.componentName?.let { componentName -> ComponentName(pkgName, componentName) }
                }
            }
            it.putExtra("url", task.url)
            it.putExtra("file", task.path)
            it.putExtra("extra", task.extra)
            Net.instance.ddNetConfig.application?.sendBroadcast(it)
        }
    }
}
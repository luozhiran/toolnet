package com.itg.net.download.operations

import android.content.ComponentName
import android.content.Intent
import android.os.Build
import com.itg.net.BROAD_ACTION
import com.itg.net.Download
import com.itg.net.Net
import com.itg.net.download.data.Task
import com.itg.net.download.data.ERROR_TAG_3

object DownloadEndNotify {

    @JvmStatic
    fun connectNotify(task: Task?) {
        if (task == null) return
        Download.instance.globalDownloadProgressCache.execAllConnecting(task)
        HoldActivityCallbackMap.loopConnecting(task)
    }

    @JvmStatic
    fun progressNotify(task: Task?) {
        if (task == null) return
        Download.instance.globalDownloadProgressCache.execAllOnProgress(task)
        HoldActivityCallbackMap.loop(task)
    }

    @JvmStatic
    fun completeNotify(task: Task) {
        progressNotify(task)
        sendBroadcast(task)
    }

    @JvmStatic
     fun sendBroadcast(task: Task) {
        var intent: Intent? = null
        if (task.customBroadcast.orEmpty().isNotBlank()) {
            intent = Intent(task.customBroadcast)
        } else if (task.broad) {
            intent = Intent(BROAD_ACTION)
        }
        intent?.let { it ->
            if (Build.VERSION.SDK_INT >= 26 && task.componentName.orEmpty()
                    .isNotBlank()
            ) {
                it.addFlags(Intent.FLAG_ACTIVITY_PREVIOUS_IS_TOP)//加上这句话，可以解决在android8.0系统以上2个module之间发送广播接收不到的问题
                it.component = Net.instance.ddNetConfig.pkgName?.let {
                    task.componentName?.let { it1 -> ComponentName(it, it1) }
                }
            }
            it.putExtra("url", task.url)
            it.putExtra("file", task.path)
            it.putExtra("extra", task.extra)
            Net.instance.ddNetConfig.application?.sendBroadcast(it)
        }
    }


    @JvmStatic
    fun failNotify(task: Task, msg: String?) {
        if (task.contentLength > 0L && ERROR_TAG_3 != msg ) {
            Download.instance.globalDownloadProgressCache.execAllOnProgress(task)
            HoldActivityCallbackMap.loop(task)
        }
        Download.instance.globalDownloadProgressCache.execAllOnFail(msg ?: "", task )
        HoldActivityCallbackMap.loopFail(msg ?: "", task )
        Download.instance.dispatchTool.getTaskState().debugPrint()
        HoldActivityCallbackMap.debugPrint()
    }

}

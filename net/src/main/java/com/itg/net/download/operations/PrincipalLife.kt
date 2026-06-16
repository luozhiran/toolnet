package com.itg.net.download.operations

import android.app.Activity
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import com.itg.net.download.data.DOWNLOAD_DEBUG_TAG
import com.itg.net.download.data.LockData
import com.itg.net.util.PrintLog
import com.itg.net.util.ThreadTool
import okhttp3.Call
import java.util.WeakHashMap

object PrincipalLife {
    private val callWeakHash by lazy { WeakHashMap<Activity, MutableList<Call>>() }
    private val lockCall = LockData()


    fun observeActivityLife(call: Call?, activity: Activity?) {
        if (call == null || activity == null) return
        PrintLog.logr("绑定 Activity ${call.request().url}")
        var needObserve = false
        synchronized(lockCall) {
            val taskList = callWeakHash.getOrPut(activity) {
                needObserve = true
                mutableListOf()
            }
            if (!taskList.contains(call)) {
                taskList.add(call)
            }
        }
        if (needObserve) {
            ThreadTool.runOnUIThread {
                val componentActivity = activity as? ComponentActivity ?: return@runOnUIThread
                componentActivity.lifecycle.addObserver(object : LifecycleEventObserver {
                    override fun onStateChanged(source: LifecycleOwner, event: Lifecycle.Event) {
                        if (event == Lifecycle.Event.ON_DESTROY) {
                            PrintLog.logr("监听销毁【Lifecycle.Event.ON_DESTROY】 Activity 开始释放资源")
                            val ownerActivity = source as? Activity ?: return
                            ThreadTool.runOnExecutor {
                                PrintLog.logr("${PrintLog.SUB_CONTENT_START}获取Activity实例捆绑Call对象实例${PrintLog.SUB_CONTENT_END}")
                                val calls = synchronized(lockCall) {
                                    callWeakHash.remove(ownerActivity)?.toList().orEmpty()
                                }
                                if (calls.isNullOrEmpty()) {
                                    PrintLog.logr("${PrintLog.SUB_CONTENT_START}Activity实例未绑定Call实例${PrintLog.SUB_CONTENT_END}")
                                } else {
                                    PrintLog.logr("${PrintLog.SUB_CONTENT_START}Activity实例绑定 ${calls.size} 个call实例对象${PrintLog.SUB_CONTENT_END}")
                                }
                                calls.forEach {
                                    PrintLog.logr("${PrintLog.SUB_CONTENT_START}activity销毁，取消Activity绑定的所有Call请求${PrintLog.SUB_CONTENT_END}")
                                    it.cancel()
                                }
                                ThreadTool.runOnUIThread {
                                    source.lifecycle.removeObserver(this)
                                    PrintLog.logr("activity销毁资源释放完成")
                                }
                            }
                        }
                    }
                })
            }

        }
    }

    fun removeCall(call: Call?) {
        if (call == null) return
        PrintLog.logr("请求完成 手动释放 Activity捆绑的 Call实例 call ${call.request().url}")
        synchronized(lockCall) {
            val iterator = callWeakHash.iterator()
            while (iterator.hasNext()) {
                val entryValue = iterator.next().value
                entryValue.remove(call)
                if (entryValue.isEmpty()) {
                    iterator.remove()
                }
            }
        }
        PrintLog.logr("请求完成 手动释放完成 ${call.request().url}")
    }

    fun debugPrint() {
        val size = synchronized(lockCall) {
            callWeakHash.size
        }
        Log.i(DOWNLOAD_DEBUG_TAG, "lifecycle-bound request count=$size")
    }


}

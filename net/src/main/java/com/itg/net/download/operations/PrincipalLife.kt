package com.itg.net.download.operations

import android.app.Activity
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import com.itg.net.download.data.DEBUG_TAG
import com.itg.net.download.data.LockData
import com.itg.net.tools.ThreadTool
import okhttp3.Call
import java.util.WeakHashMap

object PrincipalLife {
    private val callWeakHash by lazy { WeakHashMap<Activity, MutableList<Call>>() }
    private val lockCall = LockData()


    fun observeActivityLife(call: Call?, activity: Activity?) {
        if (call == null || activity == null) return
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
            ThreadTool.runOnUIThread{
                val componentActivity = activity as? ComponentActivity ?: return@runOnUIThread
                componentActivity.lifecycle.addObserver(object : LifecycleEventObserver {
                    override fun onStateChanged(source: LifecycleOwner, event: Lifecycle.Event) {
                        if (event == Lifecycle.Event.ON_DESTROY) {
                            val ownerActivity = source as? Activity ?: return
                            ThreadTool.runOnExecutor {
                                val calls = synchronized(lockCall) {
                                    callWeakHash.remove(ownerActivity)?.toList().orEmpty()
                                }
                                calls.forEach { it.cancel() }
                                ThreadTool.runOnUIThread{
                                    source.lifecycle.removeObserver(this)
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
        synchronized(lockCall) {
            val iterator = callWeakHash.iterator()
            while (iterator.hasNext()) {
                val entryValue = iterator.next().value
                entryValue.remove(call)
                if (entryValue.size == 0) {
                    iterator.remove()
                }
            }
        }
    }

    fun debugPrint(){
        val size = synchronized(lockCall) {
            callWeakHash.size
        }
        Log.i(DEBUG_TAG,"生命周期， 请求接口数：${size}")
    }


}

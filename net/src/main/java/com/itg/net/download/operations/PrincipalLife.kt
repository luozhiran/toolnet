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
import kotlin.collections.LinkedHashMap

object PrincipalLife {
    private val callWeakHash by lazy { LinkedHashMap<Activity, MutableList<Call>>() }
    private val lockCall = LockData()


    fun observeActivityLife(call: Call?, activity: Activity?) {
        if (call == null || activity == null) return
        if (!callWeakHash.containsKey(activity)) {
            val taskList: MutableList<Call> = mutableListOf()
            callWeakHash[activity] = taskList
            (activity as? ComponentActivity)?.lifecycle?.addObserver(object :
                LifecycleEventObserver {
                override fun onStateChanged(source: LifecycleOwner, event: Lifecycle.Event) {
                    if (event == Lifecycle.Event.ON_DESTROY) {
                        ThreadTool.runOnExecutor {
                            synchronized(lockCall) {
                                callWeakHash[activity]?.iterator()?.apply {
                                    while (hasNext()) {
                                        next().cancel()
                                    }
                                }
                                callWeakHash.remove(activity)
                                (activity as? ComponentActivity)?.lifecycle?.removeObserver(this)
                            }
                        }
                    }
                }
            })
        }
        val taskList = callWeakHash[activity]
        taskList?.add(call)
    }

    fun removeCall(call: Call?) {
        if (call == null) return
        val iterator = callWeakHash.iterator()
        var entryValue:MutableList<Call>?=null
        while (iterator.hasNext()) {
            entryValue = iterator.next().value
            entryValue.remove(call)
            if (entryValue.size == 0) {
                iterator.remove()
            }
        }
    }

    fun debugPrint(){
        Log.i(DEBUG_TAG,"生命周期， 请求接口数：${callWeakHash.size}")
    }


}
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
    private val observerWeakHash by lazy { WeakHashMap<Activity, LifecycleEventObserver>() }
    private val lockCall = LockData()

    fun observeActivityLife(call: Call?, activity: Activity?) {
        if (call == null || activity == null) return
        val componentActivity = activity as? ComponentActivity ?: return

        val observed = ThreadTool.runOnUIThreadBlocking {
            if (componentActivity.lifecycle.currentState == Lifecycle.State.DESTROYED) {
                call.cancel()
                return@runOnUIThreadBlocking
            }

            var observerToAdd: LifecycleEventObserver? = null
            synchronized(lockCall) {
                val taskList = callWeakHash.getOrPut(activity) { mutableListOf() }
                if (!taskList.contains(call)) {
                    taskList.add(call)
                }
                if (!observerWeakHash.containsKey(activity)) {
                    observerToAdd = createDestroyObserver()
                    observerWeakHash[activity] = observerToAdd
                }
            }

            observerToAdd?.let {
                componentActivity.lifecycle.addObserver(it)
            }
        }
        if (!observed) {
            call.cancel()
            removeCall(call)
        }
    }

    private fun createDestroyObserver(): LifecycleEventObserver {
        return object : LifecycleEventObserver {
            override fun onStateChanged(source: LifecycleOwner, event: Lifecycle.Event) {
                if (event != Lifecycle.Event.ON_DESTROY) return

                val ownerActivity = source as? Activity ?: return
                ThreadTool.runOnExecutor {
                    val calls = synchronized(lockCall) {
                        observerWeakHash.remove(ownerActivity)
                        callWeakHash.remove(ownerActivity)?.toList().orEmpty()
                    }
                    calls.forEach { it.cancel() }
                    ThreadTool.runOnUIThread {
                        source.lifecycle.removeObserver(this)
                    }
                    PrintLog.logr("activity destroyed, canceled ${calls.size} lifecycle-bound calls")
                }
            }
        }
    }

    fun removeCall(call: Call?) {
        if (call == null) return
        val observersToRemove = mutableListOf<Pair<Activity, LifecycleEventObserver>>()

        synchronized(lockCall) {
            val iterator = callWeakHash.iterator()
            while (iterator.hasNext()) {
                val entry = iterator.next()
                val calls = entry.value
                calls.remove(call)
                if (calls.isEmpty()) {
                    observerWeakHash.remove(entry.key)?.let { observer ->
                        observersToRemove.add(entry.key to observer)
                    }
                    iterator.remove()
                }
            }
        }

        if (observersToRemove.isNotEmpty()) {
            ThreadTool.runOnUIThread {
                observersToRemove.forEach { (activity, observer) ->
                    (activity as? ComponentActivity)?.lifecycle?.removeObserver(observer)
                }
            }
        }
    }

    fun debugPrint() {
        val size = synchronized(lockCall) {
            callWeakHash.size
        }
        Log.i(DOWNLOAD_DEBUG_TAG, "lifecycle-bound request count=$size")
    }
}

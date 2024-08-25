package com.itg.net.download.operations

import android.util.Log
import com.itg.net.download.data.DEBUG_TAG
import com.itg.net.download.data.Task
import com.itg.net.download.interfaces.IProgressCallback
import com.itg.net.tools.TaskTools

object HoldActivityCallbackMap {

    private val progressCallbackMap: MutableMap<String, MutableList<IProgressCallback>> by lazy { mutableMapOf() }

    fun loopConnecting(task: Task) {
        progressCallbackMap[task.url?:""]?.iterator()?.apply {
            while (hasNext()) {
                next().onConnecting(task)
            }
        }
    }

    fun loop(task: Task) {
        progressCallbackMap[task.url?:""]?.iterator()?.apply {
            while (hasNext()) {
                next().onProgress(task, TaskTools.getDownloadProgress(task) == 100)
            }
        }
    }

    fun loopFail(msg: String, task: Task) {
        progressCallbackMap[task.url?:""]?.iterator()?.apply {
            while (hasNext()) {
                next().onFail(msg, task)
            }
        }
    }


    fun setProgressCallback(task: Task, progressCallback: IProgressCallback) {
        if (task.url.isNullOrBlank()) return
        var callbackList = progressCallbackMap[task.url]
        if (callbackList == null) {
            callbackList = mutableListOf()
            progressCallbackMap[task.url!!] = callbackList
        }
        callbackList.add(progressCallback)
    }


    fun removeProgressCallback(task: Task) {
        if (task.url.isNullOrBlank()) return
        progressCallbackMap.remove(task.url)
    }

    /**
     * 删除指定的回调对象
     * @param task Task
     * @param iProgressCallback IProgressCallback
     */
    fun removeProgressCallback(task: Task, iProgressCallback:IProgressCallback) {
        if (task.url.isNullOrBlank()) return
        val callbackList = progressCallbackMap[task.url]
        callbackList?.remove(iProgressCallback)
        if (callbackList.isNullOrEmpty()) {
            progressCallbackMap.remove(task.url)
        }
    }

    /**
     * 获取url对于监听器数量
     */
    fun getUrlProgressCallbackNum(url:String):Int{
        if (url.isBlank()) return 0
        val callbackList = progressCallbackMap[url]
        return callbackList?.size ?:0
    }

    /**
     * 获取task对于监听器数量
     */
    fun getUrlProgressCallbackNum(task: Task):Int{
        if (task.url.isNullOrBlank()) return 0
        return getUrlProgressCallbackNum(task.url!!)
    }

    fun debugPrint(){
        Log.i(DEBUG_TAG,"监听器数量：${progressCallbackMap.size}")
    }
}
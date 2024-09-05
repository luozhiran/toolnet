package com.itg.net.download.operations

import com.itg.net.download.data.LockData
import com.itg.net.download.data.Task
import com.itg.net.download.interfaces.IProgressCallback
import com.itg.net.tools.TaskTools

class GlobalDownloadProgressCache {
    private val progressCallbackList : MutableList<IProgressCallback> by lazy { mutableListOf() }

    fun addItem(progressCallback:IProgressCallback){
        progressCallbackList.add(progressCallback)
    }

    fun removeItem(progressCallback:IProgressCallback){
        progressCallbackList.remove(progressCallback)
    }


    fun execAllConnecting(task: Task){
        val list = progressCallbackList.toMutableList()
        for (callback in list) {
            callback.onConnecting(task)
        }
    }

    fun execAllOnProgress(task: Task){
        val list = progressCallbackList.toMutableList()
        for (callback in list) {
            callback.onProgress(task,TaskTools.getDownloadProgress(task) == 100)
        }
    }

    fun execAllOnFail(msg:String, task: Task){
        val list = progressCallbackList.toMutableList()
        for (callback in list) {
            callback.onFail(msg,task)
        }
    }

}
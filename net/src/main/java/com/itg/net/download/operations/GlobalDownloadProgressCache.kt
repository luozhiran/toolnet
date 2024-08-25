package com.itg.net.download.operations

import com.itg.net.download.data.LockData
import com.itg.net.download.data.Task
import com.itg.net.download.interfaces.IProgressCallback
import com.itg.net.tools.TaskTools

class GlobalDownloadProgressCache {
    private val lock by lazy { LockData() }
    private val progressCallbackList : MutableList<IProgressCallback> by lazy { mutableListOf() }

    fun addItem(progressCallback:IProgressCallback){
        synchronized(lock) {
            progressCallbackList.add(progressCallback)
        }
    }

    fun removeItem(progressCallback:IProgressCallback){
        synchronized(lock) {
            progressCallbackList.remove(progressCallback)
        }
    }


    fun execAllConnecting(task: Task){
        synchronized(lock) {
            val iterator = progressCallbackList.iterator()
            while (iterator.hasNext()) {
                iterator.next().onConnecting(task)
            }
        }
    }

    fun execAllOnProgress(task: Task){
        synchronized(lock) {
            val iterator = progressCallbackList.iterator()
            while (iterator.hasNext()) {
                iterator.next().onProgress(task,TaskTools.getDownloadProgress(task) == 100)
            }
        }
    }

    fun execAllOnFail(msg:String, task: Task){
        synchronized(lock) {
            val iterator = progressCallbackList.iterator()
            while (iterator.hasNext()) {
                iterator.next().onFail(msg,task)
            }
        }
    }

}
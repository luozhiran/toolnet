package com.itg.net.download.operations

import android.util.Log
import com.itg.net.Net
import com.itg.net.download.data.DEBUG_TAG
import com.itg.net.download.data.ERROR_TAG_11
import com.itg.net.download.data.DOWNLOAD_LOG
import com.itg.net.download.data.Task

class TaskState {

    private val maxDownloadSize = Net.instance.ddNetConfig.maxDownloadNum

    //队列下载任务
    private val mWaitTask: MutableList<Task> by lazy { mutableListOf() }
    private val mWaitQueueUrl: MutableList<String?> by lazy { mutableListOf() }

    //正在执行任务
    private val mRunningTasks: MutableList<Task> by lazy { mutableListOf() }
    private val mRunningTasksUrl: MutableList<String?> by lazy { mutableListOf() }


    fun addWaitTask(task: Task):Boolean {
        if (exitWaitUrl(task.url)) {
            // 添加下载任务失败时，需要删除创建任务时生成的全局变量
            HoldActivityCallbackMap.removeProgressCallback(task)
            return false
        }
        if (mWaitTask.add(task)) {
            mWaitQueueUrl.add(task.url)
            return true
        }
        // 添加下载任务失败时，需要删除创建任务时生成的全局变量
        HoldActivityCallbackMap.removeProgressCallback(task)
        return false
    }

    fun deleteWaitTask(task: Task?) {
        if (task == null) return
        if (mWaitTask.remove(task)) {
            mWaitQueueUrl.remove(task.url)
        }
        HoldActivityCallbackMap.removeProgressCallback(task)
    }

    fun deleteWaitTask(url: String?) {
        if (!quickDeleteWaitTask(url)) {
            val iterator = mWaitTask.iterator()
            var item: Task?
            while (iterator.hasNext()) {
                item = iterator.next()
                if (item.url == url) {
                    iterator.remove()
                    mWaitQueueUrl.remove(url)
                    HoldActivityCallbackMap.removeProgressCallback(item)
                    break
                }
            }
        }
    }

    private fun quickDeleteWaitTask(url: String?): Boolean {
        if (url.isNullOrBlank()) return false
        val position = mWaitQueueUrl.indexOf(url)
        if (position > 0 && position < mWaitTask.size) {
            if (mWaitTask[position].url == url) {
                mWaitQueueUrl.removeAt(position)
                val task = mWaitTask.removeAt(position)
                HoldActivityCallbackMap.removeProgressCallback(task)
                return true
            }
        }
        return false
    }

    private fun findFirstTaskFromWaitQueue(): Task? {
        if (mWaitTask.size > 0) {
            mWaitQueueUrl.removeAt(0)
            return mWaitTask.removeAt(0)
        }
        return null
    }

    fun addRunningTask(task: Task?): Boolean {
        if (task == null) return false
        if (mRunningTasksUrl.contains(task.url)) {
            // 添加下载任务失败时，需要删除创建任务时生成的全局变量
            HoldActivityCallbackMap.removeProgressCallback(task)
            return false
        }
        if (mRunningTasks.add(task)) {
            mRunningTasksUrl.add(task.url)
            return true
        }
        // 添加下载任务失败时，需要删除创建任务时生成的全局变量
        HoldActivityCallbackMap.removeProgressCallback(task)
        return false
    }

    fun deleteRunningTask(task: Task?) {
        if (task == null) return
        if (mRunningTasks.remove(task)) {
            mRunningTasksUrl.remove(task.url)
        }
        //下载成功后，删除存储在单例集合中的持有Activity引用的回调对象
        HoldActivityCallbackMap.removeProgressCallback(task)
    }

    private fun quickDeleteRunningTask(url: String?): Boolean {
        if (url.isNullOrBlank()) return false
        val position = mRunningTasksUrl.indexOf(url)
        if (position > 0 && position < mRunningTasks.size) {
            if (mRunningTasks[position].url == url) {
                mRunningTasksUrl.removeAt(position)
                val task = mRunningTasks.removeAt(position)
                HoldActivityCallbackMap.removeProgressCallback(task)
                return true
            }
        }
        return false
    }

    fun deleteRunningTask(url: String?) {
        if (!quickDeleteRunningTask(url)) {
            val iterator = mRunningTasks.iterator()
            var item: Task?
            while (iterator.hasNext()) {
                item = iterator.next()
                if (item.url == url) {
                    iterator.remove()
                    mRunningTasksUrl.remove(url)
                    HoldActivityCallbackMap.removeProgressCallback(item)
                    break
                }
            }
        }
    }

    fun exitRunningTask(task: Task?): Boolean {
        if (task == null) return false
        return mRunningTasks.contains(task)
    }

    fun exitWaitTask(task: Task?): Boolean {
        if (task == null) return false
        return mWaitTask.contains(task)
    }

    fun exitRunningUrl(url: String?): Boolean {
        if (url.isNullOrBlank()) return false
        return mRunningTasksUrl.contains(url)
    }

    fun exitWaitUrl(url: String?): Boolean {
        if (url.isNullOrBlank()) return false
        return mWaitQueueUrl.contains(url)
    }

    /**
     * 校验是否是无效下载任务
     * @param task DTask?
     * @return Boolean
     */
    fun isInvalidTask(task: Task?): Boolean {
        if (task == null) return true
        if (task.url.isNullOrBlank()) return true
        if (task.url == task.cancelUrl) return true
        return false
    }

    /**
     * 断点续传
     * @param task Task
     * @return Boolean
     */
    fun isBreakpointContinuation(task: Task): Boolean {
        return task.append
    }


    /**
     * 下载队列是否可以接收新的下载任务
     * @return Boolean
     */
    fun runningQueueCanAcceptTask(): Boolean {
        return mRunningTasks.size < maxDownloadSize
    }

    /**
     * 按顺序从等待队列中取出下载任务
     * @param task Task
     */
    fun getTaskFromWaitQueue(task: Task?): Task? {
        return if (task == null) {
            findFirstTaskFromWaitQueue()
        } else if (runningQueueCanAcceptTask()) {
            task
        }else {
            addWaitTask(task)
            findFirstTaskFromWaitQueue()
        }
    }

    /**
     * 是否需要检测md5
     * @param task Task
     * @return Boolean
     */
    fun isCheckMd5(task: Task): Boolean {
        return task.md5.orEmpty().isNotBlank()
    }

    fun canNextTask(): Boolean {
        if (runningQueueCanAcceptTask() && mWaitTask.size > 0) return true
        return false
    }

    fun isTryAgainDownload(tag:String?):Boolean{
        if (tag.isNullOrBlank()) return false
        if (tag == ERROR_TAG_11) {
            return true
        }
        return false
    }

    fun debugPrint(){
        Log.i(DEBUG_TAG,"下载队列： 等待任务队列：${mWaitTask.size}，正在下载队列：${mRunningTasks.size}")
    }
}
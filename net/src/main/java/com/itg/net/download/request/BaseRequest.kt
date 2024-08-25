package com.itg.net.download.request

import com.itg.net.Net
import com.itg.net.ModeType
import com.itg.net.download.data.ERROR_TAG_12
import com.itg.net.download.data.ERROR_TAG_2
import com.itg.net.download.data.ERROR_TAG_3
import com.itg.net.download.data.ERROR_TAG_4
import com.itg.net.download.data.ERROR_TAG_5
import com.itg.net.download.data.ERROR_TAG_6
import com.itg.net.download.data.Task
import com.itg.net.download.operations.TaskState
import com.itg.net.reqeust.base.ParamsBuilder
import com.itg.net.tools.CheckTools
import com.itg.net.tools.TaskTools
import okhttp3.Response
import java.io.*

abstract class BaseRequest(private val task: Task, private val taskStateInstance: TaskState) {

    private var successCallback: ((Task, String) -> Unit)? = null
    protected var failureCallback: ((Task, String) -> Unit)? = null


    protected fun getBuilder(): ParamsBuilder {
        val builder = Net.instance.builder(ModeType.Get).url(task.url)
        return builder
    }

    fun setSuccessCallback(callback: ((Task, String) -> Unit)): BaseRequest {
        successCallback = callback
        return this
    }

    fun setFailCallback(callback: ((Task, String) -> Unit)): BaseRequest {
        failureCallback = callback
        return this
    }

    private fun checkFileDir(file: File): Boolean {
        if (file.parentFile != null && !file.parentFile!!.exists()) {
            return file.parentFile!!.mkdirs()
        }
        return true
    }

    private fun checkMd5(path: String, task: Task): Boolean {
        if (!CheckTools.checkMd5(task.md5, path)) {
            File(task.path.toString() + ".tmp").delete()
            failureCallback?.invoke(task, ERROR_TAG_4)
            return false
        }
        return true
    }

    private fun taskCancel(task: Task): Boolean {
        if (task.cancelUrl.isNullOrBlank()) return false
        if (task.url == task.cancelUrl) return true
        return false
    }

    private fun updateTask(size: Long, task: Task) {
        task.downloadSize = size
    }

    //如果不支持断点续传，则取消任务时删除下载的部分数据
    protected fun deletePreDownloadData(file: File) {
        file.delete()
    }


    /**
     * 最后一次数据校验，校验不通过返回false
     * @param file File
     * @return Boolean
     */
    private fun lastOneCheck(
        file: File,
        successCallback: (String) -> Unit,
        failCallback: (String) -> Unit
    ) {
        if (taskStateInstance.isCheckMd5(task) && checkMd5(file.absolutePath, task)) {
            failCallback.invoke(ERROR_TAG_4)
        } else {
            val distFile = File(file.absolutePath.replace(".tmp", ""))
            try {
                file.renameTo(distFile).apply {
                    if (this) {
                        successCallback.invoke(ERROR_TAG_12)
                    } else {
                        failCallback.invoke(ERROR_TAG_5)
                    }
                }
            } catch (e: NullPointerException) {
                failCallback.invoke(e.message.toString())
            }
        }
    }


    private fun saveNetStream(
        inputStream: InputStream,
        file: File,
    ) {
        val buffer = ByteArray(1024 shl 2)
        var length = -1
        var out: OutputStream? = null
        try {
            out = FileOutputStream(file, task.append)
            var pre = 0
            var cur = 0
            while (inputStream.read(buffer).also { length = it } > 0) {
                out.write(buffer, 0, length)
                updateTask(file.length(), task)
                cur = TaskTools.getDownloadProgress(task)
                if (taskCancel(task)) {
                    failureCallback?.invoke(task, ERROR_TAG_3)
                } else {
                    if (cur != pre) {
                        if (cur == 100) {
                            lastOneCheck(
                                file,
                                { msg -> successCallback?.invoke(task, msg) },
                                { msg -> failureCallback?.invoke(task, msg) })
                        } else {
                            task.iProgressCallback?.onProgress(task, cur == 100)
                        }
                    }
                }
                pre = cur
            }
        } catch (e: FileNotFoundException) {
            e.printStackTrace()
            failureCallback?.invoke(task, e.message.toString())
        } catch (e: IOException) {
            e.printStackTrace()
            failureCallback?.invoke(task, e.message.toString())
        } finally {
            try {
                out?.close()
            } catch (e: IOException) {
                e.printStackTrace()
            }
        }
    }

    protected fun handleResponse(response: Response) {
        val file = File(task.path + ".tmp")
        task.contentLength = response.body?.contentLength() ?: 0
        if (checkFileDir(file)) {
            if (response.body == null) {
                failureCallback?.invoke(task, ERROR_TAG_6)
            } else {
                saveNetStream(response.body!!.byteStream(), file)
            }
            response.body?.close()
        } else {
            failureCallback?.invoke(task, ERROR_TAG_2)
            response.body?.close()
        }
    }

    abstract fun start()
}
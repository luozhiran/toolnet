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
        val parentFile = file.parentFile
        if (parentFile != null && !parentFile.exists()) {
            return parentFile.mkdirs()
        }
        return true
    }

    private fun checkMd5(path: String, task: Task): Boolean {
        if (!CheckTools.checkMd5(task.md5, path)) {
            File(path).delete()
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
        if (taskStateInstance.isCheckMd5(task) && !checkMd5(file.absolutePath, task)) {
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
        try {
            val out = FileOutputStream(file, task.append)
            var pre = 0
            var cur = 0
            inputStream.use { input ->
                out.use { output ->
                    while (input.read(buffer).also { length = it } > 0) {
                        if (taskCancel(task)) {
                            failureCallback?.invoke(task, ERROR_TAG_3)
                            return
                        }
                        output.write(buffer, 0, length)
                        updateTask(file.length(), task)
                        cur = TaskTools.getDownloadProgress(task)
                        if (cur != pre) {
                            if (cur == 100) {
                                lastOneCheck(
                                    file,
                                    { msg -> successCallback?.invoke(task, msg) },
                                    { msg -> failureCallback?.invoke(task, msg) })
                            } else {
                                task.iProgressCallback?.onProgress(task, cur == 100)
                            }
                        } else {
                            if (taskCancel(task)) {
                                failureCallback?.invoke(task, ERROR_TAG_3)
                                return
                            }
                        }
                        pre = cur
                    }
                }
            }
        } catch (e: FileNotFoundException) {
            e.printStackTrace()
            failureCallback?.invoke(task, e.message.toString())
        } catch (e: IOException) {
            e.printStackTrace()
            failureCallback?.invoke(task, e.message.toString())
        }
    }

    protected fun handleResponse(response: Response) {
        val file = File(task.path + ".tmp")
        val body = response.body
        val localSize = if (task.append && file.exists()) file.length() else 0L
        task.contentLength = localSize + (body?.contentLength() ?: 0)
        try {
            if (checkFileDir(file)) {
                if (body == null) {
                    failureCallback?.invoke(task, ERROR_TAG_6)
                } else {
                    saveNetStream(body.byteStream(), file)
                }
            } else {
                failureCallback?.invoke(task, ERROR_TAG_2)
            }
        } finally {
            response.close()
        }
    }

    abstract fun start()
}

package com.itg.net.download.request

import com.itg.net.Net
import com.itg.net.ModeType
import com.itg.net.download.data.DOWNLOAD_SUCCESS_MESSAGE
import com.itg.net.download.data.ERROR_CREATE_DOWNLOAD_DIR_FAILED
import com.itg.net.download.data.ERROR_DOWNLOAD_CANCELED
import com.itg.net.download.data.ERROR_EMPTY_RESPONSE_BODY
import com.itg.net.download.data.ERROR_MD5_CHECK_FAILED
import com.itg.net.download.data.ERROR_RENAME_TEMP_FILE_FAILED
import com.itg.net.download.data.ERROR_TARGET_FILE_EXISTS
import com.itg.net.download.data.Task
import com.itg.net.download.operations.TaskState
import com.itg.net.request.base.ParamsBuilder
import com.itg.net.util.CheckTools
import com.itg.net.util.TaskTools
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
            failCallback.invoke(ERROR_MD5_CHECK_FAILED)
        } else {
            val distFile = File(file.absolutePath.removeSuffix(".tmp"))
            try {
                if (distFile.exists()) {
                    if (!task.overwrite) {
                        failCallback.invoke(ERROR_TARGET_FILE_EXISTS)
                        return
                    }
                    if (!distFile.delete()) {
                        failCallback.invoke(ERROR_RENAME_TEMP_FILE_FAILED)
                        return
                    }
                }
                if (file.renameTo(distFile)) {
                    successCallback.invoke(DOWNLOAD_SUCCESS_MESSAGE)
                } else {
                    failCallback.invoke(ERROR_RENAME_TEMP_FILE_FAILED)
                }
            } catch (e: Exception) {
                failCallback.invoke(e.message.toString())
            }
        }
    }


    private fun saveNetStream(
        inputStream: InputStream,
        file: File,
    ) {
        val buffer = ByteArray(1024 shl 5)
        var length: Int
        try {
            val out = FileOutputStream(file, task.append)
            var writtenSize = if (task.append && file.exists()) file.length() else 0L
            var pre = 0
            var cur: Int
            inputStream.use { input ->
                out.use { output ->
                    while (input.read(buffer).also { length = it } > 0) {
                        if (taskCancel(task)) {
                            failureCallback?.invoke(task, ERROR_DOWNLOAD_CANCELED)
                            return
                        }
                        output.write(buffer, 0, length)
                        writtenSize += length.toLong()
                        updateTask(writtenSize, task)
                        cur = TaskTools.getDownloadProgress(task)
                        if (cur != pre) {
                            if (cur == 100) {
                                lastOneCheck(
                                    file,
                                    { msg -> successCallback?.invoke(task, msg) },
                                    { msg -> failureCallback?.invoke(task, msg) })
                                return
                            } else {
                                task.progressCallback?.onProgress(task, cur == 100)
                            }
                        } else {
                            if (taskCancel(task)) {
                                failureCallback?.invoke(task, ERROR_DOWNLOAD_CANCELED)
                                return
                            }
                        }
                        pre = cur
                    }
                }
            }
            updateTask(writtenSize, task)
            if (task.contentLength <= 0L || writtenSize >= task.contentLength) {
                lastOneCheck(
                    file,
                    { msg -> successCallback?.invoke(task, msg) },
                    { msg -> failureCallback?.invoke(task, msg) })
            } else {
                failureCallback?.invoke(task, "Downloaded data is incomplete")
            }
        } catch (e: FileNotFoundException) {
            failureCallback?.invoke(task, e.message ?: e.javaClass.simpleName)
        } catch (e: IOException) {
            failureCallback?.invoke(task, e.message ?: e.javaClass.simpleName)
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
                    failureCallback?.invoke(task, ERROR_EMPTY_RESPONSE_BODY)
                } else {
                    saveNetStream(body.byteStream(), file)
                }
            } else {
                failureCallback?.invoke(task, ERROR_CREATE_DOWNLOAD_DIR_FAILED)
            }
        } finally {
            response.close()
        }
    }

    abstract fun start()
}

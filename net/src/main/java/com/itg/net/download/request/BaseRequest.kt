package com.itg.net.download.request

import com.itg.net.Net
import com.itg.net.ModeType
import com.itg.net.download.data.DOWNLOAD_SUCCESS_MESSAGE
import com.itg.net.download.data.ERROR_CREATE_DOWNLOAD_DIR_FAILED
import com.itg.net.download.data.ERROR_DOWNLOAD_CANCELED
import com.itg.net.download.data.ERROR_EMPTY_RESPONSE_BODY
import com.itg.net.download.data.ERROR_INVALID_DOWNLOAD_TASK
import com.itg.net.download.data.ERROR_MD5_CHECK_FAILED
import com.itg.net.download.data.ERROR_RENAME_TEMP_FILE_FAILED
import com.itg.net.download.data.ERROR_TARGET_FILE_EXISTS
import com.itg.net.download.data.Task
import com.itg.net.download.operations.DownloadQueueState
import com.itg.net.monitor.MonitorConfig
import com.itg.net.monitor.MonitorEvent
import com.itg.net.monitor.MonitorMarker
import com.itg.net.request.base.ParamsBuilder
import com.itg.net.util.CheckTools
import com.itg.net.util.TaskTools
import com.itg.net.util.ThreadTool
import okhttp3.Response
import java.io.*
import java.util.concurrent.ThreadLocalRandom
import java.util.concurrent.atomic.AtomicLong

internal abstract class BaseRequest(private val task: Task, private val taskStateInstance: DownloadQueueState) {

    companion object {
        /** 下载专用请求 ID 生成器 */
        private val downloadIdCounter = AtomicLong(0)
        private const val DOWNLOAD_BUFFER_SIZE = 128 * 1024
    }

    private var successCallback: ((Task, String) -> Unit)? = null
    protected var failureCallback: ((Task, String) -> Unit)? = null

    /** 记录原始 URL（监控用，因为 task.url 可能在取消时变化） */
    private var monitoredUrl: String? = null

    protected fun getBuilder(): ParamsBuilder {
        val builder = Net.instance.builder(ModeType.Get).url(task.url)
        if (task.noGlobalParams) {
            builder.noUseGlobalParams()
        }
        // 透传监控控制标志到 OkHttp 层（MonitorInterceptor 通过 Typed Tag 读取）
        task.monitorFlag?.let { builder.monitorFlag = it }
        builder.monitorExtra = task.monitorExtra
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

    protected fun isTaskCanceled(): Boolean {
        return taskCancel(task)
    }

    private fun taskCancel(task: Task): Boolean {
        if (task.cancelUrl.isNullOrBlank()) return false
        if (task.url == task.cancelUrl) return true
        return false
    }

    private fun updateTask(size: Long, task: Task) {
        task.downloadSize = size
    }

    private fun dispatchProgress(task: Task, complete: Boolean) {
        ThreadTool.runOnUIThread(Runnable {
            task.progressCallback?.onProgress(task, complete)
        })
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
        failCallback: (String, MonitorEvent.ErrorType) -> Unit
    ) {
        if (taskStateInstance.isCheckMd5(task) && !checkMd5(file.absolutePath, task)) {
            failCallback.invoke(ERROR_MD5_CHECK_FAILED, MonitorEvent.ErrorType.MD5_MISMATCH)
        } else {
            val targetPath = task.path?.takeIf { it.isNotBlank() }
            if (targetPath == null) {
                failCallback.invoke(ERROR_INVALID_DOWNLOAD_TASK, MonitorEvent.ErrorType.DISK_WRITE_ERROR)
                return
            }
            val distFile = File(targetPath)
            try {
                if (distFile.exists()) {
                    if (!task.overwrite) {
                        failCallback.invoke(ERROR_TARGET_FILE_EXISTS, MonitorEvent.ErrorType.DISK_WRITE_ERROR)
                        return
                    }
                    if (!distFile.delete()) {
                        failCallback.invoke(ERROR_RENAME_TEMP_FILE_FAILED, MonitorEvent.ErrorType.DISK_WRITE_ERROR)
                        return
                    }
                }
                if (file.renameTo(distFile)) {
                    successCallback.invoke(DOWNLOAD_SUCCESS_MESSAGE)
                } else {
                    failCallback.invoke(ERROR_RENAME_TEMP_FILE_FAILED, MonitorEvent.ErrorType.DISK_WRITE_ERROR)
                }
            } catch (e: Exception) {
                failCallback.invoke(e.message.toString(), MonitorEvent.ErrorType.DISK_WRITE_ERROR)
            }
        }
    }


    private fun saveNetStream(
        inputStream: InputStream,
        file: File,
    ) {
        val buffer = ByteArray(DOWNLOAD_BUFFER_SIZE)
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
                            reportDownloadEvent(writtenSize, MonitorEvent.ErrorType.CANCELLED, ERROR_DOWNLOAD_CANCELED, null)
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
                                    { msg ->
                                        successCallback?.invoke(task, msg)
                                    },
                                    { msg, errType ->
                                        reportDownloadEvent(writtenSize, errType, msg, null)
                                        failureCallback?.invoke(task, msg)
                                    })
                                return
                            } else {
                                dispatchProgress(task, cur == 100)
                            }
                        } else {
                            if (taskCancel(task)) {
                                reportDownloadEvent(writtenSize, MonitorEvent.ErrorType.CANCELLED, ERROR_DOWNLOAD_CANCELED, null)
                                failureCallback?.invoke(task, ERROR_DOWNLOAD_CANCELED)
                                return
                            }
                        }
                        pre = cur
                    }
                }
            }
            updateTask(writtenSize, task)
            if (task.contentLength <= 0L) {
                task.contentLength = writtenSize
            }
            if (writtenSize >= task.contentLength) {
                lastOneCheck(
                    file,
                    { msg ->
                        successCallback?.invoke(task, msg)
                    },
                    { msg, errType ->
                        reportDownloadEvent(writtenSize, errType, msg, null)
                        failureCallback?.invoke(task, msg)
                    })
            } else {
                reportDownloadEvent(writtenSize, MonitorEvent.ErrorType.DOWNLOAD_STREAM_ERROR, "Downloaded data is incomplete", null)
                failureCallback?.invoke(task, "Downloaded data is incomplete")
            }
        } catch (e: FileNotFoundException) {
            reportDownloadEvent(task.downloadSize, MonitorEvent.ErrorType.DISK_WRITE_ERROR, failureMessage(e), e)
            failureCallback?.invoke(task, failureMessage(e))
        } catch (e: IOException) {
            reportDownloadEvent(task.downloadSize, MonitorEvent.ErrorType.DOWNLOAD_STREAM_ERROR, failureMessage(e), e)
            failureCallback?.invoke(task, failureMessage(e))
        }
    }

    protected fun handleResponse(response: Response) {
        val savePath = task.path?.takeIf { it.isNotBlank() }
        if (savePath == null) {
            response.close()
            reportDownloadEvent(0, MonitorEvent.ErrorType.DISK_WRITE_ERROR, ERROR_INVALID_DOWNLOAD_TASK, null)
            failureCallback?.invoke(task, ERROR_INVALID_DOWNLOAD_TASK)
            return
        }
        val file = File("$savePath.tmp")
        val body = response.body
        val localSize = if (task.append && file.exists()) file.length() else 0L
        val remoteLength = body?.contentLength() ?: -1L
        task.contentLength = if (remoteLength >= 0L) {
            localSize + remoteLength
        } else {
            -1L
        }
        // 记录 URL 供下载阶段事件使用
        monitoredUrl = task.url
        try {
            if (taskCancel(task)) {
                reportDownloadEvent(0, MonitorEvent.ErrorType.CANCELLED, ERROR_DOWNLOAD_CANCELED, null)
                failureCallback?.invoke(task, ERROR_DOWNLOAD_CANCELED)
                return
            }
            if (checkFileDir(file)) {
                if (body == null) {
                    reportDownloadEvent(0, MonitorEvent.ErrorType.DOWNLOAD_STREAM_ERROR, ERROR_EMPTY_RESPONSE_BODY, null)
                    failureCallback?.invoke(task, ERROR_EMPTY_RESPONSE_BODY)
                } else {
                    saveNetStream(body.byteStream(), file)
                }
            } else {
                reportDownloadEvent(0, MonitorEvent.ErrorType.DISK_WRITE_ERROR, ERROR_CREATE_DOWNLOAD_DIR_FAILED, null)
                failureCallback?.invoke(task, ERROR_CREATE_DOWNLOAD_DIR_FAILED)
            }
        } finally {
            response.close()
        }
    }

    /**
     * 上报下载阶段监控事件（与 MonitorInterceptor 的 HTTP 层事件互补）
     *
     * 调用时机：
     * - 下载流读取中断（IOException）
     * - 磁盘写入失败（FileNotFoundException / IOException）
     * - MD5 校验失败
     * - 下载被取消
     *
     * 注：MonitorInterceptor 已上报 HTTP 层结果（DNS/连接/HTTP 状态码），
     * 本方法仅上报下载阶段（流读写）的结果。
     *
     * @param writtenSize 已写入磁盘的字节数
     * @param errorType  下载阶段错误类型
     * @param message    错误消息或成功消息
     * @param exception  原始异常（可为 null）
     */
    protected fun reportDownloadEvent(
        writtenSize: Long,
        errorType: MonitorEvent.ErrorType,
        message: String?,
        exception: IOException?,
        httpCode: Int = -1
    ) {
        val config = Net.instance.ddNetConfig.monitorConfig ?: return
        if (!shouldReportDownloadEvent(config, errorType)) return
        val okHttpManager = Net.instance.okhttpManager
        if (okHttpManager.monitorReportHandler == null) return

        task.endTime = System.currentTimeMillis()
        val totalCostMs = if (task.startTime > 0) task.endTime - task.startTime else 0
        val isSuccess = errorType == MonitorEvent.ErrorType.NONE

        // 计算平均下载速度（bytes/s），避免除零
        val speed = if (totalCostMs > 0 && writtenSize > 0) {
            writtenSize * 1000 / totalCostMs
        } else 0L

        val sanitizedUrl = sanitizeUrl(config, monitoredUrl ?: task.url ?: "")
        val event = MonitorEvent(
            requestId = "dl_${task.uniqueId.take(8)}_${downloadIdCounter.incrementAndGet()}",
            url = sanitizedUrl,
            method = "GET",
            tag = sanitizedUrl,
            requestStartMs = task.startTime,
            requestEndMs = task.endTime,
            totalCostMs = totalCostMs,
            httpCode = httpCode,
            responseBodySize = writtenSize,
            contentType = null,
            isSuccess = isSuccess,
            errorType = errorType,
            errorMessage = message,
            exceptionClass = exception?.javaClass?.simpleName,
            networkType = null,
            carrierName = null,
            eventStage = "DOWNLOAD",
            // 下载专用字段
            downloadSize = writtenSize,
            contentLength = task.contentLength,
            isAppend = task.append,
            retryCount = task.tryAgainCount,
            downloadSpeed = speed,
            downloadError = if (isSuccess) MonitorEvent.ErrorType.NONE else errorType,
            extra = task.monitorExtra
        )
        okHttpManager.dispatchMonitorEvent(event)
    }

    private fun shouldReportDownloadEvent(
        config: MonitorConfig,
        errorType: MonitorEvent.ErrorType
    ): Boolean {
        if (task.monitorFlag == MonitorMarker.SKIP) return false
        if (errorType == MonitorEvent.ErrorType.NONE) return false

        val forceMonitor = task.monitorFlag == MonitorMarker.MONITOR
        if (!forceMonitor && !config.enabled) return false

        val totalCostMs = if (task.startTime > 0) {
            System.currentTimeMillis() - task.startTime
        } else 0
        if (!config.reportMode.shouldReport(false, totalCostMs, config.slowRequestThresholdMs)) {
            return false
        }

        if (errorType != MonitorEvent.ErrorType.CANCELLED && task.canRetryDownload()) {
            return false
        }

        if (forceMonitor) return true
        val sampleRate = config.sampleRate
        return when {
            sampleRate.isNaN() -> true
            sampleRate >= 1.0f -> true
            sampleRate <= 0.0f -> false
            else -> ThreadLocalRandom.current().nextFloat() < sampleRate
        }
    }

    private fun sanitizeUrl(config: MonitorConfig, rawUrl: String): String {
        return try {
            config.urlSanitizer(rawUrl)
        } catch (_: Exception) {
            rawUrl
        }
    }

    private fun failureMessage(exception: IOException): String {
        return if (taskCancel(task)) {
            ERROR_DOWNLOAD_CANCELED
        } else {
            exception.message ?: exception.javaClass.simpleName
        }
    }

    abstract fun start()
}

package com.itg.net.download.data

const val MSG_START_NEXT_DOWNLOAD = 2

const val RESULT_DOWNLOAD_FAILED = 3
const val RESULT_DOWNLOAD_SUCCESS = 4

const val ERROR_RANGE_NOT_SUPPORTED = "服务器不支持断点续传"
const val ERROR_CREATE_DOWNLOAD_DIR_FAILED = "下载目录创建失败"
const val ERROR_DOWNLOAD_CANCELED = "下载任务已取消"
const val ERROR_MD5_CHECK_FAILED = "文件校验失败，已删除临时文件"
const val ERROR_RENAME_TEMP_FILE_FAILED = "下载完成但文件重命名失败"
const val ERROR_EMPTY_RESPONSE_BODY = "服务器响应体为空"
const val ERROR_INVALID_DOWNLOAD_TASK = "下载任务无效"
const val ERROR_DOWNLOAD_RETRYING = "下载失败，准备重试"
const val DOWNLOAD_SUCCESS_MESSAGE = "下载完成"
const val ERROR_TARGET_FILE_EXISTS = "目标文件已存在，未开启覆盖"
const val DOWNLOAD_DEBUG_TAG = "ItgNetDownload"
const val DOWNLOAD_LOG_TAG = "ItgNetDownloadLog"

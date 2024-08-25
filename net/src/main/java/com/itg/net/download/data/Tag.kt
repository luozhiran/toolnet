package com.itg.net.download.data


const val CANCEL_TASK = 1
const val DOWNLOAD_TASK = 2


const val DOWNLOAD_FILE = 3
const val DOWNLOAD_SUCCESS = 4

const val ERROR_TAG_1 = "不支持断点续传"
const val ERROR_TAG_2 = "创建文件夹失败"
const val ERROR_TAG_3 = "下载任务被主动取消"
const val ERROR_TAG_4 = "md5校验失败,并删除校验失败文件"
const val ERROR_TAG_5 = "下载任务失败，重命名失败"
const val ERROR_TAG_6 = "response.body is null"
const val ERROR_TAG_7 = "无效任务"
const val ERROR_TAG_8 = "相同请求地址的任务正在下载中,禁止重复请求"
const val ERROR_TAG_9 = "response.code() not is 200"
const val ERROR_TAG_10 = "相同请求地址已在下载队列中,禁止重复请求"
const val ERROR_TAG_11 = "即将重试下载任务"
const val ERROR_TAG_12 = "任务下载成功"
const val DEBUG_TAG="debug-tag"
const val DOWNLOAD_LOG="下载日志"

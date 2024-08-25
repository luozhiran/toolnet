package com.itg.net.download.data

import com.itg.net.download.interfaces.IProgressCallback

class Task {
    //请求地址
    var url: String? = null
    // 请求文件的md5
    var md5: String? = null
    // 下载文件保存地址
    var path: String? = null
    // 下载过程中的回调
    var iProgressCallback: IProgressCallback? = null
    // 内容长度
    var contentLength: Long = 0
    // 下载进度大小
    var downloadSize: Long = 0
    // 需要取消的任务url
    var cancelUrl: String? = null
    // 是否支持断点续传
    var append = false
    // 是否支持下载完成后，发送特定广播
    var broad = false
    // 广播组件名称
    var componentName: String? = null
    // 自定义广播名称
    var customBroadcast: String? = null
    // 携带的额外数据
    var extra: String? = null
    // 下载可以尝试的次数
    var tryAgainCount = 1 //下载重试次数
    // 创建任务的时间戳
    val uniqueId = System.currentTimeMillis()


}
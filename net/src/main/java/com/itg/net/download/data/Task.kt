package com.itg.net.download.data

import com.itg.net.download.callback.IProgressCallback
import java.util.UUID

class Task {
    //请求地址
    var url: String? = null
    // 请求文件的md5
    var md5: String? = null
    // 下载文件保存地址
    var path: String? = null
    // 下载过程中的回调
    var progressCallback: IProgressCallback? = null
    // 内容长度
    var contentLength: Long = 0
    // 下载进度大小
    var downloadSize: Long = 0
    // 需要取消的任务url
    @Volatile
    var cancelUrl: String? = null
    // 是否支持断点续传
    var append = false
    // 目标文件已存在时是否覆盖
    var overwrite = false
    // 是否支持下载完成后，发送特定广播
    var broad = false
    // 广播组件名称
    var componentName: String? = null
    // 自定义广播名称
    var customBroadcast: String? = null
    // 携带的额外数据
    var extra: String? = null
    // 下载可以尝试的次数
    var tryAgainCount = 1
    // 是否跳过全局参数，默认 false（附带全局参数）
    var noGlobalParams = false
    // 创建任务的唯一标识
    val uniqueId = UUID.randomUUID().toString()
    // 单任务监控控制：null=使用全局配置，"__monitor_force__"=强制开启，"__monitor_skip__"=强制跳过
    @Volatile
    var monitorFlag: String? = null
    // 下载开始时间戳（毫秒），用于计算下载总耗时和平均速度
    var startTime: Long = 0
    // 下载结束时间戳（毫秒）
    var endTime: Long = 0
    // 监控业务附加字段
    var monitorExtra: String? = null
}

package com.itg.net.encrypt

/**
 * OkHttp 请求级加密控制的 Typed Tag
 *
 * 通过 [okhttp3.Request.Builder.tag] 的 Class-key 机制设置到 Request 上，
 * 供 [EncryptInterceptor] 读取，不占用 [com.itg.net.request.base.ParamsBuilder.tag]。
 *
 * @property value "encrypt"=强制加密, "skip"=强制跳过, null=使用全局配置
 */
class EncryptMarker(val value: String?) {

    companion object {
        /** 强制加密 */
        const val ENCRYPT = "__encrypt_force__"

        /** 强制跳过 */
        const val SKIP = "__encrypt_skip__"

        /** 从 ParamsBuilder.encryptFlag 创建标记 */
        fun fromFlag(flag: String?): EncryptMarker? {
            return if (flag != null) EncryptMarker(flag) else null
        }
    }
}

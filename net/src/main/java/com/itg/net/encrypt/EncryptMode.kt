package com.itg.net.encrypt

/**
 * 加密模式
 *
 * 控制全局加密策略：
 * - [OPT_OUT]：全量加密，通过 [EncryptConfig.skipPath] 排除不需要的接口
 * - [OPT_IN]：按需加密，仅 [EncryptConfig.encryptPath] 匹配的接口加密，其余透传
 *
 * 单请求可通过 [com.itg.net.request.base.ParamsBuilder.encrypt] /
 * [com.itg.net.request.base.ParamsBuilder.skipEncrypt] 覆盖全局设置。
 */
enum class EncryptMode {

    /** 全量加密 + skipPath 排除（默认，向后兼容） */
    OPT_OUT,

    /** 按需加密，仅 encryptPath 匹配的路径生效 */
    OPT_IN
}

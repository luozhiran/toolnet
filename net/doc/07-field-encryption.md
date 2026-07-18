# 11. 字段加密

如何使用 `EncryptInterceptor` 对 JSON/Form 请求体中的指定字段自动加密、响应体自动解密，对 net / net-flow / net-retrofit 三个模块均透明。

## 适用条件

- 需要保护敏感字段（密码、手机号、银行卡号等）在传输过程中的安全
- 服务端支持对应的加解密算法
- 了解基本的加密概念（密钥、IV、算法）

## 推荐做法

### 快速开始

```kotlin
Net.instance.configure {
    app(this@MyApp)

    encrypt {
        algorithm(Algorithm.AES_GCM_NO_PADDING)  // 选择加密算法（推荐 GCM）
        secretKey("my-32-byte-secret-key!!123456") // 设置 AES 密钥
        iv("1234567890abcdef")                     // 设置 IV 向量
        encryptField("password")                   // 加密 password 字段
        encryptField("phone")                      // 加密 phone 字段
    }
}

// 业务代码完全无感
Net.instance.postJson()
    .url("https://api.example.com/login")
    .addParam("password", "123456")
    .send(callback)
// 实际发送: {"password":"a8f5e2d9b3c7f1..."}
```

### 加密模式

通过 `encryptMode` 切换全局策略：

```kotlin
encrypt {
    // ==== 方案一：OPT_OUT（默认）—— 全量加密，skipPath 排除 ====
    encryptMode(EncryptMode.OPT_OUT)
    encryptField("password")                   // 全局字段：所有接口加密
    skipPath(Regex("/public/.*"))              // 排除不需要加密的接口

    // ==== 方案二：OPT_IN —— 按需加密，仅 encryptPath 匹配的加密 ====
    encryptMode(EncryptMode.OPT_IN)
    encryptPath(Regex("/user/login"), listOf("password"))
                                               // 仅 /user/login 加密 password
    encryptPath(Regex("/payment/.*"), listOf("bankCard", "cvv"))
                                               // 仅 /payment/* 加密 bankCard、cvv
}
```

| 模式 | 默认行为 | 适用场景 |
|---|---|---|
| `OPT_OUT`（默认） | 全量加密，`skipPath` 排除 | 大部分接口需要加密 |
| `OPT_IN` | 全量透传，`encryptPath` 纳入 | 只有少数接口需要加密 |

### 单请求控制

优先级**高于全局配置**：

```kotlin
// 强制加密：无视全局 skipPath 和 OPT_IN 默认跳过
Net.instance.postJson()
    .url("https://api.example.com/public/apply")
    .addParam("phone", "13800138000")
    .encrypt()  // ← 单请求强制加密
    .send(callback)

// 强制跳过：无视全局 encryptField 和 OPT_OUT 默认加密
Net.instance.postJson()
    .url("https://api.example.com/user/search")
    .addParam("phone", "13800138000")
    .skipEncrypt()  // ← 单请求强制跳过
    .send(callback)
```

**优先级链**（从高到低）：

```
1. .encrypt()        → 强制加密
2. .skipEncrypt()    → 强制跳过
3. GET / 非文本 body  → 自动跳过
4. EncryptMode       → OPT_IN / OPT_OUT
5. 字段规则          → encryptField / encryptPath / encryptPattern
```

### 按路径差异化加密

不同接口加密不同字段：

```kotlin
encrypt {
    algorithm(Algorithm.AES_CBC_PKCS7)
    secretKey("my-32-byte-secret-key!!123456")
    iv("1234567890abcdef")

    // 全局规则
    encryptField("password")

    // 路径规则
    encryptPath(Regex("/user/.*"), listOf("phone", "email"))
    encryptPath(Regex("/payment/.*"), listOf("bankCard", "cvv", "idCard"))

    // 正则规则
    encryptPattern(Regex(".*[Ss]ecret"))

    // 仅响应解密（请求不加密）
    decryptField("realName")
    decryptField("idCard")
}
```

## 算法选择

### 算法对比

| 算法 | 需要 IV | 安全性 | 性能 | 推荐度 |
|---|---|---|---|---|
| `AES_GCM_NO_PADDING` | ✅ 12 字节推荐 | ⭐⭐⭐⭐⭐ 加密+认证 | ⭐⭐⭐⭐⭐ 硬件加速 | **首选** |
| `AES_CBC_PKCS7` | ✅ 16 字节 | ⭐⭐⭐ 仅加密 | ⭐⭐⭐ | 兼容性好 |
| `AES_ECB_PKCS7` | ❌ 不需要 | ⭐ 固定密文 | ⭐⭐⭐⭐ | 仅简单混淆 |
| `RSA_ECB_PKCS1` | ❌ 不需要 | ⭐⭐⭐⭐ 非对称 | ⭐ 很慢 | 仅密钥传输 |

### AES/GCM/NoPadding（推荐，带认证）

```kotlin
encrypt {
    algorithm(Algorithm.AES_GCM_NO_PADDING)
    secretKey(EncryptUtil.generateAesKey(256).encoded)
    iv(EncryptUtil.generateIv())  // GCM Nonce，每次必须不同
    encryptField("password")
    encryptField("bankCard")
}
```

> GCM 模式同时提供加密和完整性认证（AEAD），能检测密文是否被篡改。每次加密必须使用不同的 IV（Nonce），重复使用会严重破坏安全性。

### AES/CBC/PKCS7（最常用，兼容性好）

```kotlin
encrypt {
    algorithm(Algorithm.AES_CBC_PKCS7)
    secretKey("my-32-byte-secret-key!!123456")  // 32 字节 = AES-256
    iv("1234567890abcdef")                       // 16 字节 IV
    encryptField("password")
}
```

| 密钥长度 | 字节数 | 对应 AES 强度 |
|---|---|---|
| 16 字节 | 128 位 | AES-128 |
| 24 字节 | 192 位 | AES-192 |
| 32 字节 | 256 位 | AES-256 |

### AES/ECB/PKCS7（无 IV）

```kotlin
encrypt {
    algorithm(Algorithm.AES_ECB_PKCS7)
    secretKey("my-32-byte-secret-key!!123456")
    encryptField("password")
}
```

> **不推荐用于生产环境**。ECB 模式相同明文始终产生相同密文，安全性较低。

### RSA/ECB/PKCS1（非对称加密）

RSA 适合加密小数据（如 AES 密钥传输），不适合加密大段文本。推荐做法是混合加密：用 RSA 加密 AES 密钥，再用 AES 加密业务数据。

```kotlin
// 推荐：混合加密模式
encrypt {
    // 1. 登录时用 RSA 公钥加密 AES 密钥传给服务端
    // 2. 服务端用 RSA 私钥解密，返回 AES SessionKey
    // 3. 后续通信使用协商的 AES SessionKey
    algorithm(Algorithm.AES_GCM_NO_PADDING)
    secretKey(sessionAesKey)  // 服务端返回的 SessionKey
    iv(sessionIv)
    encryptField("phone")
}
```

## EncryptUtil 独立使用

不依赖拦截器，在业务代码中直接调用加解密：

```kotlin
val key = "my-32-byte-secret-key!!123456".toByteArray()
val iv = "1234567890abcdef".toByteArray()

// 加密后存入 SharedPreferences
val encryptedPhone = EncryptUtil.encrypt(
    "13800138000", key, Algorithm.AES_CBC_PKCS7, iv
)
preferences.edit().putString("phone_encrypted", encryptedPhone).apply()

// 读取时解密
val phone = EncryptUtil.decrypt(
    preferences.getString("phone_encrypted", "")!!,
    key, Algorithm.AES_CBC_PKCS7, iv
)
```

| 方法 | 说明 |
|---|---|
| `encrypt(plaintext, key, algorithm, iv?)` | 加密明文，返回 Base64 密文 |
| `decrypt(ciphertext, key, algorithm, iv?)` | 解密 Base64 密文，返回明文 |
| `generateAesKey(keySize)` | 生成随机 AES 密钥（128/192/256） |
| `generateAesKeyFromString(keyStr, keySize)` | 从字符串构造 AES 密钥 |
| `generateIv()` | 生成随机 16 字节 IV |

## EncryptConfig DSL 方法速查

| 方法 | 说明 |
|---|---|
| `encryptMode(mode)` | 设置加密模式（`OPT_OUT` / `OPT_IN`） |
| `algorithm(algorithm)` | 设置加密算法 |
| `secretKey(key)` / `secretKey(keyStr)` / `secretKeyObj(key)` | 设置密钥 |
| `iv(iv)` / `iv(ivStr)` | 设置 IV 向量 |
| `encryptField(name)` | 按字段名精确匹配（双向加解密） |
| `decryptField(name)` | 按字段名精确匹配（仅响应解密） |
| `encryptPattern(regex)` | 按正则匹配字段（双向） |
| `encryptPath(pathRegex, fields)` | 按路径+字段列表匹配（双向） |
| `skipPath(regex)` | 跳过路径（OPT_OUT 模式生效） |
| `requestEncrypt(bool)` | 启用/禁用请求加密（默认 true） |
| `responseDecrypt(bool)` | 启用/禁用响应解密（默认 true） |
| `skipGetRequest(bool)` | 是否跳过 GET 请求（默认 true） |

## 密钥管理建议

| 方案 | 安全等级 | 适用场景 |
|---|---|---|
| 本地固定密钥 | ⭐ | 开发/测试 |
| 本地固定密钥 + 代码混淆 | ⭐⭐ | 非敏感数据 |
| 服务端下发 SessionKey（登录后返回） | ⭐⭐⭐ | **生产环境推荐** |
| RSA 加密 AES 密钥（混合加密） | ⭐⭐⭐⭐ | 高安全需求 |
| Android Keystore 硬件保护 | ⭐⭐⭐⭐⭐ | 金融/支付 |

### 推荐生产方案

```
1. App 启动 → 生成 RSA 密钥对（或预置公钥）
2. 登录请求 → 服务端用 RSA 公钥加密 AES SessionKey 返回
3. 后续请求 → 使用 AES SessionKey + GCM 模式进行字段加解密
4. SessionKey 定期轮换（如每 30 分钟）
```

## 关键说明

- `EncryptInterceptor` 在 OkHttp 拦截器层工作，对上层调用方式完全透明
- 在拦截器链中位于 MonitorInterceptor 之前，所以监控上报的 URL 不包含请求体明文
- GET 请求默认跳过加密（可通过 `skipGetRequest(false)` 修改）
- 单请求的 `.encrypt()` / `.skipEncrypt()` 优先级高于全局配置
- GCM 模式每次加密必须使用不同的 IV/Nonce，重复使用会严重破坏安全性
- RSA 最多加密 245 字节（RSA-2048），不适合加密大段文本

## 验证方式

- 开启 `enableHttpLog(true)`，在日志中确认请求体中的敏感字段已加密
- 使用 `EncryptUtil.encrypt` 和 `EncryptUtil.decrypt` 进行独立的加解密验证
- 确认 `.skipEncrypt()` 的请求体中敏感字段为明文

[返回 README](../../README.md)

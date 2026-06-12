# ITG Net

ITG Net is a lightweight Android networking and download library built on OkHttp.

## Features

- GET and common POST request builders.
- Global headers/query parameters through `NetConfig`.
- OkHttp customization through interceptors, cache and custom `OkHttpClient`.
- Lifecycle-aware request cancellation.
- File download with queueing, retry, cancellation, MD5 validation, overwrite control and breakpoint continuation.

## Install

Published artifact:

```gradle
implementation "com.itg:itg-net:0.1.0"
```

## Configure

```kotlin
Net.instance.configure {
    app(application)
    url("https://api.example.com")
    maxDownloadNum(3)
    setGlobalParams("platform", "android")
}
```

Custom OkHttp:

```kotlin
Net.instance.configure {
    okHttpClient(
        OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .build()
    )
}
```

## Requests

```kotlin
Net.instance.get()
    .url("https://api.example.com/users")
    .addParam("page", "1")
    .autoCancel(activity)
    .send(object : DdCallback {
        override fun onFailure(er: String?) {
        }

        override fun onResponse(result: String?, code: Int) {
        }
    })
```

## Downloads

```kotlin
val task = Net.instance.newDownload()
    .url(fileUrl)
    .savePath(targetFile.absolutePath)
    .retryCount(3)
    .overwrite(false)
    .listener(progressCallback)
    .start()
```

Cancel:

```kotlin
Net.instance.download.cancel(task)
Net.instance.download.cancel(fileUrl)
```

## Download Errors

Download errors are defined in `com.itg.net.download.data.Tag.kt`, for example:

- `ERROR_INVALID_DOWNLOAD_TASK`
- `ERROR_TARGET_FILE_EXISTS`
- `ERROR_DOWNLOAD_CANCELED`
- `ERROR_RANGE_NOT_SUPPORTED`
- `ERROR_MD5_CHECK_FAILED`

## Compatibility Notes

The historical package name `com.itg.net.reqeust` is kept for binary/source compatibility.

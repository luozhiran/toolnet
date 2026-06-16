package com.itg.ddlnet

import android.os.Bundle
import android.util.Log
import android.widget.Button

import androidx.appcompat.app.AppCompatActivity
import com.itg.net.Download
import com.itg.net.download.data.Task

import java.io.File
import java.security.MessageDigest
import com.itg.net.Net
import com.itg.net.download.callback.IProgressCallback
import com.itg.net.request.base.DdCallback
import com.itg.net.util.StrTools
import org.json.JSONArray
import org.json.JSONObject
import java.util.Date

class DownloadActivity : AppCompatActivity() {

    private val ip = "http://10.100.219.242:3000"

    private val progress = object : IProgressCallback {
        override fun onConnecting(task: Task) {

        }

        override fun onProgress(task: Task, complete: Boolean) {
            if (complete) {
                Log.e(
                    "MainActivity",
                    "-------------download is success ${task.path} ${File(task.path ?: "").exists()}"
                )
            }
        }

        override fun onFail(error: String?, task: Task) {
        }

    }


    private val downloadUrlList= mutableListOf<String>(
        "${ip}/download/1781446055306-94565585-1778425317121.jpg",
        "${ip}/download/1781446430194-921360704-bg.jpg",
        "${ip}/download/1781451464361-210732640-fasdf.jpg")

    private fun intArrayOf(elements: String, elements2: String, elements3: String) {}

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(R.layout.activity_download)

        Download.instance.setGlobalProgressListener(progress)
        findViewById<Button>(R.id.download).setOnClickListener {
            Thread(){
                downloadUrlList.forEach {
                    Thread.sleep(1000)
                    val fileName = StrTools.extractUrlFileName(it,"default")
                    val path = "${filesDir}/$fileName.png"
                    Download.instance
                        .taskBuilder()
                        .path(path)
                        .url(it)
                        .tryAgainCount(1)
                        .autoRemoveActivity(this)
                        .setDownloadListener(object : IProgressCallback {
                            override fun onConnecting(task: Task) {
                                Log.e("MainActivity", "onConnecting $1")
                            }

                            override fun onProgress(task: Task, complete: Boolean) {
                                if (complete) {
                                    Log.e(
                                        "MainActivity",
                                        "download is success $path $1 ${File(task.path ?: "").exists()}"
                                    )
                                }
                            }

                            override fun onFail(error: String?, task: Task) {
                                Log.e("MainActivity", "onFail $error")
                            }

                        })
                        .start()
                }
            }.start()


        }

        findViewById<Button>(R.id.get).setOnClickListener {
            Net.instance.get()
                .url(ip)
                .path("api/data")
                .autoCancel(this)
                .send(object : DdCallback{
                    override fun onFailure(er: String?) {
                        Log.e("luozhiran", er + "")
                    }

                    override fun onResponse(result: String?, code: Int) {
                        Log.e("luozhiran", result + "")
                    }
                })

        }

        findViewById<Button>(R.id.post).setOnClickListener {
            val array = JSONArray()
            val obj = JSONObject()
            obj.put("token_id", "fsadfsd")
            array.put(obj)
            val num = Date().time
            Net.instance.postJson()
                .url(ip)
                .path("api/json")
                .addParam("loginname", "18516607913")
                .addJson("params", array)
                .addParam("nonce", num)
                .addParam("pwd", md5("${md5("123456")}${num}"))
                .noUseGlobalParams()
                .autoCancel(this)
                .send(object : DdCallback {
                    override fun onFailure(er: String?) {
                        Log.e("luozhiran", er + "")
                    }

                    override fun onResponse(result: String?, code: Int) {
                        Log.e("luozhiran", result + "")
                    }

                })

        }
    }

    override fun onDestroy() {
        super.onDestroy()
        Download.instance.remoteGlobalProgressListener(progress)
    }


    /** md5加密 */
    fun md5(content: String): String {
        val hash = MessageDigest.getInstance("MD5").digest(content.toByteArray())
        val hex = StringBuilder(hash.size * 2)
        for (b in hash) {
            var str = Integer.toHexString(b.toInt())
            if (b < 0x10) {
                str = "0$str"
            }
            hex.append(str.substring(str.length - 2))
        }
        return hex.toString()
    }
}
package com.itg.ddlnet

import android.annotation.SuppressLint
import android.os.Bundle
import android.util.Log
import android.widget.Button
import androidx.appcompat.app.AppCompatActivity
import com.itg.net.Download
import com.itg.net.Net
import com.itg.net.download.data.Task
import com.itg.net.download.interfaces.IProgressCallback
import com.itg.net.reqeust.base.DdCallback
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.security.MessageDigest
import java.security.NoSuchAlgorithmException
import java.util.Date


class MainActivity : AppCompatActivity() {
    var stop = true
    private val progress = object : IProgressCallback {
        override fun onConnecting(task: Task) {

        }

        override fun onProgress(task: Task, complete:Boolean) {
        }

        override fun onFail(error: String?, task: Task) {
        }

    }

    @SuppressLint("SuspiciousIndentation")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
       Download.instance.setGlobalProgressListener(progress)
        findViewById<Button>(R.id.download).setOnClickListener {
            for (i in 0..10) {
//                Thread.sleep(1000)
                Log.e("MainActivity", "延时$i")
                val path = "${filesDir}/$i.zip"
                  Download.instance
                    .taskBuilder()
                    .path(path)
                    .url("https://www.baidu.com/img/PCtm_d9c8750bed0b3c7d089fa7d55720d6cf.png")
                    .tryAgainCount(3)
                    .autoRemoveActivity(this)
                    .setDownloadListener(object : IProgressCallback {
                        override fun onConnecting(task: Task) {
                            Log.e("MainActivity", "onConnecting $i")
                        }

                        override fun onProgress(task: Task, complete:Boolean) {
                            if (complete) {
                                Log.e(
                                    "MainActivity",
                                    "download is success $path $i ${File(task.path ?: "").exists()}"
                                )
                            }
                        }

                        override fun onFail(error: String?, task: Task) {
                            Log.e("MainActivity", "onFail $error")
                        }

                    })
                    .start()
            }


        }

        findViewById<Button>(R.id.get).setOnClickListener {
            Net.instance.get()
                .url("http://www.baidu.com")
                .addParam("key1", "a")
                .addParam("key2", "b")
                .noUseGlobalParams()
                .autoCancel(this)
                .send(object : DdCallback {
                    override fun onFailure(er: String?) {

                    }

                    override fun onResponse(result: String?, code: Int) {

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
//                .url("https://www.baidu.com")
                .path("login")
                .addParam("loginname","18516607913")
                .addJson("params",array)
                .addParam("nonce",num)
                .addParam("pwd",md5("${md5("123456")}${num}"))
                .noUseGlobalParams()
                .autoCancel(this)
                .send(object : DdCallback {
                    override fun onFailure(er: String?) {
                        Log.e("luozhiran",er+"")
                    }

                    override fun onResponse(result: String?, code: Int) {
                        Log.e("luozhiran",result+"")
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
            hex.append(str.substring(str.length -2))
        }
        return hex.toString()
    }

}
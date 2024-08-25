package com.itg.net.download.implement

import android.os.Handler
import android.os.Looper
import android.os.Message

class ReceiverHandler(looper: Looper, callback: Handler.Callback) : Handler(looper, callback)
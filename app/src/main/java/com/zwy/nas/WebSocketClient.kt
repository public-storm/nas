package com.zwy.nas

import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.WebSocket
import okhttp3.WebSocketListener

object WebSocketClient {
    private val client by lazy { OkHttpClient() }
    private var webSocket: WebSocket? = null

    fun connect(url: String, impl: WebSocketListener) {
        val request = Request.Builder()
            .url(url)
            .build()
        webSocket = client.newWebSocket(request, impl)
    }
}
package com.example.pult.network

import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.ByteString

class MetricsWebSocketListener(
    private val metricsFlow: MutableStateFlow<String>,
    private val initialTimestamp: Long
) : WebSocketListener() {

    private val tag = "PultWS"

    override fun onOpen(webSocket: WebSocket, response: Response) {
        val jsonPayload = "{\"last_timestamp\": $initialTimestamp}"
        webSocket.send(jsonPayload)
    }

    override fun onMessage(webSocket: WebSocket, text: String) {
        metricsFlow.value = text
    }

    override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
        Log.d(tag, "Connection closed")
        metricsFlow.value = "WS: Closed"
    }

    override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
        metricsFlow.value = "{\"error\": \"Connection failed\"}"
    }
}

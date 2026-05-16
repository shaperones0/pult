package com.example.pult

import android.app.Application
import com.example.pult.db.DatabaseHelper
import com.example.pult.db.HistoryEntity
import com.example.pult.network.ActionRequest
import com.example.pult.network.ActionResponse
import com.example.pult.network.MetricsWebSocketListener
import com.example.pult.network.NetworkClient
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import okhttp3.Request

class PultRepository(
    application: Application,
    private val wsMetrics: MutableStateFlow<String>,
    private val wsUrl: String
) {

    private var webSocket: okhttp3.WebSocket? = null

    private val dbHelper = DatabaseHelper(application)

    fun wsStart() {
        val client = NetworkClient.okHttpClient
        val request = Request.Builder()
            .url(wsUrl)
            .build()

        val listener = MetricsWebSocketListener(wsMetrics)

        webSocket = client.newWebSocket(request, listener)
    }

    fun wsClose(reason: String) {
        webSocket?.close(1000, reason)
    }

    fun dbHistoryList(): List<HistoryEntity> {
        return dbHelper.getAllHistory()
    }

    fun dbHistoryPush(historyEntity: HistoryEntity) {
        dbHelper.insertAction(historyEntity)
    }

    fun dbHistoryPushMonitor(cpu: Float, ram: Float) {
        val logMessage = "CPU: $cpu%, RAM: $ram%"
        dbHistoryPush(HistoryEntity(
            actionName = "background_monitor",
            logLevel = "info",
            resultMessage = logMessage
        ))
    }

    fun dbHistoryPushActionResponse(actionName: String, responseMessage: String) {
        dbHistoryPush(
            HistoryEntity(
                actionName = actionName,
                logLevel = "info",
                resultMessage = responseMessage
            )
        )
    }

    fun dbHistoryPushActionFail(actionName: String, errorMessage: String) {
        dbHistoryPush(
            HistoryEntity(
                actionName = "$actionName (failed)",
                logLevel = "error",
                resultMessage = errorMessage
            )
        )
    }

    suspend fun netPostAction(actionName: String): ActionResponse {
        return NetworkClient.api.postAction(
            ActionRequest(actionName = actionName, payload = null)
        )
    }
}
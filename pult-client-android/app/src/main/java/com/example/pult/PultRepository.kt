package com.example.pult

import android.app.Application
import com.example.pult.db.DatabaseHelper
import com.example.pult.db.FavoriteEntity
import com.example.pult.db.HistoryEntity
import com.example.pult.network.ActionRequest
import com.example.pult.network.ActionResponse
import com.example.pult.network.MetricsWebSocketListener
import com.example.pult.network.NetworkClient
import kotlinx.coroutines.flow.MutableStateFlow
import okhttp3.Request

class PultRepository(
    application: Application,
    private val wsMetrics: MutableStateFlow<String>,
) {

    private var webSocket: okhttp3.WebSocket? = null
    private val dbHelper = DatabaseHelper(application)

    private val prefsManager = PrefsManager(application)

    init {
        NetworkClient.initialize(prefsManager)
    }

    fun wsStart() {
        val client = NetworkClient.okHttpClient
        val baseUrl = prefsManager.getServerUrl() ?: return
        val wsUrl = baseUrl
            .replace("http://", "ws://")
            .replace("https://", "wss://") + "ws/metrics"
        val request = Request.Builder().url(wsUrl).build()
        val lastTimeStamp = dbHelper.actionGetLastTimestamp()

        val listener = MetricsWebSocketListener(wsMetrics, lastTimeStamp)
        webSocket = client.newWebSocket(request, listener)
    }

    fun wsClose(reason: String) {
        webSocket?.close(1000, reason)
    }

    fun dbHistoryList(): List<HistoryEntity> {
        return dbHelper.actionList()
    }

    fun dbHistoryPush(historyEntity: HistoryEntity) {
        dbHelper.actionInsert(historyEntity)
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

    fun dbFavoriteList(): List<FavoriteEntity> {
        return dbHelper.favoriteList()
    }

    fun dbFavoriteUpsert(command: String) {
        dbHelper.favoriteUpsert(command)
    }

    fun dbFavoriteRemove(command: String) {
        dbHelper.favoriteRemove(command)
    }

    fun dbActionCleanupOld() {
        dbHelper.actionCleanupOld()
    }

    suspend fun netPostAction(actionName: String, payload: String? = null): ActionResponse {
        return NetworkClient.api.postAction(
            ActionRequest(actionName = actionName, payload = payload)
        )
    }
}
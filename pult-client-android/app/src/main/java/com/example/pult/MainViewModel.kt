package com.example.pult

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.pult.db.DatabaseHelper
import com.example.pult.db.HistoryEntity
import com.example.pult.network.ActionRequest
import com.example.pult.network.MetricsWebSocketListener
import com.example.pult.network.NetworkClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import okhttp3.Request

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val _statusText = MutableStateFlow("Waiting for command")
    val statusText: StateFlow<String> = _statusText

    private val _historyList = MutableStateFlow<List<HistoryEntity>>(emptyList())
    val historyList: StateFlow<List<HistoryEntity>> = _historyList

    private val _wsMetrics = MutableStateFlow("Waiting for WS...")
    val wsMetrics: StateFlow<String> = _wsMetrics

    private var webSocket: okhttp3.WebSocket? = null

    private val dbHelper = DatabaseHelper(application)

    init {
        loadHistory()
        startWebSocket()
    }

    private fun startWebSocket() {
        val client = NetworkClient.okHttpClient
        val wsUrl = "ws://10.0.2.2:7070/ws/metrics"
        val request = Request.Builder()
            .url(wsUrl)
            .build()

        val listener = MetricsWebSocketListener(_wsMetrics)
        webSocket = client.newWebSocket(request, listener)
    }

    override fun onCleared() {
        super.onCleared()

        webSocket?.close(1000, "App closed")
    }

    private fun loadHistory() {
        viewModelScope.launch(Dispatchers.IO) {
            val list = dbHelper.getAllHistory()
            _historyList.value = list
        }
    }

    fun refreshHistory() {
        //why separate method? dont ask...
        loadHistory()
    }

    fun saveLiveMetricsToDb(cpu: Float, ram: Float) {
        viewModelScope.launch(Dispatchers.IO) {
            val logMessage = "CPU: $cpu%, RAM: $ram%"
            dbHelper.insertAction(
                HistoryEntity(actionName = "background_monitor", logLevel = "info", resultMessage = logMessage)
            )
            loadHistory()
        }
    }

    fun sendCommand() {
        _statusText.value = "Sending request..."
        val actionName = "lock"
        viewModelScope.launch(Dispatchers.IO) {
            try {

                val response = NetworkClient.api.postAction(
                    ActionRequest(actionName = actionName, payload = null)
                )

                _statusText.value = "Success: ${response.message}"

                dbHelper.insertAction(
                    HistoryEntity(
                        actionName = actionName,
                        logLevel = "info",
                        resultMessage = response.message
                    )
                )
                loadHistory()

            } catch (e: Exception) {
                _statusText.value = "Error: ${e.message}"

                dbHelper.insertAction(
                    HistoryEntity(
                        actionName = "$actionName (failed)",
                        logLevel = "error",
                        resultMessage = e.message ?: "Unknown error"
                    )
                )
                loadHistory()
            }
        }
    }

}
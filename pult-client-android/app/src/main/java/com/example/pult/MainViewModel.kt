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

    private val repo = PultRepository(
        application,
        _wsMetrics,
        "ws://10.0.2.2:7070/ws/metrics"
    )

    init {
        loadHistory()
        repo.wsStart()
    }

    override fun onCleared() {
        super.onCleared()

        repo.wsClose("App closed")
    }

    private fun loadHistory() {
        viewModelScope.launch(Dispatchers.IO) {
            _historyList.value = repo.dbHistoryList()
        }
    }

    fun refreshHistory() {
        loadHistory()
    }

    fun saveLiveMetricsToDb(cpu: Float, ram: Float) {
        viewModelScope.launch(Dispatchers.IO) {
            repo.dbHistoryPushMonitor(cpu, ram)
            loadHistory()
        }
    }

    fun sendCommand() {
        _statusText.value = "Sending request..."
        val actionName = "lock"
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val response = repo.netPostAction(actionName)

                _statusText.value = "Success: ${response.message}"
                repo.dbHistoryPushActionResponse(actionName, response.message)
                loadHistory()

            } catch (e: Exception) {
                _statusText.value = "Error: ${e.message}"
                repo.dbHistoryPushActionFail(actionName, e.message ?: "Unknown error")
                loadHistory()
            }
        }
    }

}
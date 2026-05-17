package com.example.pult

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.pult.db.FavoriteEntity
import com.example.pult.db.HistoryEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import org.json.JSONArray
import retrofit2.HttpException

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val _statusText = MutableStateFlow("Waiting for command")
    val statusText: StateFlow<String> = _statusText

    private val _historyList = MutableStateFlow<List<HistoryEntity>>(emptyList())
    val historyList: StateFlow<List<HistoryEntity>> = _historyList

    private val _wsMetrics = MutableStateFlow("Waiting for WS...")
    val wsMetrics: StateFlow<String> = _wsMetrics

    private val _favoriteList = MutableStateFlow<List<FavoriteEntity>>(emptyList())
    val favoriteList: StateFlow<List<FavoriteEntity>> = _favoriteList

    private val _authErrorEvent = MutableSharedFlow<Unit>()
    val authErrorEvent = _authErrorEvent.asSharedFlow()

    private val repo = PultRepository(application, _wsMetrics)

    init {
        cleanupOldData()
        historyLoad()
        favoritesLoad()
        repo.wsStart()
    }

    override fun onCleared() {
        super.onCleared()

        repo.wsClose("App closed")
    }

    private fun historyLoad() {
        viewModelScope.launch(Dispatchers.IO) {
            _historyList.value = repo.dbHistoryList()
        }
    }

    fun historyRefresh() {
        historyLoad()
    }

    fun saveLiveMetricsToDb(cpu: Float, ram: Float) {
        viewModelScope.launch(Dispatchers.IO) {
            repo.dbHistoryPushMonitor(cpu, ram)
            historyLoad()
        }
    }

    private fun favoritesLoad() {
        viewModelScope.launch(Dispatchers.IO) {
            _favoriteList.value = repo.dbFavoriteList()
        }
    }

    fun favoritesAdd(command: String) {
        viewModelScope.launch(Dispatchers.IO) {
            repo.dbFavoriteUpsert(command)
            _favoriteList.value = repo.dbFavoriteList()
        }
    }

    fun favoritesRemove(command: String) {
        viewModelScope.launch(Dispatchers.IO) {
            repo.dbFavoriteRemove(command)
            _favoriteList.value = repo.dbFavoriteList()
        }
    }

    private fun cleanupOldData() {
        viewModelScope.launch(Dispatchers.IO) {
            repo.dbActionCleanupOld()
        }
    }

    fun processIncomingLogs(logsArray: JSONArray) {
        if (logsArray.length() == 0) return

        viewModelScope.launch(Dispatchers.IO) {
            for (i in 0 until logsArray.length()) {
                val logObj = logsArray.getJSONObject(i)
                val msg = logObj.getString("message")
                repo.dbHistoryPushActionResponse("server_log", msg)
            }
            historyLoad()
        }
    }

    fun sendCommand(command: String) {
        _statusText.value = "Sending request..."
        val actionName = "cmd"
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val response = repo.netPostAction(actionName, command)
                repo.dbHistoryPushActionResponse(actionName, response.message)
                historyLoad()

            } catch (e: Exception) {
                if (e is HttpException && e.code() == 401) {
                    _authErrorEvent.emit(Unit)
                }
                else {
                    repo.dbHistoryPushActionFail(actionName, e.message ?: "Unknown error")
                    historyLoad()
                }
            }
        }
    }

}
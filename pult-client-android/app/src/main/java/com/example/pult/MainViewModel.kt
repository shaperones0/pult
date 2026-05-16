package com.example.pult

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.pult.db.DatabaseHelper
import com.example.pult.db.HistoryEntity
import com.example.pult.network.ActionRequest
import com.example.pult.network.NetworkClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import okhttp3.internal.connection.Exchange

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val _statusText = MutableStateFlow("Waiting for command")
    val statusText: StateFlow<String> = _statusText

    private val _historyList = MutableStateFlow<List<HistoryEntity>>(emptyList())
    val historyList: StateFlow<List<HistoryEntity>> = _historyList

    private val dbHelper = DatabaseHelper(application)

    init {
        loadHistory()
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
                        resultMessage = response.message
                    )
                )
                loadHistory()

            } catch (e: Exception) {
                _statusText.value = "Error: ${e.message}"

                dbHelper.insertAction(
                    HistoryEntity(
                        actionName = "$actionName (failed)",
                        resultMessage = e.message ?: "Unknown error"
                    )
                )
                loadHistory()
            }
        }
    }

}
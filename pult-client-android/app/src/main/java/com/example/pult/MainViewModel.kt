package com.example.pult

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.pult.network.ActionRequest
import com.example.pult.network.NetworkClient
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import okhttp3.internal.connection.Exchange

class MainViewModel : ViewModel() {

    private val _statusText = MutableStateFlow("Waiting for command")

    val statusText: StateFlow<String> = _statusText

    fun sendCommand() {
        _statusText.value = "Sending request..."

        viewModelScope.launch {
            try {
                val response = NetworkClient.api.postAction(
                    ActionRequest(actionName = "test", payload = null)
                )

                _statusText.value = "Success: ${response.message}"
            } catch (e: Exception) {
                _statusText.value = "Error: ${e.message}"
            }
        }
    }

}
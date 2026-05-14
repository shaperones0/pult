package com.example.pult

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class MainViewModel : ViewModel() {

    private val _statusText = MutableStateFlow("Waiting for command")

    val statusText: StateFlow<String> = _statusText

    fun sendCommand() {
        _statusText.value = "Command send! [200 OK]"
    }

}
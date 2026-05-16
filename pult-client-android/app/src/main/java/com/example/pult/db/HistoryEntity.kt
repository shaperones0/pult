package com.example.pult.db

data class HistoryEntity(
    val id: Int = 0,
    val actionName: String,
    val resultMessage: String,
    val logLevel: String,
    val timestamp: Long = System.currentTimeMillis()
)
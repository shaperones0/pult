package com.example.pult.db

data class FavoriteEntity(
    val id: Int = 0,
    val commandText: String,
    val lastUsed: Long
)

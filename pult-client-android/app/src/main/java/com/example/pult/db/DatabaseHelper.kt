package com.example.pult.db

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

class DatabaseHelper(context: Context) : SQLiteOpenHelper(context, "pult_database.db", null, 1) {
    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("""
            CREATE TABLE action_history (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                actionName TEXT,
                resultMessage TEXT,
                logLevel TEXT,
                timestamp INTEGER
            )
        """.trimIndent())
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        // migration my ass
        db.execSQL("DROP TABLE IF EXISTS action_history")
        onCreate(db)
    }

    fun insertAction(history: HistoryEntity) {
        val values = ContentValues().apply {
            put("actionName", history.actionName)
            put("resultMessage", history.resultMessage)
            put("logLevel", history.logLevel)
            put("timestamp", history.timestamp)
        }
        writableDatabase.insert("action_history", null, values)
    }

    fun getAllHistory(): List<HistoryEntity> {
        val list = mutableListOf<HistoryEntity>()
        val cursor = readableDatabase.rawQuery("SELECT * FROM action_history ORDER BY timestamp ASC", null)

        while (cursor.moveToNext()) {
            list.add(
                HistoryEntity(
                    id = cursor.getInt(cursor.getColumnIndexOrThrow("id")),
                    actionName = cursor.getString(cursor.getColumnIndexOrThrow("actionName")),
                    resultMessage = cursor.getString(cursor.getColumnIndexOrThrow("resultMessage")),
                    logLevel = cursor.getString(cursor.getColumnIndexOrThrow("logLevel")),
                    timestamp = cursor.getLong(cursor.getColumnIndexOrThrow("timestamp"))
                )
            )
        }
        cursor.close()
        return list
    }

    fun clearAll() {
        writableDatabase.execSQL("DELETE FROM action_history")
    }
}
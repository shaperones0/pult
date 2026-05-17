package com.example.pult.db

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

class DatabaseHelper(context: Context) : SQLiteOpenHelper(context, "pult_database.db", null, 2) {
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

        db.execSQL("""
            CREATE TABLE favorites (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                commandText TEXT UNIQUE,
                lastUsed INTEGER
            )
        """.trimIndent())
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        // migration my ass
        db.execSQL("DROP TABLE IF EXISTS action_history")
        db.execSQL("DROP TABLE IF EXISTS favorites")
        onCreate(db)
    }

    fun actionInsert(history: HistoryEntity) {
        val values = ContentValues().apply {
            put("actionName", history.actionName)
            put("resultMessage", history.resultMessage)
            put("logLevel", history.logLevel)
            put("timestamp", history.timestamp)
        }
        writableDatabase.insert("action_history", null, values)
    }

    fun actionList(): List<HistoryEntity> {
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

    fun actionClear() {
        writableDatabase.execSQL("DELETE FROM action_history")
    }

    fun favoriteUpsert(command: String) {
        //insert new favorite command or update last used timestamp of existing one

        val db = writableDatabase
        val currentTime = System.currentTimeMillis()

        //check exists
        val cursor = db.rawQuery("SELECT id FROM favorites WHERE commandText = ?", arrayOf(command))
        if (cursor.moveToFirst()) {
            //update time
            val id = cursor.getInt(0)
            val values = ContentValues().apply {
                put("lastUsed", currentTime)
            }
            db.update("favorites", values, "id = ?", arrayOf(id.toString()))
        } else {
            val values = ContentValues().apply {
                put("commandText", command)
                put("lastUsed", currentTime)
            }
            db.insert("favorites", null, values)
        }

        cursor.close()
    }

    fun favoriteRemove(command: String) {
        writableDatabase.delete("favorites", "commandText=?", arrayOf(command))
    }

    fun favoriteList(): List<FavoriteEntity> {
        val list = mutableListOf<FavoriteEntity>()
        val cursor = readableDatabase.rawQuery("SELECT * FROM favorites ORDER BY lastUsed DESC", null)

        while (cursor.moveToNext()) {
            list.add(
                FavoriteEntity(
                    id = cursor.getInt(cursor.getColumnIndexOrThrow("id")),
                    commandText = cursor.getString(cursor.getColumnIndexOrThrow("commandText")),
                    lastUsed = cursor.getLong(cursor.getColumnIndexOrThrow("lastUsed"))
                )
            )
        }

        cursor.close()
        return list
    }
}
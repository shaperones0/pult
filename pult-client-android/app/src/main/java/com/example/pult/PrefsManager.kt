package com.example.pult

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import androidx.core.content.edit

class PrefsManager(context: Context) {

    private val masterKey = MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()

    private val sharedPreferences: SharedPreferences = EncryptedSharedPreferences.create(
        context,
        "pult_secure_prefs",
        masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    fun saveCredentials(url: String, apiKey: String) {
        sharedPreferences.edit {
            putString("SERVER_URL", url)
            putString("API_KEY", apiKey)
        }
    }

    fun getServerUrl(): String? {
        return sharedPreferences.getString("SERVER_URL", null)
    }

    fun getApiKey(): String? {
        return sharedPreferences.getString("API_KEY", null)
    }

    fun clearCredentials() {
        sharedPreferences.edit { clear() }
    }

    fun isLoggedIn(): Boolean {
        return !getServerUrl().isNullOrBlank() && !getApiKey().isNullOrBlank()
    }
}
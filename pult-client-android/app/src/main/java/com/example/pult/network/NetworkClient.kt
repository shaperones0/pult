package com.example.pult.network

import okhttp3.Interceptor
import com.example.pult.BuildConfig
import com.example.pult.PrefsManager
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

object NetworkClient {
    private var _api: PultApi? = null
    private var _okHttpClient: OkHttpClient? = null

    //singleton
    val api: PultApi
        get() = _api ?: throw IllegalStateException("NetworkClient is not initialized")

    val okHttpClient: OkHttpClient
        get() = _okHttpClient ?: throw IllegalStateException("NetworkClient is not initialized")

    fun initialize(prefsManager: PrefsManager) {
        val baseUrl = prefsManager.getServerUrl() ?: return
        val apiKey = prefsManager.getApiKey() ?: ""

        _okHttpClient = OkHttpClient.Builder()
            .addInterceptor { chain ->
                val request = chain.request().newBuilder()
                    .addHeader("X-Api-Key", apiKey)
                    .build()
                chain.proceed(request)
            }
            .build()

        val retrofit = Retrofit.Builder()
            .baseUrl(baseUrl)
            .client(_okHttpClient!!)
            .addConverterFactory(GsonConverterFactory.create())
            .build()

        _api = retrofit.create(PultApi::class.java)
    }
}
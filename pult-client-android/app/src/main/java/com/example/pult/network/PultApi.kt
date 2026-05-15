package com.example.pult.network

import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST

interface PultApi {
    @GET("metrics")
    suspend fun getMetrics(): SystemMetrics

    @POST("action")
    suspend fun postAction(@Body request: ActionRequest): ActionResponse
}

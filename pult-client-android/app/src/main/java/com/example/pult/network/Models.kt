package com.example.pult.network

import com.google.gson.annotations.SerializedName

data class SystemMetrics(
    @SerializedName("cpu_usage_percent") val cpuUsagePercent: Double,
    @SerializedName("ram_usage_percent") val ramUsagePercent: Double,
)

data class ActionRequest(
    @SerializedName("action_name") val actionName: String,
    @SerializedName("payload") val payload: String? = null
)

data class ActionResponse(
    @SerializedName("message") val message: String
)

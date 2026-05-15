package com.example.pult

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.contextaware.ContextAware
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.pult.db.DatabaseHelper
import com.example.pult.db.HistoryEntity
import com.example.pult.network.NetworkClient

class PcMonitorWorker(
    private val context: Context,
    workerParams: WorkerParameters
    ) : CoroutineWorker(context, workerParams) {

    override suspend fun doWork(): Result {
        val dbHelper = DatabaseHelper(context)

        return try {
            //request metrics
            val metrics = NetworkClient.api.getMetrics()
            val logMessage = "CPU: ${metrics.cpuUsagePercent}%, RAM: ${metrics.ramUsagePercent}%"

            dbHelper.insertAction(
                HistoryEntity(actionName = "background_monitor", resultMessage = logMessage)
            )

            if (metrics.cpuUsagePercent > 80.0) {
                showNotiffication("Внимание: Перегрев ПК!", "Загрузка процессора: ${metrics.cpuUsagePercent}%")
            }

            Result.success()
        } catch (e: Exception) {
            dbHelper.insertAction(
                HistoryEntity(actionName = "background_monitor_failed", resultMessage = e.message ?: "Error")
            )
            Result.retry()
        }
    }

    private fun showNotiffication(title: String, message: String) {
        val channelId = "pult_alerts_channel"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "PC Alerts",
                NotificationManager.IMPORTANCE_HIGH
            )
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel);
        }

        val builder = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setContentTitle(title)
            .setContentText(message)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)

        //check if allowed
        if (ActivityCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED) {
            NotificationManagerCompat.from(context).notify(1, builder.build())
        }
    }
}
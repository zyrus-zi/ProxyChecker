package com.example.proxychecker

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.*

class ProxyService : Service() {

    private val serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private val CHANNEL_ID = "proxy_checker_channel"
    private val NOTIFICATION_ID = 1001

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val pendingIntent = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notificationBuilder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Проверка прокси...")
            .setSmallIcon(android.R.drawable.ic_menu_sort_by_size)
            .setContentIntent(pendingIntent)
            .setOnlyAlertOnce(true)
            .setOngoing(true) // Уведомление нельзя смахнуть, пока сервис работает

        startForeground(NOTIFICATION_ID, notificationBuilder.build())

        // Подписываемся на прогресс из ProxyManager и обновляем уведомление
        serviceScope.launch {
            ProxyManager.progressFlow.collect { (current, total) ->
                if (total > 0) {
                    notificationBuilder
                        .setContentText("Проверено: $current из $total")
                        .setProgress(total, current, false)

                    val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                    manager.notify(NOTIFICATION_ID, notificationBuilder.build())
                }
            }
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()

        // 1. Убираем сервис из Foreground
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } else {
            @Suppress("DEPRECATION")
            stopForeground(true)
        }

        // 2. ГАРАНТИРОВАННО удаляем уведомление о прогрессе
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.cancel(NOTIFICATION_ID)
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID, "Proxy Checker Progress",
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    companion object {
        fun showCompletionNotification(context: Context) {
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

            // Создаем канал для финального уведомления (со звуком/вибрацией)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val channelId = "proxy_finish"
                val channelName = "Proxy Checker Alerts"
                // IMPORTANCE_DEFAULT или HIGH, чтобы был звук и всплывающее окно
                val channel = NotificationChannel(channelId, channelName, NotificationManager.IMPORTANCE_DEFAULT)
                manager.createNotificationChannel(channel)
            }

            val pendingIntent = PendingIntent.getActivity(
                context, 0, Intent(context, MainActivity::class.java),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val notification = NotificationCompat.Builder(context, "proxy_finish")
                .setContentTitle("Проверка завершена!")
                .setContentText("Все прокси успешно проверены.")
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setAutoCancel(true) // Исчезает при нажатии
                .setContentIntent(pendingIntent)
                .build()

            // ID 1002 - это новое уведомление, оно заменит визуально старое
            manager.notify(1002, notification)
        }
    }
}
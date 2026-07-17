package com.ahu_plus.notification

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.ahu_plus.MainActivity
import com.ahu_plus.data.local.SessionManager

object CampusCardAlertNotifier {
    suspend fun evaluate(context: Context, balance: Double, sessionManager: SessionManager) {
        if (!sessionManager.getCardBalanceAlertEnabled()) return
        val threshold = sessionManager.getCardBalanceAlertThreshold()
        if (balance > threshold) {
            sessionManager.setCardBalanceLastAlertAt(0L)
            return
        }
        val now = System.currentTimeMillis()
        if (now - sessionManager.getCardBalanceLastAlertAt() < ALERT_INTERVAL_MILLIS) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) return

        val appContext = context.applicationContext
        ensureChannel(appContext)
        val openApp = PendingIntent.getActivity(
            appContext,
            NOTIFICATION_ID,
            Intent(appContext, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(appContext, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setContentTitle("校园卡余额偏低")
            .setContentText("当前余额 %.2f 元，已低于 %.0f 元提醒阈值".format(balance, threshold))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setAutoCancel(true)
            .setContentIntent(openApp)
            .build()
        runCatching {
            NotificationManagerCompat.from(appContext).notify(NOTIFICATION_ID, notification)
        }.onSuccess {
            sessionManager.setCardBalanceLastAlertAt(now)
        }
    }

    private fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(NotificationManager::class.java)
        if (manager.getNotificationChannel(CHANNEL_ID) == null) {
            manager.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_ID,
                    "校园卡提醒",
                    NotificationManager.IMPORTANCE_DEFAULT,
                ).apply { description = "校园卡余额低于设定阈值时提醒" }
            )
        }
    }

    private const val CHANNEL_ID = "campus_card_alerts"
    private const val NOTIFICATION_ID = 0x414855
    private const val ALERT_INTERVAL_MILLIS = 24L * 60 * 60 * 1000
}

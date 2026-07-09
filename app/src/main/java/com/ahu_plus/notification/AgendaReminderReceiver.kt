package com.ahu_plus.notification

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import com.ahu_plus.MainActivity

/**
 * 日程提醒通知广播接收器。
 *
 * 与 [CourseReminderReceiver] 相同的套路,但日程内容(标题/时间/地点)随 Intent extra
 * 直接携带 —— 手动日程数据体量小,注册闹钟时一次性带上,避免 receiver 再读一遍
 * DataStore。点击通知 deep-link 到日程页。
 */
class AgendaReminderReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_AGENDA_REMINDER) return
        val key = intent.getStringExtra(EXTRA_EVENT_KEY) ?: return
        val title = intent.getStringExtra(EXTRA_TITLE) ?: "日程提醒"
        val body = intent.getStringExtra(EXTRA_BODY).orEmpty()
        Log.i(TAG, "onReceive: 日程提醒 key=$key")

        val appContext = context.applicationContext
        ensureChannel(appContext)

        val deepLink = Intent(appContext, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(MainActivity.EXTRA_DEEP_LINK, MainActivity.DEEP_LINK_AGENDA)
        }
        val pendingIntent = PendingIntent.getActivity(
            appContext,
            key.hashCode(),
            deepLink,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val notification = NotificationCompat.Builder(appContext, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        val nm = appContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(key.hashCode(), notification)
        Log.i(TAG, "日程通知已发送: $title")
    }

    private fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            if (nm.getNotificationChannel(CHANNEL_ID) == null) {
                val channel = NotificationChannel(
                    CHANNEL_ID,
                    "日程提醒",
                    NotificationManager.IMPORTANCE_HIGH,
                ).apply {
                    description = "在自定义日程开始前提醒"
                    enableLights(true)
                    enableVibration(true)
                }
                nm.createNotificationChannel(channel)
            }
        }
    }

    companion object {
        private const val TAG = "AgendaReminder"

        const val ACTION_AGENDA_REMINDER = "com.ahu_plus.notification.AGENDA_REMINDER"
        const val EXTRA_EVENT_KEY = "agenda_event_key"
        const val EXTRA_TITLE = "agenda_title"
        const val EXTRA_BODY = "agenda_body"

        const val CHANNEL_ID = "agenda_reminder"
    }
}

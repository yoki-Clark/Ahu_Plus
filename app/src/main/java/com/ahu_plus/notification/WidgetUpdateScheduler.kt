package com.ahu_plus.notification

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * versionCode 33 migration-only receiver class.
 *
 * It is intentionally absent from the manifest and cannot receive new work. The
 * class remains for one stable version so existing installs can cancel the two
 * PendingIntents created by the previous minute-ticker implementation.
 */
class WidgetUpdateScheduler : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) = Unit

    companion object {
        private const val ACTION_UPDATE_WIDGETS = "com.ahu_plus.widget.ACTION_UPDATE_WIDGETS"
        private const val REQUEST_DATA = 3001
        private const val REQUEST_TICKER = 3002

        fun cancel(context: Context) {
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
            listOf(REQUEST_DATA, REQUEST_TICKER).forEach { requestCode ->
                val pendingIntent = PendingIntent.getBroadcast(
                    context,
                    requestCode,
                    Intent(context, WidgetUpdateScheduler::class.java).setAction(ACTION_UPDATE_WIDGETS),
                    PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE,
                )
                if (pendingIntent != null) {
                    alarmManager.cancel(pendingIntent)
                    pendingIntent.cancel()
                }
            }
        }
    }
}

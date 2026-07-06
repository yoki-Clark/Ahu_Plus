package com.yourname.ahu_plus.notification

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import com.yourname.ahu_plus.ui.widget.TodayScheduleWidgetUpdater
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * Widget / 课程提醒统一调度器 (2026-06-22 重做倒计时刷新机制)。
 *
 * 双闹钟架构:
 *  - **数据闹钟** (RTC_WAKEUP, requestCode=3001): 每天 01:00 校准 + 触发课程提醒重排
 *  - **显示闹钟** (RTC setRepeating, requestCode=3002): 1 分钟重复,不唤醒 CPU。
 *    屏幕亮时约每分钟触发一次 widget 刷新,使"还剩 X 分钟"倒计时实时更新。
 *    数据来自本地缓存,无网络开销;屏幕灭时 widget 不可见,不浪费电量。
 *
 * onReceive 通过 requestCode 区分两种闹钟:
 *  - 3001 → 更新 widget + 重排课程提醒 + 自递归排下一次
 *  - 3002 → 仅更新 widget (轻量,不重排课程提醒)
 */
class WidgetUpdateScheduler : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_UPDATE_WIDGETS) return

        val isTicker = intent.getIntExtra(EXTRA_IS_TICKER, 0) == 1
        Log.i(TAG, "onReceive: isTicker=$isTicker")

        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                TodayScheduleWidgetUpdater.updateAll(context)
            } catch (e: Exception) {
                Log.e(TAG, "Widget 更新失败: ${e.message}", e)
            } finally {
                if (!isTicker) {
                    // 仅在数据闹钟触发时重排课程提醒
                    try {
                        CourseReminderScheduler.scheduleAll(context.applicationContext)
                        AgendaReminderScheduler.scheduleAll(context.applicationContext)
                    } catch (e: Exception) {
                        Log.e(TAG, "课程/日程提醒重排失败: ${e.message}", e)
                    }
                }
                pendingResult.finish()
            }
        }

        if (!isTicker) {
            scheduleNext(context)  // 自递归排下一个数据闹钟
        }
        // ticker 不需要自递归 — setRepeating 自动重复
    }

    companion object {
        private const val TAG = "WidgetUpdateScheduler"
        const val ACTION_UPDATE_WIDGETS = "com.yourname.ahu_plus.widget.ACTION_UPDATE_WIDGETS"
        const val EXTRA_IS_TICKER = "is_ticker"

        private const val REQUEST_DATA = 3001   // 数据校准闹钟
        private const val REQUEST_TICKER = 3002 // 显示刷新闹钟

        // ── 数据闹钟调度 (RTC_WAKEUP, 每天 01:00) ──────────

        fun scheduleNext(context: Context) {
            // 计算明天 01:00 作为下一次数据校准
            val now = LocalDateTime.now()
            val tomorrow = now.toLocalDate().plusDays(1)
            val nextTrigger = tomorrow.atTime(1, 0)
            val triggerMillis = nextTrigger.atZone(ZoneId.systemDefault())
                .toInstant().toEpochMilli()

            Log.i(TAG, "scheduleNext: data alarm at $nextTrigger")

            val pendingIntent = PendingIntent.getBroadcast(
                context, REQUEST_DATA,
                Intent(context, WidgetUpdateScheduler::class.java).apply {
                    action = ACTION_UPDATE_WIDGETS
                    putExtra(EXTRA_IS_TICKER, 0)
                },
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )

            val am = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !am.canScheduleExactAlarms()) {
                am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerMillis, pendingIntent)
            } else {
                am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerMillis, pendingIntent)
            }
        }

        // ── 显示刷新闹钟 (RTC setRepeating, 1 分钟) ─────────

        /**
         * 启动 1 分钟 RTC 重复闹钟,专用于 widget 倒计时刷新。
         *
         * RTC = 不唤醒 CPU。屏幕亮时闹钟约每分钟触发一次,
         * 从 widget 读本地缓存 + LocalTime.now() 重算倒计时。
         * 屏幕灭时 widget 不可见,闹钟不触发,不耗电。
         *
         * setRepeating 是 inexact 的,实际间隔可能 1~5 分钟,
         * 但远比 30 分钟的 updatePeriodMillis 准确。
         */
        fun scheduleTicker(context: Context) {
            val pendingIntent = PendingIntent.getBroadcast(
                context, REQUEST_TICKER,
                Intent(context, WidgetUpdateScheduler::class.java).apply {
                    action = ACTION_UPDATE_WIDGETS
                    putExtra(EXTRA_IS_TICKER, 1)
                },
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )

            val am = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
            try {
                am.setRepeating(
                    AlarmManager.RTC,
                    System.currentTimeMillis() + 60_000,
                    60_000L,
                    pendingIntent,
                )
                Log.i(TAG, "scheduleTicker: RTC setRepeating 60s 已启动")
            } catch (e: Exception) {
                Log.e(TAG, "scheduleTicker 失败: ${e.message}")
            }
        }

        // ── 取消 ──────────────────────────────────────────

        fun cancel(context: Context) {
            val am = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
            for (code in listOf(REQUEST_DATA, REQUEST_TICKER)) {
                val pi = PendingIntent.getBroadcast(
                    context, code,
                    Intent(context, WidgetUpdateScheduler::class.java).apply {
                        action = ACTION_UPDATE_WIDGETS
                    },
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                )
                am.cancel(pi)
            }
            Log.i(TAG, "cancel: 已取消所有调度")
        }
    }
}

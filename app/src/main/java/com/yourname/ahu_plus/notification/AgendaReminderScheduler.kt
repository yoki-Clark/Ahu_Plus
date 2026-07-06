package com.yourname.ahu_plus.notification

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.util.Log
import com.yourname.ahu_plus.data.GsonProvider
import com.yourname.ahu_plus.data.debug.DebugClock
import com.yourname.ahu_plus.data.local.AppDataStore
import com.yourname.ahu_plus.data.local.SessionManager
import com.yourname.ahu_plus.data.model.task.UserTask
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 日程提醒调度器。
 *
 * 照搬 [CourseReminderScheduler] 的设计:读手动日程([UserTask],存在 `user_tasks_json`),
 * 对每条设了 [UserTask.reminderMinutes] 且触发时刻在未来的日程注册一枚**单发**精确闹钟。
 * 用独立的 `agenda_reminder_keys` 持久化已注册 key,`cancelAll` 各清各的 —— 绝不与课程
 * 提醒的 `reminder_keys` 混用,否则两者会互相清除对方的闹钟。
 *
 * 触发时刻 = 日程开始时刻([UserTask.dueAt]) - reminderMinutes 分钟。
 */
object AgendaReminderScheduler {

    private const val TAG = "AgendaReminderScheduler"
    private const val KEY_PREFIX = "agenda|"

    /** 重排所有未来的日程提醒。调用方需在协程内(SessionManager.init 为 suspend)。 */
    suspend fun scheduleAll(context: Context) {
        val appContext = context.applicationContext
        val sessionManager = SessionManager(AppDataStore(appContext))
        sessionManager.init()

        // 先清掉上一轮(基于持久化 key),再重排 —— 避免删掉的日程闹钟成孤儿
        cancelAll(appContext, sessionManager)

        val json = sessionManager.getUserTasksJson() ?: run {
            Log.i(TAG, "scheduleAll: 无手动日程,跳过")
            return
        }
        val tasks = runCatching {
            GsonProvider.instance.fromJson(json, Array<UserTask>::class.java).toList()
        }.getOrNull() ?: run {
            Log.w(TAG, "scheduleAll: 手动日程 JSON 解析失败")
            return
        }

        val alarmManager = appContext.getSystemService(Context.ALARM_SERVICE) as? AlarmManager
            ?: return
        val now = DebugClock.nowMillis()
        val registeredKeys = ArrayList<String>()

        for (task in tasks) {
            if (task.completed) continue
            val start = task.dueAt ?: continue
            val lead = task.reminderMinutes ?: continue
            val triggerAt = start - lead * 60_000L
            if (triggerAt <= now) continue

            val key = "$KEY_PREFIX${task.id}"
            val pendingIntent = PendingIntent.getBroadcast(
                appContext,
                key.hashCode(),
                Intent(appContext, AgendaReminderReceiver::class.java).apply {
                    action = AgendaReminderReceiver.ACTION_AGENDA_REMINDER
                    putExtra(AgendaReminderReceiver.EXTRA_EVENT_KEY, key)
                    putExtra(AgendaReminderReceiver.EXTRA_TITLE, task.title)
                    putExtra(AgendaReminderReceiver.EXTRA_BODY, buildBody(task, lead))
                },
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                if (alarmManager.canScheduleExactAlarms()) {
                    alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pendingIntent)
                } else {
                    // 无精确闹钟权限 → 降级 inexact(时间可能漂移十几分钟)
                    alarmManager.set(AlarmManager.RTC_WAKEUP, triggerAt, pendingIntent)
                }
            } else {
                @Suppress("DEPRECATION")
                alarmManager.setExact(AlarmManager.RTC_WAKEUP, triggerAt, pendingIntent)
            }
            registeredKeys.add(key)
            Log.i(TAG, "  → ${task.title} @ ${Date(triggerAt)} (key=$key)")
        }

        sessionManager.saveAgendaReminderKeys(registeredKeys)
        Log.i(TAG, "scheduleAll: 注册了 ${registeredKeys.size} 个日程提醒")
    }

    /** 取消上一轮注册的所有日程提醒(读持久化 key,逐个 cancel)。 */
    suspend fun cancelAll(context: Context, sessionManager: SessionManager) {
        val appContext = context.applicationContext
        val alarmManager = appContext.getSystemService(Context.ALARM_SERVICE) as? AlarmManager
            ?: return
        val keys = sessionManager.getAgendaReminderKeys()
        if (keys.isEmpty()) return

        var cancelled = 0
        for (key in keys) {
            val intent = Intent(appContext, AgendaReminderReceiver::class.java).apply {
                action = AgendaReminderReceiver.ACTION_AGENDA_REMINDER
            }
            val pendingIntent = PendingIntent.getBroadcast(
                appContext,
                key.hashCode(),
                intent,
                PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE,
            )
            if (pendingIntent != null) {
                pendingIntent.cancel()
                alarmManager.cancel(pendingIntent)
                cancelled++
            }
        }
        sessionManager.saveAgendaReminderKeys(emptyList())
        Log.i(TAG, "cancelAll: 清理了 $cancelled / ${keys.size} 个日程提醒")
    }

    private fun buildBody(task: UserTask, leadMinutes: Int): String {
        val timeFmt = SimpleDateFormat(if (task.allDay) "MM-dd" else "MM-dd HH:mm", Locale.getDefault())
        return buildString {
            task.dueAt?.let { append(timeFmt.format(Date(it))) }
            task.location?.takeIf { it.isNotBlank() }?.let { append(" · ").append(it) }
            append(" · ")
            append(if (leadMinutes == 0) "现在开始" else "$leadMinutes 分钟后开始")
        }
    }
}

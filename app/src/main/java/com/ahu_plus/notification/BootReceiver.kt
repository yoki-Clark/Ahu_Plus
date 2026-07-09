package com.ahu_plus.notification

import android.appwidget.AppWidgetManager
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.util.Log
import com.ahu_plus.ui.widget.TodayScheduleWidgetReceiver
import com.ahu_plus.ui.widget.TodayScheduleWidgetUpdater
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * 开机广播接收器:重启后重新调度 widget 更新 + 课程提醒。
 *
 * 必要性:
 *  - AlarmManager 的所有 PendingIntent 在 BOOT_COMPLETED 后会被系统清空,
 *    必须重新注册,否则 widget 永远停在关机前的最后状态。
 *  - 用户从开机到打开 App 之间,如果课表需要刷新,只能靠这个 Receiver 启动更新。
 *
 * 兼容性:
 *  - 直接接收 BOOT_COMPLETED 在 API 26+ 仍然有效,但 manifest 必须显式声明
 *  - 部分国产 ROM 把开机广播放到 LOCKED_BOOT_COMPLETED(用户未解锁)之后才发,
 *    这里两个 action 都监听
 */
class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        val isRelevant = action == Intent.ACTION_BOOT_COMPLETED ||
            action == Intent.ACTION_LOCKED_BOOT_COMPLETED ||
            action == Intent.ACTION_MY_PACKAGE_REPLACED ||
            action == "android.intent.action.QUICKBOOT_POWERON"  // HTC / 部分国产 ROM
        if (!isRelevant) {
            return
        }
        Log.i(TAG, "onReceive: $action, 重新调度 widget + 课程提醒")

        val appContext = context.applicationContext
        WidgetUpdateScheduler.scheduleNext(appContext)
        WidgetUpdateScheduler.scheduleTicker(appContext)  // 2026-06-22: 重启后重设 1 分钟倒计时刷新

        val appWidgetManager = AppWidgetManager.getInstance(appContext)
        val widgetComponent = ComponentName(appContext, TodayScheduleWidgetReceiver::class.java)
        val hasWidgets = appWidgetManager.getAppWidgetIds(widgetComponent).isNotEmpty()

        // goAsync() 保活:onReceive 返回后系统可能立刻回收进程,
        // 用 pendingResult 撑到 suspend 工作(scheduleAll / widget 刷新)全部完成再 finish。
        // 不能用 GlobalScope(fire-and-forget,进程被杀协程即断)。
        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                CourseReminderScheduler.scheduleAll(appContext)
                AgendaReminderScheduler.scheduleAll(appContext)
                if (hasWidgets) {
                    TodayScheduleWidgetUpdater.updateAll(appContext)
                }
            } catch (e: Exception) {
                Log.e(TAG, "开机重排失败: ${e.message}", e)
            } finally {
                pendingResult.finish()
            }
        }
    }

    companion object {
        private const val TAG = "BootReceiver"
    }
}
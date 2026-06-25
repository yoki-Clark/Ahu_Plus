package com.yourname.ahu_plus.notification

import android.app.AlarmManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.ContextCompat

/**
 * 课程提醒所需运行时权限的检查与跳转工具。
 *
 * 课程提醒依赖两项在新系统上需用户显式授予的能力:
 *  - **POST_NOTIFICATIONS** (Android 13 / API 33+):没有它通知静默不显示
 *  - **SCHEDULE_EXACT_ALARM** (Android 12 / API 31+ 起,14+ 默认关闭):没有它
 *    精确闹钟被降级为 inexact,提醒时间可能漂移十几分钟
 *
 * Manifest 已声明全部权限,这里只负责"运行时是否真正可用"的判定与引导跳转。
 */
object ReminderPermissions {

    /** Android 13+ 是否已获得通知权限。低版本恒为 true(无需运行时授权)。 */
    fun hasNotificationPermission(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return true
        return ContextCompat.checkSelfPermission(
            context,
            android.Manifest.permission.POST_NOTIFICATIONS,
        ) == PackageManager.PERMISSION_GRANTED
    }

    /** Android 12+ 是否可调度精确闹钟。低版本恒为 true。 */
    fun canScheduleExactAlarms(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return true
        val am = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return false
        return am.canScheduleExactAlarms()
    }

    /** 是否仍有课程提醒相关权限缺失(用于决定是否展示引导)。 */
    fun hasMissingPermission(context: Context): Boolean =
        !hasNotificationPermission(context) || !canScheduleExactAlarms(context)

    /**
     * 打开本应用的系统通知设置页(供用户手动开启通知开关)。
     * 低版本回退到应用详情页。
     */
    fun openNotificationSettings(context: Context) {
        val intent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
        } else {
            appDetailsIntent(context)
        }
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { context.startActivity(intent) }
            .onFailure { context.startActivity(appDetailsIntent(context)) }
    }

    /**
     * 跳转到"闹钟和提醒"权限授权页(Android 12+)。低版本无此页,回退应用详情页。
     */
    fun openExactAlarmSettings(context: Context) {
        val intent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM)
                .setData(Uri.fromParts("package", context.packageName, null))
        } else {
            appDetailsIntent(context)
        }
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { context.startActivity(intent) }
            .onFailure { context.startActivity(appDetailsIntent(context)) }
    }

    private fun appDetailsIntent(context: Context): Intent =
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
            .setData(Uri.fromParts("package", context.packageName, null))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
}

package com.ahu_plus.data.developer

import android.Manifest
import android.app.ActivityManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.SystemClock
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.ahu_plus.AhuPlusApplication
import com.ahu_plus.BuildConfig
import com.ahu_plus.MainActivity
import com.ahu_plus.notification.AgendaReminderScheduler
import com.ahu_plus.notification.CourseReminderScheduler
import com.ahu_plus.notification.WidgetUpdateScheduler
import com.ahu_plus.service.ChaoxingStudyService
import com.ahu_plus.service.WeLearnStudyService
import com.ahu_plus.ui.widget.TodayScheduleWidgetReceiver
import com.ahu_plus.ui.widget.TodayScheduleWidgetUpdater
import java.io.File
import java.nio.charset.StandardCharsets
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

enum class DeveloperMaintenanceCategory {
    NOTIFICATION,
    WIDGET,
    REMINDER,
    STUDY_TASK,
    SESSION,
    RUNTIME,
}

enum class DeveloperMaintenanceRisk {
    LOW,
    MEDIUM,
    HIGH,
}

enum class DeveloperMaintenanceStatus {
    SUCCESS,
    SKIPPED,
    FAILED,
}

/** A maintenance operation exposed by [DeveloperMaintenanceRepository]. */
data class DeveloperMaintenanceAction(
    val id: String,
    val category: DeveloperMaintenanceCategory,
    val title: String,
    val description: String,
    val risk: DeveloperMaintenanceRisk,
    val requiresConfirmation: Boolean,
    /** This repository deliberately contains no operation that writes to a remote service. */
    val performsServerWrite: Boolean = false,
)

data class DeveloperMaintenanceResult(
    val actionId: String,
    val status: DeveloperMaintenanceStatus,
    val message: String,
    val startedAtEpochMillis: Long,
    val durationMillis: Long,
    /** Optional human-readable output, for example a runtime snapshot. */
    val payload: String? = null,
    /** Absolute path of a locally exported artifact, when the action creates one. */
    val exportedFilePath: String? = null,
    val errorType: String? = null,
)

object DeveloperMaintenanceActionIds {
    const val SEND_TEST_NOTIFICATION = "notification.send_test"
    const val REFRESH_TODAY_WIDGET = "widget.refresh_today"
    const val RESCHEDULE_WIDGET_UPDATES = "widget.reschedule_updates"
    const val RESCHEDULE_COURSE_REMINDERS = "reminder.reschedule_courses"
    const val RESCHEDULE_AGENDA_REMINDERS = "reminder.reschedule_agenda"
    const val RESCHEDULE_ALL_REMINDERS = "reminder.reschedule_all"
    const val STOP_CHAOXING_STUDY = "study.stop_chaoxing"
    const val STOP_WELEARN_STUDY = "study.stop_welearn"
    const val STOP_ALL_STUDY = "study.stop_all"
    const val CLEAR_CAS_SESSION = "session.clear_cas"
    const val CLEAR_JW_SESSION = "session.clear_jw"
    const val CLEAR_YCARD_SESSION = "session.clear_ycard"
    const val CLEAR_ADWMH_SESSION = "session.clear_adwmh"
    const val CLEAR_CHAOXING_SESSION = "session.clear_chaoxing"
    const val CLEAR_WELEARN_SESSION = "session.clear_welearn"
    const val CLEAR_CPROG_SESSION = "session.clear_cprog"
    const val CLEAR_ALL_SESSIONS = "session.clear_all"
    const val REQUEST_GC = "runtime.request_gc"
    const val READ_RUNTIME_INFO = "runtime.read_info"
    const val EXPORT_RUNTIME_INFO = "runtime.export_info"
}

/**
 * Local-only maintenance actions used by the developer center.
 *
 * The repository never performs an authenticated server write. Session actions only clear local
 * cookies/tokens and intentionally preserve saved credentials and business caches.
 */
class DeveloperMaintenanceRepository(
    private val application: AhuPlusApplication,
) {
    private data class ActionOutput(
        val status: DeveloperMaintenanceStatus = DeveloperMaintenanceStatus.SUCCESS,
        val message: String,
        val payload: String? = null,
        val exportedFilePath: String? = null,
    )

    private data class Definition(
        val action: DeveloperMaintenanceAction,
        val execute: suspend () -> ActionOutput,
    )

    private val context: Context = application.applicationContext
    private val executionMutex = Mutex()
    private val definitions: List<Definition> = buildDefinitions()
    private val definitionsById: Map<String, Definition> = definitions.associateBy { it.action.id }

    val actions: List<DeveloperMaintenanceAction> = definitions.map(Definition::action)

    fun action(id: String): DeveloperMaintenanceAction? = definitionsById[id]?.action

    suspend fun execute(id: String): DeveloperMaintenanceResult {
        val startedAt = System.currentTimeMillis()
        val startedNanos = System.nanoTime()
        val definition = definitionsById[id]
            ?: return result(
                id = id,
                status = DeveloperMaintenanceStatus.FAILED,
                message = "未知维护动作",
                startedAt = startedAt,
                startedNanos = startedNanos,
                errorType = "UnknownAction",
            )

        return executionMutex.withLock {
            try {
                val output = withContext(Dispatchers.IO) { definition.execute() }
                result(
                    id = id,
                    status = output.status,
                    message = output.message,
                    startedAt = startedAt,
                    startedNanos = startedNanos,
                    payload = output.payload,
                    exportedFilePath = output.exportedFilePath,
                )
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Throwable) {
                result(
                    id = id,
                    status = DeveloperMaintenanceStatus.FAILED,
                    message = "执行失败（${error.javaClass.simpleName.ifBlank { "UnknownError" }}）",
                    startedAt = startedAt,
                    startedNanos = startedNanos,
                    errorType = error.javaClass.name,
                )
            }
        }
    }

    private fun buildDefinitions(): List<Definition> = listOf(
        definition(
            id = DeveloperMaintenanceActionIds.SEND_TEST_NOTIFICATION,
            category = DeveloperMaintenanceCategory.NOTIFICATION,
            title = "发送开发者测试通知",
            description = "通过独立通知渠道发送一条本地测试通知。",
            risk = DeveloperMaintenanceRisk.LOW,
            execute = ::sendTestNotification,
        ),
        definition(
            id = DeveloperMaintenanceActionIds.REFRESH_TODAY_WIDGET,
            category = DeveloperMaintenanceCategory.WIDGET,
            title = "刷新今日课表 Widget",
            description = "使用本地缓存立即重绘全部今日课表 Widget。",
            risk = DeveloperMaintenanceRisk.LOW,
            execute = ::refreshTodayWidget,
        ),
        definition(
            id = DeveloperMaintenanceActionIds.RESCHEDULE_WIDGET_UPDATES,
            category = DeveloperMaintenanceCategory.WIDGET,
            title = "重排 Widget 更新任务",
            description = "取消后重新注册每日数据校准与每分钟显示刷新闹钟。",
            risk = DeveloperMaintenanceRisk.MEDIUM,
            execute = ::rescheduleWidgetUpdates,
        ),
        definition(
            id = DeveloperMaintenanceActionIds.RESCHEDULE_COURSE_REMINDERS,
            category = DeveloperMaintenanceCategory.REMINDER,
            title = "重排课程提醒",
            description = "按当前课表缓存和提醒设置重新注册未来课程闹钟。",
            risk = DeveloperMaintenanceRisk.MEDIUM,
            execute = ::rescheduleCourseReminders,
        ),
        definition(
            id = DeveloperMaintenanceActionIds.RESCHEDULE_AGENDA_REMINDERS,
            category = DeveloperMaintenanceCategory.REMINDER,
            title = "重排日程提醒",
            description = "按当前日程缓存重新注册日程闹钟。",
            risk = DeveloperMaintenanceRisk.MEDIUM,
            execute = ::rescheduleAgendaReminders,
        ),
        definition(
            id = DeveloperMaintenanceActionIds.RESCHEDULE_ALL_REMINDERS,
            category = DeveloperMaintenanceCategory.REMINDER,
            title = "重排全部提醒",
            description = "依次重排课程提醒与日程提醒。",
            risk = DeveloperMaintenanceRisk.MEDIUM,
            execute = ::rescheduleAllReminders,
        ),
        definition(
            id = DeveloperMaintenanceActionIds.STOP_CHAOXING_STUDY,
            category = DeveloperMaintenanceCategory.STUDY_TASK,
            title = "停止超星学习任务",
            description = "取消学习引擎并停止超星前台服务。",
            risk = DeveloperMaintenanceRisk.HIGH,
            requiresConfirmation = true,
            execute = ::stopChaoxingStudy,
        ),
        definition(
            id = DeveloperMaintenanceActionIds.STOP_WELEARN_STUDY,
            category = DeveloperMaintenanceCategory.STUDY_TASK,
            title = "停止 WeLearn 学习任务",
            description = "取消学习引擎并停止 WeLearn 前台服务。",
            risk = DeveloperMaintenanceRisk.HIGH,
            requiresConfirmation = true,
            execute = ::stopWeLearnStudy,
        ),
        definition(
            id = DeveloperMaintenanceActionIds.STOP_ALL_STUDY,
            category = DeveloperMaintenanceCategory.STUDY_TASK,
            title = "停止全部学习任务",
            description = "停止超星和 WeLearn 学习引擎及对应前台服务。",
            risk = DeveloperMaintenanceRisk.HIGH,
            requiresConfirmation = true,
            execute = ::stopAllStudy,
        ),
        sessionDefinition(
            DeveloperMaintenanceActionIds.CLEAR_CAS_SESSION,
            "清除 CAS / 门户会话",
            "清除 CAS 内存 Cookie 和本地门户 JSESSIONID，保留统一身份凭据。",
            ::clearCasSession,
        ),
        sessionDefinition(
            DeveloperMaintenanceActionIds.CLEAR_JW_SESSION,
            "清除教务会话",
            "清除教务 Cookie、SESSION 和 PSTSID，保留教务业务缓存。",
            ::clearJwSession,
        ),
        sessionDefinition(
            DeveloperMaintenanceActionIds.CLEAR_YCARD_SESSION,
            "清除一卡通会话",
            "清除 Ycard 内存 Cookie 和 JWT。",
            ::clearYcardSession,
        ),
        sessionDefinition(
            DeveloperMaintenanceActionIds.CLEAR_ADWMH_SESSION,
            "清除支付码会话",
            "清除 adwmh 内存 Cookie 和本地 SESSION。",
            ::clearAdwmhSession,
        ),
        sessionDefinition(
            DeveloperMaintenanceActionIds.CLEAR_CHAOXING_SESSION,
            "清除超星会话",
            "清除超星内存及持久化 Cookie，保留账号密码。",
            ::clearChaoxingSession,
        ),
        sessionDefinition(
            DeveloperMaintenanceActionIds.CLEAR_WELEARN_SESSION,
            "清除 WeLearn 会话",
            "清除 WeLearn 内存及持久化 Cookie，保留账号密码。",
            ::clearWeLearnSession,
        ),
        sessionDefinition(
            DeveloperMaintenanceActionIds.CLEAR_CPROG_SESSION,
            "清除 C 语言平台会话",
            "清除 C 语言平台 JWT、JSESSIONID 和用户 ID，保留登录信息。",
            ::clearCProgSession,
        ),
        definition(
            id = DeveloperMaintenanceActionIds.CLEAR_ALL_SESSIONS,
            category = DeveloperMaintenanceCategory.SESSION,
            title = "清除全部独立会话",
            description = "清除 CAS、教务、Ycard、adwmh、超星、WeLearn 和 C 语言平台会话。",
            risk = DeveloperMaintenanceRisk.HIGH,
            requiresConfirmation = true,
            execute = ::clearAllSessions,
        ),
        definition(
            id = DeveloperMaintenanceActionIds.REQUEST_GC,
            category = DeveloperMaintenanceCategory.RUNTIME,
            title = "请求垃圾回收",
            description = "向运行时发送 GC 请求；系统可选择延迟或忽略。",
            risk = DeveloperMaintenanceRisk.MEDIUM,
            execute = ::requestGarbageCollection,
        ),
        definition(
            id = DeveloperMaintenanceActionIds.READ_RUNTIME_INFO,
            category = DeveloperMaintenanceCategory.RUNTIME,
            title = "读取运行信息",
            description = "生成不包含 Cookie、Token 或密码的基础运行快照。",
            risk = DeveloperMaintenanceRisk.LOW,
            execute = ::readRuntimeInfo,
        ),
        definition(
            id = DeveloperMaintenanceActionIds.EXPORT_RUNTIME_INFO,
            category = DeveloperMaintenanceCategory.RUNTIME,
            title = "导出运行信息",
            description = "将脱敏运行快照写入应用私有的开发者报告目录。",
            risk = DeveloperMaintenanceRisk.LOW,
            execute = ::exportRuntimeInfo,
        ),
    )

    private fun definition(
        id: String,
        category: DeveloperMaintenanceCategory,
        title: String,
        description: String,
        risk: DeveloperMaintenanceRisk,
        requiresConfirmation: Boolean = false,
        execute: suspend () -> ActionOutput,
    ) = Definition(
        action = DeveloperMaintenanceAction(
            id = id,
            category = category,
            title = title,
            description = description,
            risk = risk,
            requiresConfirmation = requiresConfirmation,
        ),
        execute = execute,
    )

    private fun sessionDefinition(
        id: String,
        title: String,
        description: String,
        clear: suspend () -> Unit,
    ): Definition = definition(
        id = id,
        category = DeveloperMaintenanceCategory.SESSION,
        title = title,
        description = description,
        risk = DeveloperMaintenanceRisk.HIGH,
        requiresConfirmation = true,
        execute = {
            clear()
            ActionOutput(message = "$title 已清除")
        },
    )

    private fun sendTestNotification(): ActionOutput {
        val notificationManager = context.getSystemService(NotificationManager::class.java)
        if (!notificationManager.areNotificationsEnabled()) {
            return ActionOutput(
                status = DeveloperMaintenanceStatus.SKIPPED,
                message = "应用通知已被系统关闭",
            )
        }
        if (
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            return ActionOutput(
                status = DeveloperMaintenanceStatus.SKIPPED,
                message = "未授予通知权限",
            )
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                TEST_NOTIFICATION_CHANNEL_ID,
                "开发者测试",
                NotificationManager.IMPORTANCE_DEFAULT,
            ).apply {
                description = "开发者中心发送的本地测试通知"
                setShowBadge(false)
            }
            notificationManager.createNotificationChannel(channel)
            if (notificationManager.getNotificationChannel(TEST_NOTIFICATION_CHANNEL_ID)?.importance ==
                NotificationManager.IMPORTANCE_NONE
            ) {
                return ActionOutput(
                    status = DeveloperMaintenanceStatus.SKIPPED,
                    message = "开发者测试通知渠道已被系统关闭",
                )
            }
        }

        val openApp = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(context, TEST_NOTIFICATION_CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("AHU+ 开发者测试")
            .setContentText("通知链路工作正常 · ${LocalDateTime.now().format(DISPLAY_TIME_FORMAT)}")
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .setContentIntent(openApp)
            .build()
        notificationManager.notify(TEST_NOTIFICATION_ID, notification)
        return ActionOutput(message = "测试通知已发送")
    }

    private suspend fun refreshTodayWidget(): ActionOutput {
        val widgetManager = AppWidgetManager.getInstance(context)
        val receiver = ComponentName(context, TodayScheduleWidgetReceiver::class.java)
        val widgetCount = widgetManager.getAppWidgetIds(receiver).size
        if (widgetCount == 0) {
            return ActionOutput(
                status = DeveloperMaintenanceStatus.SKIPPED,
                message = "桌面上没有今日课表 Widget",
            )
        }
        TodayScheduleWidgetUpdater.updateAll(context)
        return ActionOutput(message = "已刷新 $widgetCount 个今日课表 Widget")
    }

    private fun rescheduleWidgetUpdates(): ActionOutput {
        WidgetUpdateScheduler.cancel(context)
        WidgetUpdateScheduler.scheduleNext(context)
        WidgetUpdateScheduler.scheduleTicker(context)
        return ActionOutput(message = "Widget 数据校准和显示刷新任务已重排")
    }

    private suspend fun rescheduleCourseReminders(): ActionOutput {
        CourseReminderScheduler.scheduleAll(context)
        return ActionOutput(message = "课程提醒已按当前设置重排")
    }

    private suspend fun rescheduleAgendaReminders(): ActionOutput {
        AgendaReminderScheduler.scheduleAll(context)
        return ActionOutput(message = "日程提醒已按当前数据重排")
    }

    private suspend fun rescheduleAllReminders(): ActionOutput {
        CourseReminderScheduler.scheduleAll(context)
        AgendaReminderScheduler.scheduleAll(context)
        return ActionOutput(message = "课程提醒和日程提醒已重排")
    }

    private fun stopChaoxingStudy(): ActionOutput {
        application.chaoxingStudyRepository.stop()
        val serviceWasRunning = context.stopService(Intent(context, ChaoxingStudyService::class.java))
        return ActionOutput(
            message = if (serviceWasRunning) {
                "超星学习任务和前台服务已停止"
            } else {
                "超星学习任务已停止；前台服务原本未运行"
            },
        )
    }

    private fun stopWeLearnStudy(): ActionOutput {
        application.weLearnStudyRepository.stop()
        val serviceWasRunning = context.stopService(Intent(context, WeLearnStudyService::class.java))
        return ActionOutput(
            message = if (serviceWasRunning) {
                "WeLearn 学习任务和前台服务已停止"
            } else {
                "WeLearn 学习任务已停止；前台服务原本未运行"
            },
        )
    }

    private fun stopAllStudy(): ActionOutput {
        application.chaoxingStudyRepository.stop()
        application.weLearnStudyRepository.stop()
        context.stopService(Intent(context, ChaoxingStudyService::class.java))
        context.stopService(Intent(context, WeLearnStudyService::class.java))
        return ActionOutput(message = "超星和 WeLearn 学习任务及前台服务已停止")
    }

    private suspend fun clearCasSession() {
        application.casAuthRepository.clearCookies()
        application.sessionManager.clearSession()
    }

    private suspend fun clearJwSession() {
        application.jwAuthRepository.clearCookies()
        application.sessionManager.clearJwSession()
        application.sessionManager.clearEvaluationJwt()
        application.jwAppAuthRepository.clearSession()
    }

    private fun clearYcardSession() {
        application.ycardRepository.clearCookies()
    }

    private suspend fun clearAdwmhSession() {
        application.adwmhCardRepository.clearCookies()
        application.sessionManager.clearAdwmhSessionId()
    }

    private suspend fun clearChaoxingSession() {
        application.chaoxingRepository.clearCookies()
    }

    private suspend fun clearWeLearnSession() {
        application.weLearnAuthRepository.clearCookies()
    }

    private suspend fun clearCProgSession() {
        application.cProgAuthRepository.clearSession()
    }

    private suspend fun clearAllSessions(): ActionOutput {
        val failures = mutableListOf<String>()
        val operations: List<Pair<String, suspend () -> Unit>> = listOf(
            "CAS" to ::clearCasSession,
            "教务" to ::clearJwSession,
            "Ycard" to { clearYcardSession() },
            "adwmh" to ::clearAdwmhSession,
            "超星" to ::clearChaoxingSession,
            "WeLearn" to ::clearWeLearnSession,
            "CProg" to ::clearCProgSession,
        )
        for ((name, clear) in operations) {
            try {
                clear()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                failures += name
            }
        }
        return if (failures.isEmpty()) {
            ActionOutput(message = "7 类独立会话已全部清除，凭据和业务缓存已保留")
        } else {
            ActionOutput(
                status = DeveloperMaintenanceStatus.FAILED,
                message = "部分会话未能清除",
                payload = "失败项：${failures.joinToString()}；其余会话已清除。",
            )
        }
    }

    private fun requestGarbageCollection(): ActionOutput {
        val before = usedHeapBytes()
        Runtime.getRuntime().gc()
        System.runFinalization()
        val after = usedHeapBytes()
        return ActionOutput(
            message = "已向运行时请求垃圾回收",
            payload = "请求前堆内存：${formatBytes(before)}\n请求返回后：${formatBytes(after)}\nGC 由系统调度，结果不保证立即可见。",
        )
    }

    private fun readRuntimeInfo(): ActionOutput {
        val report = buildRuntimeReport()
        return ActionOutput(
            message = "运行信息已读取",
            payload = report,
        )
    }

    private fun exportRuntimeInfo(): ActionOutput {
        val report = buildRuntimeReport()
        val exportDirectory = File(application.filesDir, RUNTIME_EXPORT_DIRECTORY)
        check(exportDirectory.exists() || exportDirectory.mkdirs()) {
            "Unable to create runtime export directory"
        }
        val file = File(
            exportDirectory,
            "ahu-plus-runtime-${LocalDateTime.now().format(FILE_TIME_FORMAT)}.txt",
        )
        file.outputStream().bufferedWriter(StandardCharsets.UTF_8).use { it.write(report) }
        return ActionOutput(
            message = "运行信息已导出",
            payload = report,
            exportedFilePath = file.absolutePath,
        )
    }

    private fun buildRuntimeReport(): String {
        val runtime = Runtime.getRuntime()
        val activityManager = context.getSystemService(ActivityManager::class.java)
        val memoryInfo = ActivityManager.MemoryInfo().also(activityManager::getMemoryInfo)
        val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
        val widgetCount = AppWidgetManager.getInstance(context).getAppWidgetIds(
            ComponentName(context, TodayScheduleWidgetReceiver::class.java),
        ).size
        val processUptimeMillis = (
            SystemClock.uptimeMillis() - android.os.Process.getStartUptimeMillis()
        ).coerceAtLeast(0L)

        return buildString {
            appendLine("AHU+ developer runtime report")
            appendLine("Generated: ${LocalDateTime.now().atZone(ZoneId.systemDefault())}")
            appendLine("Sensitive values: excluded")
            appendLine()
            appendLine("[Build]")
            appendLine("Application ID: ${BuildConfig.APPLICATION_ID}")
            appendLine("Version: ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
            appendLine("Build type: ${BuildConfig.BUILD_TYPE}")
            appendLine("Debuggable: ${BuildConfig.DEBUG}")
            appendLine("Last update: ${packageInfo.lastUpdateTime}")
            appendLine()
            appendLine("[Device]")
            appendLine("Manufacturer: ${Build.MANUFACTURER}")
            appendLine("Model: ${Build.MODEL}")
            appendLine("Android: ${Build.VERSION.RELEASE} / API ${Build.VERSION.SDK_INT}")
            appendLine("ABI: ${Build.SUPPORTED_ABIS.joinToString()}")
            appendLine("Locale: ${Locale.getDefault().toLanguageTag()}")
            appendLine("Time zone: ${ZoneId.systemDefault().id}")
            appendLine("Primary security provider: ${java.security.Security.getProviders().firstOrNull()?.name.orEmpty()}")
            appendLine()
            appendLine("[Runtime]")
            appendLine("PID: ${android.os.Process.myPid()}")
            appendLine("Process uptime: ${formatDuration(processUptimeMillis)}")
            appendLine("Heap used: ${formatBytes(usedHeapBytes())}")
            appendLine("Heap committed: ${formatBytes(runtime.totalMemory())}")
            appendLine("Heap maximum: ${formatBytes(runtime.maxMemory())}")
            appendLine("System memory available: ${formatBytes(memoryInfo.availMem)}")
            appendLine("System low-memory flag: ${memoryInfo.lowMemory}")
            appendLine("Active threads: ${Thread.activeCount()}")
            appendLine("Available processors: ${runtime.availableProcessors()}")
            appendLine("Files free space: ${formatBytes(application.filesDir.freeSpace)}")
            appendLine("Cache free space: ${formatBytes(application.cacheDir.freeSpace)}")
            appendLine()
            appendLine("[Local integrations]")
            appendLine("Notifications enabled: ${notificationsEnabled()}")
            appendLine("Today widget instances: $widgetCount")
            appendLine("Course reminders enabled: ${application.sessionManager.getCourseReminderEnabled()}")
            appendLine("Course reminder lead: ${application.sessionManager.getCourseReminderLeadMinutes()} min")
        }.trimEnd() + "\n"
    }

    private fun notificationsEnabled(): Boolean {
        val manager = context.getSystemService(NotificationManager::class.java)
        val permissionGranted = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
        return manager.areNotificationsEnabled() && permissionGranted
    }

    private fun usedHeapBytes(): Long {
        val runtime = Runtime.getRuntime()
        return runtime.totalMemory() - runtime.freeMemory()
    }

    private fun formatDuration(millis: Long): String {
        val totalSeconds = TimeUnit.MILLISECONDS.toSeconds(millis)
        val days = totalSeconds / 86_400L
        val hours = totalSeconds % 86_400L / 3_600L
        val minutes = totalSeconds % 3_600L / 60L
        val seconds = totalSeconds % 60L
        return if (days > 0L) {
            "%dd %02d:%02d:%02d".format(Locale.ROOT, days, hours, minutes, seconds)
        } else {
            "%02d:%02d:%02d".format(Locale.ROOT, hours, minutes, seconds)
        }
    }

    private fun formatBytes(bytes: Long): String {
        if (bytes < 1_024L) return "$bytes B"
        val kib = bytes / 1_024.0
        if (kib < 1_024.0) return "%.1f KiB".format(Locale.ROOT, kib)
        val mib = kib / 1_024.0
        if (mib < 1_024.0) return "%.1f MiB".format(Locale.ROOT, mib)
        return "%.1f GiB".format(Locale.ROOT, mib / 1_024.0)
    }

    private fun result(
        id: String,
        status: DeveloperMaintenanceStatus,
        message: String,
        startedAt: Long,
        startedNanos: Long,
        payload: String? = null,
        exportedFilePath: String? = null,
        errorType: String? = null,
    ) = DeveloperMaintenanceResult(
        actionId = id,
        status = status,
        message = message,
        startedAtEpochMillis = startedAt,
        durationMillis = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedNanos),
        payload = payload,
        exportedFilePath = exportedFilePath,
        errorType = errorType,
    )

    private companion object {
        const val TEST_NOTIFICATION_CHANNEL_ID = "developer_test_channel"
        const val TEST_NOTIFICATION_ID = 9_901
        const val RUNTIME_EXPORT_DIRECTORY = "downloads/developer-reports"
        val DISPLAY_TIME_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm:ss")
        val FILE_TIME_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss")
    }
}

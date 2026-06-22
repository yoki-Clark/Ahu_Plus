package com.yourname.ahu_plus.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.yourname.ahu_plus.AhuPlusApplication
import com.yourname.ahu_plus.MainActivity
import com.yourname.ahu_plus.R
import com.yourname.ahu_plus.data.model.CxCourse
import com.yourname.ahu_plus.data.model.CxStudyUiState
import com.yourname.ahu_plus.data.repository.ChaoxingStudyRepository
import com.yourname.ahu_plus.util.OverlayWindow
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

/**
 * 超星学习通后台学习 Service (2026-06-22 新增)。
 *
 * 启动场景: 用户在 ChaoxingTabScreen 点击"开始学习" → 启动本 Service。
 * Service 进入前台(startForeground),显示持续通知 + 可选的悬浮窗。
 *
 * 关键设计:
 *  - **ForegroundService**: Android 14+ 强制要求,否则后台被杀
 *  - **foregroundServiceType**: Android 14+ 必填,选 `dataSync`(同步数据)
 *  - **持久通知**: 用户必须能看到"学习中"通知,符合 Android 14 政策
 *  - **悬浮窗**: 可选,需 SYSTEM_ALERT_WINDOW 权限。Service 启动时检查,有权限则显示
 *  - **状态共享**: ChaoxingStudyRepository 是 app singleton,Service 和 UI 共享 studyState
 *  - **点击通知**: 回到 MainActivity(用户可手动切到学习通 Tab)
 */
class ChaoxingStudyService : Service() {

    private val tag = "CxStudyService"
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var overlay: OverlayWindow? = null
    private var stateCollectionJob: Job? = null

    override fun onCreate() {
        super.onCreate()
        android.util.Log.e(tag, "★★★ onCreate START ★★★")
        try {
            createNotificationChannel()

            // 启动前台服务 (Android 14+ 必须指定 foregroundServiceType)
            val initialNotification = buildNotification(CxStudyUiState(isRunning = true))
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                startForeground(NOTIFICATION_ID, initialNotification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
            } else {
                startForeground(NOTIFICATION_ID, initialNotification)
            }
            android.util.Log.e(tag, "startForeground 成功")

            // 尝试显示悬浮窗（需同时满足：用户已授权 + 设置中已开启）
            val app = applicationContext as AhuPlusApplication
            val overlayEnabled = app.sessionManager.showStudyOverlay
            val hasPerm = OverlayWindow.hasOverlayPermission(this)
            android.util.Log.e(tag, "悬浮窗权限: $hasPerm, 设置开关: $overlayEnabled")
            if (hasPerm && overlayEnabled) {
                try {
                    val overlayWin = OverlayWindow(this)
                    val shown = overlayWin.show()
                    android.util.Log.e(tag, "悬浮窗 show()=$shown")
                    if (shown) overlay = overlayWin
                } catch (e: Exception) {
                    android.util.Log.e(tag, "悬浮窗异常: ${e.message}", e)
                }
            }

            // 订阅学习状态
            stateCollectionJob = scope.launch {
                app.chaoxingStudyRepository.studyState.collect { state ->
                    updateNotification(state)
                    overlay?.update(state)
                }
            }
            android.util.Log.e(tag, "★★★ onCreate DONE ★★★")
        } catch (e: Exception) {
            android.util.Log.e(tag, "★★★ onCreate CRASH: ${e.message} ★★★", e)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.i(tag, "onStartCommand action=${intent?.action}")

        // START_STICKY 会在进程被系统杀死后自动重建 Service，
        // 但此时 intent 为 null，无法获取课程参数 → 直接停服，避免僵尸通知。
        if (intent == null) {
            Log.w(tag, "onStartCommand intent=null (系统重建), 停服避免僵尸通知")
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            return START_NOT_STICKY
        }

        when (intent.action) {
            ACTION_START -> {
                val courseIds = intent.getStringArrayListExtra(EXTRA_COURSE_IDS) ?: emptyList()
                val speed = intent.getFloatExtra(EXTRA_SPEED, 1.5f)
                val concurrency = intent.getIntExtra(EXTRA_CONCURRENCY, 4)
                val autoSubmit = intent.getBooleanExtra(EXTRA_AUTO_SUBMIT, true)

                startStudying(courseIds, speed, concurrency, autoSubmit)
            }
            ACTION_STOP -> {
                stopStudyingAndSelf()
            }
        }

        return START_STICKY
    }

    private fun startStudying(
        courseIds: List<String>,
        speed: Float,
        concurrency: Int,
        autoSubmit: Boolean,
    ) {
        val app = applicationContext as AhuPlusApplication
        scope.launch(Dispatchers.IO) {
            try {
                // 从 ChaoxingRepository 拉取课程列表(避免传整个对象 Intent 序列化复杂)
                val allCoursesResult = app.chaoxingRepository.getCourseList()
                val allCourses = allCoursesResult.getOrElse { emptyList() }
                val coursesToStudy = allCourses.filter { it.courseId in courseIds }

                if (coursesToStudy.isEmpty()) {
                    Log.w(tag, "未找到匹配的课程,停止服务")
                    stopStudyingAndSelf()
                    return@launch
                }

                app.chaoxingStudyRepository.studyAll(
                    courses = coursesToStudy,
                    speed = speed,
                    concurrency = concurrency,
                    autoSubmit = autoSubmit,
                )

                // 完成后自动停止服务
                Log.i(tag, "学习完成,自动停止服务")
                stopStudyingAndSelf()
            } catch (e: Exception) {
                Log.e(tag, "学习失败: ${e.message}", e)
                stopStudyingAndSelf()
            }
        }
    }

    private fun stopStudyingAndSelf() {
        val app = applicationContext as AhuPlusApplication
        app.chaoxingStudyRepository.stop()
        scope.launch { stopSelf() }
    }

    override fun onDestroy() {
        Log.i(tag, "onDestroy")
        overlay?.dismiss()
        overlay = null
        stateCollectionJob?.cancel()
        scope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "超星后台学习",
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = "学习通刷课时持续显示,可点击回到应用"
            setShowBadge(false)
        }
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(channel)
    }

    private fun buildNotification(state: CxStudyUiState): Notification {
        val title = "超星学习通"
        val text = buildString {
            val task = state.currentTask
            if (task != null) {
                val jobType = task.job.type
                when {
                    jobType == "video" && task.progress > 0f -> {
                        append("视频: ${(task.progress * 100).toInt()}% — ${task.job.name.take(18)}")
                    }
                    jobType == "video" -> {
                        append("视频加载中 — ${task.job.name.take(22)}")
                    }
                    jobType == "workid" -> {
                        append("答题: ${task.job.name.take(22)}")
                    }
                    jobType == "document" -> {
                        append("文档: ${task.job.name.take(22)}")
                    }
                    jobType == "read" -> {
                        append("阅读: ${task.job.name.take(22)}")
                    }
                    jobType == "audio" -> {
                        append("音频: ${task.job.name.take(22)}")
                    }
                    jobType == "live" -> {
                        append("直播: ${task.job.name.take(22)}")
                    }
                    else -> {
                        append(task.job.name.take(28))
                    }
                }
            } else if (state.totalTasks > 0) {
                append("已完成 ${state.completedCount}/${state.totalTasks}")
            } else {
                append("准备中...")
            }
        }

        val openIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                putExtra("open_tab", "chaoxing")  // 2026-06-22: 通知点击直接跳到学习通 Tab
            },
            PendingIntent.FLAG_IMMUTABLE,
        )

        val stopIntent = PendingIntent.getService(
            this, 1,
            Intent(this, ChaoxingStudyService::class.java).apply { action = ACTION_STOP },
            PendingIntent.FLAG_IMMUTABLE,
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(title)
            .setContentText(text)
            .setOngoing(true)
            .setSilent(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setContentIntent(openIntent)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "停止", stopIntent)
            .build()
    }

    private fun updateNotification(state: CxStudyUiState) {
        val nm = getSystemService(NotificationManager::class.java)
        nm.notify(NOTIFICATION_ID, buildNotification(state))
    }

    companion object {
        private const val NOTIFICATION_ID = 2001
        private const val CHANNEL_ID = "chaoxing_study_channel"

        const val ACTION_START = "com.yourname.ahu_plus.action.START_STUDY"
        const val ACTION_STOP = "com.yourname.ahu_plus.action.STOP_STUDY"

        const val EXTRA_COURSE_IDS = "course_ids"
        const val EXTRA_SPEED = "speed"
        const val EXTRA_CONCURRENCY = "concurrency"
        const val EXTRA_AUTO_SUBMIT = "auto_submit"

        /** 启动服务开始学习 */
        fun start(
            context: Context,
            courseIds: List<String>,
            speed: Float = 1.5f,
            concurrency: Int = 4,
            autoSubmit: Boolean = true,
        ) {
            android.util.Log.e("CxStudyService", "★★★ start() called, courseIds=$courseIds ★★★")
            try {
                val intent = Intent(context, ChaoxingStudyService::class.java).apply {
                    action = ACTION_START
                    putStringArrayListExtra(EXTRA_COURSE_IDS, ArrayList(courseIds))
                    putExtra(EXTRA_SPEED, speed)
                    putExtra(EXTRA_CONCURRENCY, concurrency)
                    putExtra(EXTRA_AUTO_SUBMIT, autoSubmit)
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(intent)
                } else {
                    context.startService(intent)
                }
                android.util.Log.e("CxStudyService", "★★★ startForegroundService 调用成功 ★★★")
            } catch (e: Exception) {
                android.util.Log.e("CxStudyService", "★★★ start() CRASH: ${e.message} ★★★", e)
            }
        }

        /** 停止服务 */
        fun stop(context: Context) {
            val intent = Intent(context, ChaoxingStudyService::class.java).apply {
                action = ACTION_STOP
            }
            context.startService(intent)
        }
    }
}

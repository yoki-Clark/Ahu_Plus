package com.ahu_plus.service

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
import com.ahu_plus.AhuPlusApplication
import com.ahu_plus.MainActivity
import com.ahu_plus.data.model.CxStudyUiState
import com.ahu_plus.data.repository.CxAnswerMode
import com.ahu_plus.util.OverlayWindow
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.withContext

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

    /** Exactly one study run may own this service at a time. */
    private val activeJobLock = Any()

    private data class StudyRun(
        val generation: Long,
        val startId: Int,
        val job: Job,
    )

    @Volatile
    private var activeStudyRun: StudyRun? = null

    private var nextRunGeneration = 0L
    private var latestStartId = 0

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
                    android.util.Log.e(tag, "悬浮窗异常: ${safeErrorLabel(e)}")
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
            android.util.Log.e(tag, "onCreate 失败: ${safeErrorLabel(e)}")
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.i(tag, "onStartCommand action=${intent?.action}")
        synchronized(activeJobLock) {
            latestStartId = startId
        }

        // START_STICKY 会在进程被系统杀死后自动重建 Service，
        // 但此时 intent 为 null，无法获取课程参数 → 直接停服，避免僵尸通知。
        if (intent == null) {
            Log.w(tag, "onStartCommand intent=null (系统重建), 停服避免僵尸通知")
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf(startId)
            return START_NOT_STICKY
        }

        when (intent.action) {
            ACTION_START -> {
                val courseKeys = intent.getStringArrayListExtra(EXTRA_COURSE_KEYS) ?: emptyList()
                val speed = intent.getFloatExtra(EXTRA_SPEED, 1.0f)
                val concurrency = intent.getIntExtra(EXTRA_CONCURRENCY, 1)
                val answerMode = CxAnswerMode.fromSetting(intent.getStringExtra(EXTRA_ANSWER_MODE))
                val enabledTaskTypes = if (intent.hasExtra(EXTRA_ENABLED_TASK_TYPES)) {
                    intent.getStringArrayListExtra(EXTRA_ENABLED_TASK_TYPES)?.toSet() ?: emptySet()
                } else {
                    null
                }

                if (!startStudying(startId, courseKeys, speed, concurrency, answerMode, enabledTaskTypes)) {
                    notifyStartRejected()
                }
            }
            ACTION_STOP -> {
                stopStudyingAndSelf(startId)
            }
        }

        return START_NOT_STICKY
    }

    private fun startStudying(
        startId: Int,
        courseKeys: List<String>,
        speed: Float,
        concurrency: Int,
        answerMode: CxAnswerMode,
        enabledTaskTypes: Set<String>?,
    ): Boolean {
        val app = applicationContext as AhuPlusApplication
        synchronized(activeJobLock) {
            if (activeStudyRun?.job?.isActive == true) {
                Log.w(tag, "拒绝重复启动：已有学习任务运行")
                return false
            }

            val generation = ++nextRunGeneration
            // LAZY makes the guard assignment and coroutine start one atomic section.
            val job = scope.launch(Dispatchers.IO, start = CoroutineStart.LAZY) {
                try {
                    if (courseKeys.isEmpty()) {
                        Log.w(tag, "未选择课程，停止服务")
                        return@launch
                    }
                    // 从 ChaoxingRepository 拉取课程列表(避免传整个对象 Intent 序列化复杂)
                    val allCoursesResult = app.chaoxingRepository.getCourseList()
                    if (allCoursesResult.isFailure) {
                        val error = allCoursesResult.exceptionOrNull()
                        Log.w(tag, "获取课程列表失败: ${safeErrorLabel(error)}")
                        throw (error as? Exception)
                            ?: IllegalStateException("course list request failed", error)
                    }
                    val allCourses = allCoursesResult.getOrNull().orEmpty()
                    if (allCourses.isEmpty()) {
                        Log.w(tag, "课程列表为空，停止服务")
                        return@launch
                    }
                    val selectedKeys = courseKeys.toSet()
                    val coursesToStudy = allCourses.filter { course ->
                        "${course.courseId}_${course.clazzId}" in selectedKeys
                    }

                    if (coursesToStudy.isEmpty()) {
                        Log.w(tag, "未找到匹配的课程,停止服务")
                        return@launch
                    }

                    app.chaoxingStudyRepository.studyAll(
                        courses = coursesToStudy,
                        speed = speed,
                        concurrency = concurrency,
                        answerMode = answerMode,
                        enabledTaskTypes = enabledTaskTypes ?: app.sessionManager.getCxTaskTypes(),
                    )

                    Log.i(tag, "学习完成,自动停止服务")
                } catch (_: CancellationException) {
                    Log.i(tag, "学习已由用户停止")
                } catch (e: Exception) {
                    Log.e(tag, "学习失败: ${safeErrorLabel(e)}")
                } finally {
                    withContext(NonCancellable + Dispatchers.Main.immediate) {
                        finishRunIfOwned(generation)
                    }
                }
            }
            activeStudyRun = StudyRun(generation = generation, startId = startId, job = job)
            job.start()
            return true
        }
    }

    private fun finishRunIfOwned(generation: Long) {
        val stopStartId = synchronized(activeJobLock) {
            val current = activeStudyRun
            if (current?.generation != generation) return
            activeStudyRun = null
            latestStartId
        }
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf(stopStartId)
    }

    private fun stopStudyingAndSelf(stopStartId: Int) {
        val app = applicationContext as AhuPlusApplication
        val run = synchronized(activeJobLock) { activeStudyRun }
        // Cancel the service job first: repository.stop() alone cannot interrupt the
        // course-list request before studyAll has installed its own Job reference.
        run?.job?.cancel()
        if (run != null) {
            app.chaoxingStudyRepository.stop()
        }
        scope.launch(Dispatchers.Main.immediate) {
            run?.job?.join()
            val canStop = synchronized(activeJobLock) {
                latestStartId == stopStartId && activeStudyRun == null
            }
            if (canStop) {
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf(stopStartId)
            }
        }
    }

    private fun notifyStartRejected() {
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setContentTitle("超星学习通")
            .setContentText("已有学习任务正在运行")
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .setTimeoutAfter(5_000L)
            .build()
        getSystemService(NotificationManager::class.java)
            .notify(REJECT_NOTIFICATION_ID, notification)
    }

    private fun safeErrorLabel(error: Throwable?): String =
        error?.javaClass?.simpleName.orEmpty().ifBlank { "UnknownError" }

    override fun onDestroy() {
        Log.i(tag, "onDestroy")
        val ownedRun = synchronized(activeJobLock) {
            activeStudyRun.also { activeStudyRun = null }
        }
        ownedRun?.job?.cancel()
        if (ownedRun != null) {
            (applicationContext as? AhuPlusApplication)?.chaoxingStudyRepository?.stop()
        }
        overlay?.dismiss()
        overlay = null
        stateCollectionJob?.cancel()
        scope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
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
        private const val REJECT_NOTIFICATION_ID = 2002
        private const val CHANNEL_ID = "chaoxing_study_channel"

        const val ACTION_START = "com.ahu_plus.action.START_STUDY"
        const val ACTION_STOP = "com.ahu_plus.action.STOP_STUDY"

        const val EXTRA_COURSE_KEYS = "course_keys"
        const val EXTRA_SPEED = "speed"
        const val EXTRA_CONCURRENCY = "concurrency"
        const val EXTRA_ANSWER_MODE = "answer_mode"
        const val EXTRA_ENABLED_TASK_TYPES = "enabled_task_types"

        /** 启动服务开始学习 */
        fun start(
            context: Context,
            courseKeys: List<String>,
            speed: Float = 1.0f,
            concurrency: Int = 1,
            answerMode: CxAnswerMode = CxAnswerMode.SKIP,
            enabledTaskTypes: Set<String>,
        ) {
            android.util.Log.i("CxStudyService", "start() called, courseCount=${courseKeys.size}")
            try {
                val intent = Intent(context, ChaoxingStudyService::class.java).apply {
                    action = ACTION_START
                    putStringArrayListExtra(EXTRA_COURSE_KEYS, ArrayList(courseKeys))
                    putExtra(EXTRA_SPEED, speed)
                    putExtra(EXTRA_CONCURRENCY, concurrency)
                    putExtra(EXTRA_ANSWER_MODE, answerMode.settingValue)
                    putStringArrayListExtra(EXTRA_ENABLED_TASK_TYPES, ArrayList(enabledTaskTypes))
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(intent)
                } else {
                    context.startService(intent)
                }
                android.util.Log.e("CxStudyService", "★★★ startForegroundService 调用成功 ★★★")
            } catch (e: Exception) {
                android.util.Log.e("CxStudyService", "start() failed: ${e.javaClass.simpleName}")
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

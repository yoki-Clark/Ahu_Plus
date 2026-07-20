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
import com.ahu_plus.data.diagnostic.SafeLog as Log
import androidx.core.app.NotificationCompat
import com.ahu_plus.AhuPlusApplication
import com.ahu_plus.MainActivity
import com.ahu_plus.R
import com.ahu_plus.data.model.WeLearnStudyUiState
import com.ahu_plus.data.job.BackgroundJobCommand
import com.ahu_plus.data.job.BackgroundJobInterruption
import com.ahu_plus.data.job.BackgroundJobPayload
import com.ahu_plus.data.job.BackgroundJobPlatform
import com.ahu_plus.data.job.BackgroundJobStartResult
import com.ahu_plus.util.OverlayWindow
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * WeLearn 随行课堂后台刷课 Service (2026-06-27)。
 *
 * 仿 ChaoxingStudyService:
 *  - ForegroundService + 持续通知(Android 14+ 强制)
 *  - 可选悬浮窗(复用 OverlayWindow,通过 update(Float) 重载)
 *  - 状态共享:Service 订阅 WeLearnStudyRepository.studyState
 *  - 点击通知回到 App(暂未带 open_tab 跳 tab,主屏底部 tab 用户自选)
 *
 * 流程:Service onStartCommand 收到 ACTION_START → 拉课程树 → 启动 WeLearnStudyRepository.studyCourse
 *      → 完成后 stopSelf。stop 通过 ACTION_STOP 触发 studyRepo.stop()。
 */
class WeLearnStudyService : Service() {

    private val tag = "WeLearnStudyService"
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var overlay: OverlayWindow? = null
    private var stateCollectionJob: Job? = null
    private var activeRun: Job? = null
    private var activeJobId: String? = null
    private var lastPersistedProgress: Pair<Int, Int>? = null

    override fun onCreate() {
        super.onCreate()
        try {
            createNotificationChannel()

            val initial = buildNotification(WeLearnStudyUiState(isRunning = true))
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                startForeground(NOTIFICATION_ID, initial, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
            } else {
                startForeground(NOTIFICATION_ID, initial)
            }
            Log.i(tag, "startForeground 成功")

            val app = applicationContext as AhuPlusApplication
            val overlayEnabled = app.sessionManager.showStudyOverlay
            val hasPerm = OverlayWindow.hasOverlayPermission(this)
            Log.i(tag, "悬浮窗权限=$hasPerm 开关=$overlayEnabled")
            if (hasPerm && overlayEnabled) {
                runCatching {
                    val win = OverlayWindow(this)
                    if (win.show()) overlay = win
                }.onFailure { Log.w(tag, "悬浮窗异常: ${it.message}") }
            }

            stateCollectionJob = scope.launch {
                app.weLearnStudyRepository.studyState.collect { state ->
                    updateNotification(state)
                    overlay?.update(state.progress)
                    val jobId = activeJobId
                    val progress = (state.completedCount + state.partialCount) to state.totalCount
                    if (jobId != null && progress != lastPersistedProgress) {
                        lastPersistedProgress = progress
                        app.applicationScope.launch(Dispatchers.IO) {
                            app.backgroundJobController.updateProgress(jobId, progress.first, progress.second)
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(tag, "onCreate 异常", e)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.i(tag, "onStartCommand action=${intent?.action}")
        if (intent == null) {
            Log.w(tag, "intent=null (系统重建), 停服避免僵尸通知")
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            return START_NOT_STICKY
        }
        when (intent.action) {
            ACTION_START -> {
                val jobId = intent.getStringExtra(EXTRA_JOB_ID) ?: run {
                    Log.w(tag, "缺少 jobId, 停服")
                    stopForeground(STOP_FOREGROUND_REMOVE); stopSelf(); return START_NOT_STICKY
                }
                startStudying(jobId)
            }
            ACTION_STOP -> {
                val jobId = intent.getStringExtra(EXTRA_JOB_ID) ?: activeJobId
                if (jobId == null) stopStudyingAndSelf() else {
                    scope.launch { (applicationContext as AhuPlusApplication).backgroundJobController.cancel(jobId) }
                }
            }
        }
        return START_NOT_STICKY
    }

    private fun startStudying(jobId: String) {
        val app = applicationContext as AhuPlusApplication
        if (activeRun?.isActive == true) return
        val run = scope.launch(Dispatchers.IO, start = CoroutineStart.LAZY) {
            try {
                val controller = app.backgroundJobController
                val record = controller.get(jobId)
                    ?: throw IllegalArgumentException("missing background job")
                require(record.platform == BackgroundJobPlatform.WELEARN) { "wrong job platform" }
                val payload = record.payload
                require(payload.courseId.isNotBlank()) { "missing course id" }
                controller.markRunning(jobId)
                // 1. 自动登录(若 cookie 失效)
                if (!app.weLearnAuthRepository.isLoggedIn()) {
                    val ok = app.weLearnAuthRepository.autoLoginIfPossible()
                    if (!ok) {
                        controller.markFailed(
                            jobId,
                            com.ahu_plus.data.job.BackgroundJobFailure.AUTHENTICATION_REQUIRED,
                        )
                        return@launch
                    }
                }

                // 2. 拉课程树
                val tree = app.weLearnRepository.getCourseTree(payload.courseId).getOrThrow()

                // 3. 启动刷课(fullMode=true 时已完成的 sco 也重提交;unitIndices!=null 时只刷选中单元;heartbeatEnabled=true 时每节跑 N 秒心跳)
                app.weLearnStudyRepository.studyCourse(
                    tree = tree,
                    accuracyRange = parseAccuracy(payload.accuracy),
                    fullMode = payload.fullMode,
                    unitIndices = payload.unitIndices.takeIf { it.isNotEmpty() }?.toIntArray(),
                    heartbeatEnabled = payload.heartbeatEnabled,
                    heartbeatSecondsPerSco = payload.heartbeatSecondsPerSco,
                )
                if (!app.weLearnStudyRepository.studyState.value.error.isNullOrBlank()) {
                    throw IllegalStateException("WeLearn study execution failed")
                }
                controller.markSucceeded(jobId)
                Log.i(tag, "刷课完成,停服")
            } catch (_: CancellationException) {
                Log.i(tag, "刷课已取消")
            } catch (e: Exception) {
                app.backgroundJobController.markFailed(
                    jobId,
                    app.backgroundJobController.classifyFailure(e),
                )
                Log.e(tag, "刷课异常", e)
            } finally {
                app.backgroundJobController.detachCanceller(jobId)
                activeRun = null
                activeJobId = null
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
        }
        activeRun = run
        activeJobId = jobId
        app.backgroundJobController.attachCanceller(jobId) {
            run.cancel()
            app.weLearnStudyRepository.stop()
        }
        run.start()
    }

    private fun stopStudyingAndSelf() {
        runCatching { (applicationContext as AhuPlusApplication).weLearnStudyRepository.stop() }
        scope.launch { stopSelf() }
    }

    override fun onDestroy() {
        Log.i(tag, "onDestroy")
        val interruptedJobId = activeJobId
        activeRun?.cancel()
        activeRun = null
        activeJobId = null
        if (interruptedJobId != null) {
            val app = applicationContext as AhuPlusApplication
            app.applicationScope.launch(Dispatchers.IO) {
                val current = app.backgroundJobController.get(interruptedJobId)
                if (current?.phase?.isActive == true) {
                    app.backgroundJobController.markInterrupted(
                        interruptedJobId,
                        BackgroundJobInterruption.SERVICE_DESTROYED,
                    )
                }
            }
        }
        overlay?.dismiss()
        overlay = null
        stateCollectionJob?.cancel()
        scope.cancel()
        super.onDestroy()
    }

    override fun onTimeout(startId: Int, fgsType: Int) {
        val app = applicationContext as AhuPlusApplication
        val jobId = activeJobId
        activeRun?.cancel()
        app.weLearnStudyRepository.stop()
        if (jobId != null) {
            app.applicationScope.launch(Dispatchers.IO) {
                app.backgroundJobController.markInterrupted(jobId, BackgroundJobInterruption.SYSTEM_TIMEOUT)
            }
        }
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf(startId)
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val channel = NotificationChannel(
            CHANNEL_ID,
            "WeLearn 后台刷课",
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = "WeLearn 刷课时持续显示,可点击回到应用"
            setShowBadge(false)
        }
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    private fun buildNotification(state: WeLearnStudyUiState): Notification {
        val text = when {
            state.isRunning && state.totalCount > 0 -> {
                val done = state.completedCount + state.partialCount
                val base = "进度 ${state.completedCount}✓ ${state.partialCount}△ ${state.failedCount}✗ ($done/${state.totalCount}) — ${state.currentScoLocation.take(20)}"
                // 2026-06-28:刷时长进行中时追加 "· 已刷 X/Y 分"
                if (state.currentScoHeartbeatSec > 0) {
                    val failTag = if (state.heartbeatKeepFails > 0) " · ⚠${state.heartbeatKeepFails}" else ""
                    "$base · 已刷 ${state.elapsedSec / 60}/${state.currentScoHeartbeatSec / 60} 分$failTag"
                } else base
            }
            state.isRunning -> "准备中…"
            else -> "已停止"
        }

        val openIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                putExtra(MainActivity.EXTRA_DEEP_LINK, MainActivity.DEEP_LINK_WELEARN)
            },
            PendingIntent.FLAG_IMMUTABLE,
        )
        val stopIntent = PendingIntent.getService(
            this, 1,
            Intent(this, WeLearnStudyService::class.java).apply {
                action = ACTION_STOP
                putExtra(EXTRA_JOB_ID, activeJobId)
            },
            PendingIntent.FLAG_IMMUTABLE,
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("WeLearn 随行课堂")
            .setContentText(text)
            .setOngoing(true)
            .setSilent(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setContentIntent(openIntent)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "停止", stopIntent)
            .build()
    }

    private fun updateNotification(state: WeLearnStudyUiState) {
        getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, buildNotification(state))
    }

    companion object {
        private const val NOTIFICATION_ID = 2002  // 不和超星 2001 撞
        private const val CHANNEL_ID = "welearn_study_channel"

        const val ACTION_START = "com.ahu_plus.action.WELEARN_START"
        const val ACTION_STOP = "com.ahu_plus.action.WELEARN_STOP"

        const val EXTRA_JOB_ID = "job_id"

        /** UI 入口:启动 Service 刷指定 cid */
        fun start(
            context: Context,
            cid: String,
            accuracy: String = "100",
            fullMode: Boolean = false,
            unitIndices: IntArray? = null,
            heartbeatEnabled: Boolean = true,  // 2026-06-28
            heartbeatSecondsPerSco: Int = 180,  // 2026-06-28
        ) {
            val app = context.applicationContext as AhuPlusApplication
            app.applicationScope.launch(Dispatchers.IO) {
                val result = app.backgroundJobController.start(
                    BackgroundJobCommand(
                        platform = BackgroundJobPlatform.WELEARN,
                        payload = BackgroundJobPayload(
                            courseId = cid,
                            accuracy = accuracy,
                            fullMode = fullMode,
                            unitIndices = unitIndices?.toList().orEmpty(),
                            heartbeatEnabled = heartbeatEnabled,
                            heartbeatSecondsPerSco = heartbeatSecondsPerSco,
                        ),
                    )
                )
                if (result is BackgroundJobStartResult.Accepted) {
                    runCatching { launchService(context, result.record.id) }
                        .onFailure {
                            app.backgroundJobController.markFailed(
                                result.record.id,
                                app.backgroundJobController.classifyFailure(it),
                            )
                        }
                }
            }
        }

        /** UI 入口:停止 */
        fun stop(context: Context) {
            val app = context.applicationContext as AhuPlusApplication
            app.applicationScope.launch(Dispatchers.IO) {
                app.backgroundJobController.active(BackgroundJobPlatform.WELEARN)?.let {
                    app.backgroundJobController.cancel(it.id)
                }
            }
        }

        fun resume(context: Context, jobId: String) {
            val app = context.applicationContext as AhuPlusApplication
            app.applicationScope.launch(Dispatchers.IO) {
                if (app.backgroundJobController.resume(jobId) is BackgroundJobStartResult.Accepted) {
                    runCatching { launchService(context, jobId) }
                        .onFailure {
                            app.backgroundJobController.markFailed(
                                jobId,
                                app.backgroundJobController.classifyFailure(it),
                            )
                        }
                }
            }
        }

        private fun launchService(context: Context, jobId: String) {
            val intent = Intent(context, WeLearnStudyService::class.java).apply {
                action = ACTION_START
                putExtra(EXTRA_JOB_ID, jobId)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) context.startForegroundService(intent)
            else context.startService(intent)
        }

        /** "100" → 100..100, "70,100" → 70..100, 非法默认 100..100 */
        internal fun parseAccuracy(spec: String): IntRange {
            val parts = spec.split(",").mapNotNull { it.trim().toIntOrNull() }
            return when (parts.size) {
                2 -> parts[0].coerceIn(0, 100)..parts[1].coerceIn(0, 100)
                1 -> parts[0].coerceIn(0, 100)..parts[0].coerceIn(0, 100)
                else -> 100..100
            }
        }
    }
}

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
import com.yourname.ahu_plus.data.model.WeLearnStudyUiState
import com.yourname.ahu_plus.util.OverlayWindow
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
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
                val cid = intent.getStringExtra(EXTRA_CID) ?: run {
                    Log.w(tag, "缺少 cid, 停服")
                    stopForeground(STOP_FOREGROUND_REMOVE); stopSelf(); return START_NOT_STICKY
                }
                val accuracy = intent.getStringExtra(EXTRA_ACCURACY) ?: "100"
                val fullMode = intent.getBooleanExtra(EXTRA_FULL_MODE, false)  // 2026-06-28
                val unitIndices = intent.getIntArrayExtra(EXTRA_UNIT_INDICES)  // 2026-06-28
                // 2026-06-28:刷时长参数(默认开,每节 3 分钟)
                val heartbeatEnabled = intent.getBooleanExtra(EXTRA_HEARTBEAT_ENABLED, true)
                val heartbeatSecondsPerSco = intent.getIntExtra(EXTRA_HEARTBEAT_SECONDS_PER_SCO, 180)
                startStudying(cid, accuracy, fullMode, unitIndices, heartbeatEnabled, heartbeatSecondsPerSco)
            }
            ACTION_STOP -> stopStudyingAndSelf()
        }
        return START_STICKY
    }

    private fun startStudying(
        cid: String,
        accuracy: String,
        fullMode: Boolean = false,
        unitIndices: IntArray? = null,  // 2026-06-28
        heartbeatEnabled: Boolean = true,  // 2026-06-28:刷时长开关(默认开)
        heartbeatSecondsPerSco: Int = 180,  // 2026-06-28:每节时长(秒),分钟数 × 60
    ) {
        val app = applicationContext as AhuPlusApplication
        scope.launch(Dispatchers.IO) {
            try {
                // 1. 自动登录(若 cookie 失效)
                if (!app.weLearnAuthRepository.isLoggedIn()) {
                    val ok = app.weLearnAuthRepository.autoLoginIfPossible()
                    if (!ok) {
                        Log.w(tag, "未登录且无凭据,停服")
                        stopStudyingAndSelf(); return@launch
                    }
                }

                // 2. 拉课程树
                val treeRes = app.weLearnRepository.getCourseTree(cid)
                val tree = treeRes.getOrElse {
                    Log.w(tag, "拉课程树失败: ${it.message}")
                    stopStudyingAndSelf(); return@launch
                }

                // 3. 启动刷课(fullMode=true 时已完成的 sco 也重提交;unitIndices!=null 时只刷选中单元;heartbeatEnabled=true 时每节跑 N 秒心跳)
                app.weLearnStudyRepository.studyCourse(
                    tree = tree,
                    accuracyRange = parseAccuracy(accuracy),
                    fullMode = fullMode,
                    unitIndices = unitIndices,
                    heartbeatEnabled = heartbeatEnabled,
                    heartbeatSecondsPerSco = heartbeatSecondsPerSco,
                )
                Log.i(tag, "刷课完成(fullMode=$fullMode units=${unitIndices?.toList()} heartbeat=$heartbeatEnabled/${heartbeatSecondsPerSco}s), 停服")
                stopStudyingAndSelf()
            } catch (e: Exception) {
                Log.e(tag, "刷课异常", e)
                stopStudyingAndSelf()
            }
        }
    }

    private fun stopStudyingAndSelf() {
        runCatching { (applicationContext as AhuPlusApplication).weLearnStudyRepository.stop() }
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
                    "$base · 已刷 ${state.elapsedSec / 60}/${state.currentScoHeartbeatSec / 60} 分"
                } else base
            }
            state.isRunning -> "准备中…"
            else -> "已停止"
        }

        val openIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                putExtra("open_tab", "welearn")
            },
            PendingIntent.FLAG_IMMUTABLE,
        )
        val stopIntent = PendingIntent.getService(
            this, 1,
            Intent(this, WeLearnStudyService::class.java).apply { action = ACTION_STOP },
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

        const val ACTION_START = "com.yourname.ahu_plus.action.WELEARN_START"
        const val ACTION_STOP = "com.yourname.ahu_plus.action.WELEARN_STOP"

        const val EXTRA_CID = "cid"
        const val EXTRA_ACCURACY = "accuracy"  // "100" 或 "70,100"
        const val EXTRA_FULL_MODE = "full_mode"  // 2026-06-28:全量模式 — 已完成 sco 也重提交
        const val EXTRA_UNIT_INDICES = "unit_indices"  // 2026-06-28:选择性刷 — 选中单元 idx 数组
        // 2026-06-28:刷时长参数
        const val EXTRA_HEARTBEAT_ENABLED = "heartbeat_enabled"  // 默认 true
        const val EXTRA_HEARTBEAT_SECONDS_PER_SCO = "heartbeat_seconds_per_sco"  // 每节时长(秒),默认 180

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
            val intent = Intent(context, WeLearnStudyService::class.java).apply {
                action = ACTION_START
                putExtra(EXTRA_CID, cid)
                putExtra(EXTRA_ACCURACY, accuracy)
                putExtra(EXTRA_FULL_MODE, fullMode)
                if (unitIndices != null) putExtra(EXTRA_UNIT_INDICES, unitIndices)
                putExtra(EXTRA_HEARTBEAT_ENABLED, heartbeatEnabled)
                putExtra(EXTRA_HEARTBEAT_SECONDS_PER_SCO, heartbeatSecondsPerSco)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        /** UI 入口:停止 */
        fun stop(context: Context) {
            val intent = Intent(context, WeLearnStudyService::class.java).apply { action = ACTION_STOP }
            context.startService(intent)
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
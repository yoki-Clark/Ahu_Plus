package com.yourname.ahu_plus.util

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.RectF
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.util.Log
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import com.yourname.ahu_plus.MainActivity
import com.yourname.ahu_plus.data.model.CxStudyUiState

/**
 * 学习通后台学习环形进度悬浮窗 (2026-06-22)。
 *
 * 圆形、无文字、用环形边框表示进度。可拖动、点击回 App、双击关闭。
 */
class OverlayWindow(private val context: Context) {

    private val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private var circleView: CircleProgressView? = null
    private var layoutParams: WindowManager.LayoutParams? = null

    companion object {
        private const val TAG = "OverlayWindow"
        private const val SIZE_DP = 56

        fun hasOverlayPermission(context: Context): Boolean {
            return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M)
                Settings.canDrawOverlays(context) else true
        }
        fun openPermissionSettings(context: Context) {
            val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:${context.packageName}"))
                .apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
            runCatching { context.startActivity(intent) }
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    fun show(): Boolean {
        Log.e(TAG, "★★★ show(), perm=${hasOverlayPermission(context)} ★★★")
        if (circleView != null) return false
        if (!hasOverlayPermission(context)) return false

        val sizePx = dp(SIZE_DP)
        val view = CircleProgressView(context)
        view.setOnTouchListener(DragListener())
        view.setOnClickListener {
            val intent = Intent(context, MainActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            runCatching { context.startActivity(intent) }
        }

        layoutParams = WindowManager.LayoutParams(
            sizePx, sizePx,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            else WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = dp(16)
            y = dp(200)
        }

        return runCatching {
            windowManager.addView(view, layoutParams)
            circleView = view
            Log.e(TAG, "★★★ 环形悬浮窗 addView 成功 ★★★")
            true
        }.onFailure { e ->
            Log.e(TAG, "★★★ addView 失败: ${e.message} ★★★", e)
        }.getOrDefault(false)
    }

    fun update(state: CxStudyUiState) {
        val progress = if (state.totalTasks > 0) state.completedCount.toFloat() / state.totalTasks else 0f
        circleView?.setProgress(progress)
    }

    /** 通用进度重载(供 WeLearnStudyService 等非超星场景复用悬浮窗) */
    fun update(progress: Float) {
        circleView?.setProgress(progress.coerceIn(0f, 1f))
    }

    fun dismiss() {
        circleView?.let { runCatching { windowManager.removeView(it) } }
        circleView = null
    }

    private fun dp(value: Int): Int = (value * context.resources.displayMetrics.density + 0.5f).toInt()

    private inner class DragListener : View.OnTouchListener {
        private var startX = 0; private var startY = 0
        private var initialX = 0; private var initialY = 0
        private var moved = false
        private var lastClickTime = 0L

        @SuppressLint("ClickableViewAccessibility")
        override fun onTouch(v: View, event: MotionEvent): Boolean {
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    startX = event.rawX.toInt(); startY = event.rawY.toInt()
                    initialX = layoutParams?.x ?: 0; initialY = layoutParams?.y ?: 0
                    moved = false
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = event.rawX.toInt() - startX; val dy = event.rawY.toInt() - startY
                    if (!moved && (kotlin.math.abs(dx) > 8 || kotlin.math.abs(dy) > 8)) moved = true
                    if (moved && layoutParams != null) {
                        layoutParams?.x = initialX + dx; layoutParams?.y = initialY + dy
                        runCatching { windowManager.updateViewLayout(v, layoutParams) }
                    }
                }
                MotionEvent.ACTION_UP -> {
                    if (!moved) {
                        val now = System.currentTimeMillis()
                        if (now - lastClickTime < 300) { dismiss(); return true }
                        lastClickTime = now
                        v.performClick()
                    }
                }
            }
            return true
        }
    }
}

/**
 * 自定义环形进度 View：纯圆，无文字，环形边框从 0° 顺时针填充表示进度。
 */
@SuppressLint("ViewConstructor")
class CircleProgressView(context: Context) : View(context) {
    private var progress = 0f // 0..1
    private val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 3.5f * resources.displayMetrics.density
        color = Color.parseColor("#40FFFFFF")
    }
    private val fgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 3.5f * resources.displayMetrics.density
        strokeCap = Paint.Cap.ROUND
        color = Color.parseColor("#FF4FC3F7")
    }
    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.parseColor("#CC1565C0")
    }
    private val rect = RectF()

    fun setProgress(p: Float) {
        progress = p.coerceIn(0f, 1f)
        postInvalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val padding = 5f * resources.displayMetrics.density
        val size = width.coerceAtMost(height).toFloat()
        rect.set(padding, padding, size - padding, size - padding)

        // 实心圆背景
        canvas.drawOval(rect, fillPaint)

        // 背景环
        canvas.drawOval(rect, bgPaint)

        // 前景进度环 (从 -90° 即 12 点钟方向开始顺时针)
        val sweepAngle = progress * 360f
        canvas.drawArc(rect, -90f, sweepAngle, false, fgPaint)
    }
}

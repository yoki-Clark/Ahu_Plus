package com.ahu_plus.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/**
 * 顶部细进度条 - 2dp 品牌色 indeterminate 条,用于"有缓存后台刷新"场景(批次二项53)。
 *
 * 替代居中转圈:刷新不打断阅读,顶部一条细品牌色光带滑过(类 YouTube/Gmail)。
 * 仅在 `isRefreshing && hasCache` 时显示--无缓存的首屏加载仍用骨架/转圈。
 *
 * 用法(页面顶部 Box 内):
 * ```
 * if (isRefreshing && hasCache) {
 *     AhuLinearProgressIndicator(Modifier.align(Alignment.TopCenter))
 * }
 * ```
 *
 * 色取 `colorScheme.primary`(跟随用户主题色),手绘 Canvas 保证 2dp 净条无 Material 默认
 * 的 gap/stop 装饰。
 */
@Composable
fun AhuLinearProgressIndicator(
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.primary,
) {
    val transition = rememberInfiniteTransition(label = "linear-progress")
    val progress by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1100, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "linear-progress-x",
    )
    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(2.dp),
    ) {
        // 底轨
        drawRect(color = color.copy(alpha = 0.12f))
        // 移动光带:宽 40% 控件宽,从左外滑到右外,两端渐隐成柔光带
        val segWidth = size.width * 0.4f
        val travel = size.width + segWidth
        val start = progress * travel - segWidth
        drawRect(
            brush = Brush.horizontalGradient(
                colors = listOf(Color.Transparent, color, Color.Transparent),
                startX = start,
                endX = start + segWidth,
            ),
            topLeft = Offset(start, 0f),
            size = Size(segWidth, size.height),
        )
    }
}

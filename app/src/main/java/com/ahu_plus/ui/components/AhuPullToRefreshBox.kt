package com.ahu_plus.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.pulltorefresh.PullToRefreshState
import androidx.compose.material3.pulltorefresh.pullToRefresh
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.ahu_plus.ui.theme.AhuElevation
import com.ahu_plus.ui.theme.ahuShadow
import kotlin.math.roundToInt

private val AhuRefreshThreshold = 48.dp
private const val REFRESH_SPIN_MS = 900

/**
 * 统一下拉刷新容器,配合 [AhuRefreshIndicator] 品牌渐变指示器(批次二项28)。
 *
 * 替代 Material 默认的 `PullToRefreshDefaults.Indicator`,换成品牌渐变弧 + 旋转,
 * 全 App 13 处下拉刷新统一品牌识别。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AhuPullToRefreshBox(
    isRefreshing: Boolean,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier,
    threshold: Dp = AhuRefreshThreshold,
    content: @Composable BoxScope.() -> Unit,
) {
    val state = rememberPullToRefreshState()
    Box(
        modifier = modifier.pullToRefresh(
            isRefreshing = isRefreshing,
            state = state,
            threshold = threshold,
            onRefresh = onRefresh,
        ),
    ) {
        content()
        AhuRefreshIndicator(
            state = state,
            isRefreshing = isRefreshing,
            maxDistance = threshold,
            modifier = Modifier.align(Alignment.TopCenter),
        )
    }
}

/**
 * 品牌渐变下拉刷新指示器 - 圆形 surface 容器 + 品牌色渐变弧。
 *
 * - 拉动时:弧随拉动距离缩放显现(0->1)。
 * - 刷新时:弧持续旋转。
 * - 静止时:隐藏。
 *
 * 渐变色取 `colorScheme.primary`(跟随用户选的主题色,批次一项8),弧带"尾巴"渐隐,
 * 像品牌加载圈。垂直位置随 `distanceFraction` 下移,刷新时停在 threshold 处。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AhuRefreshIndicator(
    state: PullToRefreshState,
    isRefreshing: Boolean,
    maxDistance: Dp,
    modifier: Modifier = Modifier,
) {
    val fraction = state.distanceFraction.coerceIn(0f, 1f)
    val show = isRefreshing || fraction > 0f
    if (!show) return

    val indicatorSize = 38.dp
    val strokeWidth = 3.dp
    // 刷新时停在 threshold,拉动时随 fraction 下移。
    val offsetDp = if (isRefreshing) maxDistance else maxDistance * fraction
    val scale = if (isRefreshing) 1f else fraction

    val primary = MaterialTheme.colorScheme.primary
    val arcBrush = Brush.sweepGradient(
        listOf(primary, primary.copy(alpha = 0.15f)),
    )

    val rotation = if (isRefreshing) {
        val transition = rememberInfiniteTransition(label = "refresh-spin")
        transition.animateFloat(
            initialValue = 0f,
            targetValue = 360f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = REFRESH_SPIN_MS, easing = LinearEasing),
                repeatMode = RepeatMode.Restart,
            ),
            label = "refresh-rotation",
        ).value
    } else {
        0f
    }

    Box(
        modifier = modifier
            .offset { IntOffset(0, offsetDp.toPx().roundToInt()) }
            .size(indicatorSize)
            .scale(scale)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surface)
            .ahuShadow(AhuElevation.Medium, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(modifier = Modifier.size(indicatorSize)) {
            val sw = strokeWidth.toPx()
            val half = sw / 2f
            val arcSize = Size(size.width - sw, size.height - sw)
            // 拉动时弧长随 fraction 增长;刷新时画固定 300° 弧并旋转。
            val sweep = if (isRefreshing) 300f else 360f * fraction
            if (sweep > 0f) {
                drawArc(
                    brush = arcBrush,
                    startAngle = -90f + rotation,
                    sweepAngle = sweep,
                    useCenter = false,
                    topLeft = Offset(half, half),
                    size = arcSize,
                    style = Stroke(width = sw, cap = StrokeCap.Round),
                )
            }
        }
    }
}

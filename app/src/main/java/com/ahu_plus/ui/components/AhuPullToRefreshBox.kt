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
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshState
import androidx.compose.material3.pulltorefresh.pullToRefresh
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
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
import com.ahu_plus.data.local.RefreshIndicatorStyle
import com.ahu_plus.ui.theme.AhuElevation
import com.ahu_plus.ui.theme.ahuShadow
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.roundToInt

private val AhuRefreshThreshold = 48.dp
private const val REFRESH_SPIN_MS = 900
private const val REFRESH_DOTS_MS = 900
private const val REFRESH_ORBIT_MS = 1200
private const val REFRESH_PULSE_MS = 1200
private const val RING_COUNT = 3

// 指示器宿主固定尺寸:各样式在此框内居中,保证不同样式的垂直位置一致。
private val IndicatorHostSize = 44.dp
private val ChipSize = 38.dp

/**
 * 统一下拉刷新容器,配合 [AhuRefreshIndicator] 可切换品牌指示器(批次二项28 + 样式设置项)。
 *
 * 指示器样式由 [LocalRefreshIndicatorStyle] 决定(用户在「设置 -> 外观」选择,
 * MainActivity 根部注入),全 App 13 处下拉刷新统一品牌识别、统一切换。
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
    val style = LocalRefreshIndicatorStyle.current
    Box(
        modifier = modifier.pullToRefresh(
            isRefreshing = isRefreshing,
            state = state,
            threshold = threshold,
            onRefresh = onRefresh,
        ),
    ) {
        content()
        if (style == RefreshIndicatorStyle.SYSTEM_DEFAULT) {
            // Material 原生指示器:自管 offset / 显隐(按 state.distanceFraction + maxDistance),
            // 不走 [AhuRefreshIndicator] 宿主,避免双重位移。maxDistance 对齐 pullToRefresh threshold。
            PullToRefreshDefaults.Indicator(
                state = state,
                isRefreshing = isRefreshing,
                modifier = Modifier.align(Alignment.TopCenter),
                maxDistance = threshold,
            )
        } else {
            AhuRefreshIndicator(
                state = state,
                isRefreshing = isRefreshing,
                maxDistance = threshold,
                style = style,
                modifier = Modifier.align(Alignment.TopCenter),
            )
        }
    }
}

/**
 * 下拉刷新指示器宿主:统一处理显隐门控与垂直位移(拉动随 fraction 下移、刷新停 threshold),
 * 内部按 [style] 分发到具体动画样式。各样式自管 pull 阶段的显现反馈(scale / 半径 / 透明度)。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AhuRefreshIndicator(
    state: PullToRefreshState,
    isRefreshing: Boolean,
    maxDistance: Dp,
    style: RefreshIndicatorStyle,
    modifier: Modifier = Modifier,
) {
    val fraction = state.distanceFraction.coerceIn(0f, 1f)
    if (!isRefreshing && fraction <= 0f) return

    // 刷新时停在 threshold,拉动时随 fraction 下移。
    val offsetDp = if (isRefreshing) maxDistance else maxDistance * fraction

    Box(
        modifier = modifier
            .offset { IntOffset(0, offsetDp.toPx().roundToInt()) }
            .size(IndicatorHostSize),
        contentAlignment = Alignment.Center,
    ) {
        when (style) {
            // SYSTEM_DEFAULT 在 AhuPullToRefreshBox 走 PullToRefreshDefaults.Indicator,不经此宿主。
            RefreshIndicatorStyle.SYSTEM_DEFAULT -> Unit
            RefreshIndicatorStyle.GRADIENT_ARC -> GradientArcIndicator(fraction, isRefreshing)
            RefreshIndicatorStyle.BOUNCING_DOTS -> BouncingDotsIndicator(fraction, isRefreshing)
            RefreshIndicatorStyle.ORBIT -> OrbitIndicator(fraction, isRefreshing)
            RefreshIndicatorStyle.PULSE_RINGS -> PulseRingsIndicator(fraction, isRefreshing)
        }
    }
}

/**
 * 设置页用的刷新指示器预览:以刷新态(isRefreshing = true)渲染指定样式,供用户选择时直观对比。
 */
@Composable
fun RefreshIndicatorPreview(
    style: RefreshIndicatorStyle,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier.size(IndicatorHostSize),
        contentAlignment = Alignment.Center,
    ) {
        when (style) {
            RefreshIndicatorStyle.SYSTEM_DEFAULT -> SystemDefaultPreview()
            RefreshIndicatorStyle.GRADIENT_ARC -> GradientArcIndicator(fraction = 1f, isRefreshing = true)
            RefreshIndicatorStyle.BOUNCING_DOTS -> BouncingDotsIndicator(fraction = 1f, isRefreshing = true)
            RefreshIndicatorStyle.ORBIT -> OrbitIndicator(fraction = 1f, isRefreshing = true)
            RefreshIndicatorStyle.PULSE_RINGS -> PulseRingsIndicator(fraction = 1f, isRefreshing = true)
        }
    }
}

// ── 样式零:系统默认(Material 原生,仅用于设置页预览) ──────────

/**
 * 系统默认样式预览:圆形 surface 容器 + 品牌色 indeterminate 进度环。
 * 实际下拉刷新用 [PullToRefreshDefaults.Indicator](自管位移),此处仅复现其刷新态外观供设置页对比。
 */
@Composable
private fun SystemDefaultPreview() {
    val primary = MaterialTheme.colorScheme.primary
    Box(
        modifier = Modifier
            .size(ChipSize)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surface)
            .ahuShadow(AhuElevation.Medium, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        CircularProgressIndicator(
            color = primary,
            strokeWidth = 2.5.dp,
            modifier = Modifier.size(22.dp),
        )
    }
}

// ── 样式一:渐变弧(= 历史自定义实现) ──────────────────────────

/**
 * 圆形 surface 容器 + 品牌色 sweep 渐变弧。
 * - 拉动:弧随 fraction 缩放显现,弧长随 fraction 增长。
 * - 刷新:固定 300° 弧持续旋转。
 */
@Composable
private fun GradientArcIndicator(fraction: Float, isRefreshing: Boolean) {
    val primary = MaterialTheme.colorScheme.primary
    val surface = MaterialTheme.colorScheme.surface
    val strokeWidth = 3.dp
    val scale = if (isRefreshing) 1f else fraction

    val rotation = if (isRefreshing) {
        rememberInfiniteTransition(label = "refresh-spin").animateFloat(
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

    val arcBrush = Brush.sweepGradient(listOf(primary, primary.copy(alpha = 0.15f)))

    Box(
        modifier = Modifier
            .size(ChipSize)
            .scale(scale)
            .clip(CircleShape)
            .background(surface)
            .ahuShadow(AhuElevation.Medium, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(modifier = Modifier.size(ChipSize)) {
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

// ── 样式二:弹跳点 ─────────────────────────────────────────────

/**
 * 圆形 surface 容器 + 三点依次弹跳缩放。
 * - 拉动:三点以 fraction 静态淡入缩放。
 * - 刷新:三点按相位错开依次上跳,呼吸节奏。
 */
@Composable
private fun BouncingDotsIndicator(fraction: Float, isRefreshing: Boolean) {
    val primary = MaterialTheme.colorScheme.primary
    val surface = MaterialTheme.colorScheme.surface
    val dotRadius = 3.5.dp
    val step = 9.dp
    val scale = if (isRefreshing) 1f else fraction

    val phase = if (isRefreshing) {
        rememberInfiniteTransition(label = "dots").animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = REFRESH_DOTS_MS, easing = LinearEasing),
                repeatMode = RepeatMode.Restart,
            ),
            label = "dots-phase",
        ).value
    } else {
        0f
    }

    Box(
        modifier = Modifier
            .size(ChipSize)
            .scale(scale)
            .clip(CircleShape)
            .background(surface)
            .ahuShadow(AhuElevation.Medium, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(modifier = Modifier.size(ChipSize)) {
            val cx = size.width / 2f
            val cy = size.height / 2f
            val r = dotRadius.toPx()
            val s = step.toPx()
            val centers = listOf(cx - s, cx, cx + s)
            for (i in 0..2) {
                val p = if (isRefreshing) {
                    val raw = (phase - i / 3f) % 1f
                    if (raw < 0f) raw + 1f else raw
                } else {
                    0f
                }
                // 一次正弦跳跃:p∈[0,1) 在 0.5 处到顶,负半周截到 0。
                val amp = sin(p.toDouble() * PI).toFloat().coerceAtLeast(0f)
                val dotScale = if (isRefreshing) 0.6f + 0.4f * amp else fraction
                val yy = cy - amp * r * 0.9f
                val alpha = if (isRefreshing) (0.35f + 0.65f * amp).coerceIn(0f, 1f) else fraction
                drawCircle(
                    color = primary.copy(alpha = alpha.coerceIn(0f, 1f)),
                    radius = r * dotScale.coerceIn(0f, 1f),
                    center = Offset(centers[i], yy),
                )
            }
        }
    }
}

// ── 样式三:轨道行星 ───────────────────────────────────────────

/**
 * 圆形 surface 容器 + 中心点 + 卫星椭圆公转。
 * - 拉动:轨道半径随 fraction 展开,卫星停在右侧、轨道淡入。
 * - 刷新:卫星沿椭圆持续公转。
 */
@Composable
private fun OrbitIndicator(fraction: Float, isRefreshing: Boolean) {
    val primary = MaterialTheme.colorScheme.primary
    val surface = MaterialTheme.colorScheme.surface
    val reveal = if (isRefreshing) 1f else fraction

    val angle = if (isRefreshing) {
        rememberInfiniteTransition(label = "orbit").animateFloat(
            initialValue = 0f,
            targetValue = 360f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = REFRESH_ORBIT_MS, easing = LinearEasing),
                repeatMode = RepeatMode.Restart,
            ),
            label = "orbit-angle",
        ).value
    } else {
        0f
    }

    Box(
        modifier = Modifier
            .size(ChipSize)
            .scale(reveal)
            .clip(CircleShape)
            .background(surface)
            .ahuShadow(AhuElevation.Medium, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(modifier = Modifier.size(ChipSize)) {
            val cx = size.width / 2f
            val cy = size.height / 2f
            val maxA = size.width * 0.34f
            val maxB = size.height * 0.20f
            val a = maxA * reveal
            val b = maxB * reveal
            val rad = angle.toDouble() * PI / 180.0
            val ca = cos(rad).toFloat()
            val sa = sin(rad).toFloat()
            val sx = cx + a * ca
            val sy = cy + b * sa
            // 轨道椭圆(淡)
            drawOval(
                color = primary.copy(alpha = 0.18f * reveal),
                topLeft = Offset(cx - a, cy - b),
                size = Size(2f * a, 2f * b),
                style = Stroke(width = 1.dp.toPx()),
            )
            // 中心星
            drawCircle(color = primary, radius = 3.dp.toPx(), center = Offset(cx, cy))
            // 卫星
            drawCircle(color = primary, radius = 3.5.dp.toPx(), center = Offset(sx, sy))
        }
    }
}

// ── 样式四:脉冲环 ─────────────────────────────────────────────

/**
 * 同心圆向外脉冲扩散(声呐感),无 surface 容器,环即主体。
 * - 拉动:一圈随 fraction 向外生长 + 中心点淡入。
 * - 刷新:[RING_COUNT] 圈按相位错开连续向外扩散并淡出。
 */
@Composable
private fun PulseRingsIndicator(fraction: Float, isRefreshing: Boolean) {
    val primary = MaterialTheme.colorScheme.primary

    val t = if (isRefreshing) {
        rememberInfiniteTransition(label = "pulse").animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = REFRESH_PULSE_MS, easing = LinearEasing),
                repeatMode = RepeatMode.Restart,
            ),
            label = "pulse-t",
        ).value
    } else {
        0f
    }

    Box(
        modifier = Modifier.size(IndicatorHostSize),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(modifier = Modifier.size(IndicatorHostSize)) {
            val cx = size.width / 2f
            val cy = size.height / 2f
            val minR = 3.dp.toPx()
            val maxR = size.width * 0.46f
            val sw = 2.dp.toPx()

            if (isRefreshing) {
                for (i in 0 until RING_COUNT) {
                    val raw = (t - i / RING_COUNT.toFloat()) % 1f
                    val p = if (raw < 0f) raw + 1f else raw
                    val r = minR + (maxR - minR) * p
                    val alpha = ((1f - p) * 0.7f).coerceIn(0f, 1f)
                    drawCircle(
                        color = primary.copy(alpha = alpha),
                        radius = r,
                        center = Offset(cx, cy),
                        style = Stroke(width = sw),
                    )
                }
            } else {
                // 拉动:一圈随 fraction 生长。
                val r = minR + (maxR - minR) * fraction
                drawCircle(
                    color = primary.copy(alpha = (0.7f * fraction).coerceIn(0f, 1f)),
                    radius = r,
                    center = Offset(cx, cy),
                    style = Stroke(width = sw),
                )
            }
            // 中心点
            drawCircle(
                color = primary.copy(alpha = if (isRefreshing) 1f else fraction.coerceIn(0f, 1f)),
                radius = minR,
                center = Offset(cx, cy),
            )
        }
    }
}

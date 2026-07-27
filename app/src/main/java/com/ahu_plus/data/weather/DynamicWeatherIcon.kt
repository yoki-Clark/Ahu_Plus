package com.ahu_plus.data.weather

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.rotate
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * 动态天气图标(批次四·44)。
 *
 * 首批支持晴/多云/雨/雪 4 类高频天气(决策 D5),其余码回退到 [WeatherCode.icon] 静态图标。
 * 全部用 [Canvas] + [rememberInfiniteTransition] 自绘,避免多层 Composable 叠加造成卡顿;
 * 仅在 CurrentWeatherCard / WeatherPanel 等少量位置使用,不进滚动列表。
 *
 * 配色:晴天太阳固定暖金色(对齐 M3 太阳直觉),云/雨/雪 用 [tint] 跟随主题内容色,
 * 保证深浅色下均可见。"动态"主要由运动(自转/飘动/下落/旋转)而非高饱和色表达。
 *
 * @param code WMO 天气码
 * @param tint 云/雨/雪 主色;传入内容色以适配主题(默认中性蓝灰,避免未指定时不可见)
 * @param modifier 尺寸由调用方用 size/width+height 约束
 */
@Composable
fun DynamicWeatherIcon(
    code: Int,
    modifier: Modifier = Modifier,
    tint: Color = Color.Unspecified,
) {
    val color = if (tint == Color.Unspecified) Color(0xFF607D8B) else tint
    val kind = weatherKind(code)

    // 未覆盖的码回退静态图标,保持信息一致(文案仍由 WeatherCode.describe 提供)。
    if (kind == WeatherKind.OTHER) {
        Icon(
            imageVector = WeatherCode.icon(code),
            contentDescription = null,
            tint = color,
            modifier = modifier,
        )
        return
    }

    val transition = rememberInfiniteTransition(label = "weather-icon")
    // 晴:太阳缓慢自转 + 光晕呼吸
    val rotation by transition.animateFloat(
        initialValue = 0f, targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(24_000, easing = LinearEasing)),
        label = "sun-rotation",
    )
    val pulse by transition.animateFloat(
        initialValue = 0f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1_400, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "sun-pulse",
    )
    // 多云:云朵水平飘动
    val drift by transition.animateFloat(
        initialValue = 0f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(4_500, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "cloud-drift",
    )
    // 雨:雨线下落(0->1 重启循环)
    val rainPhase by transition.animateFloat(
        initialValue = 0f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(900, easing = LinearEasing)),
        label = "rain-phase",
    )
    // 雪:雪花下落 + 自旋
    val snowPhase by transition.animateFloat(
        initialValue = 0f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(3_000, easing = LinearEasing)),
        label = "snow-phase",
    )
    val spin by transition.animateFloat(
        initialValue = 0f, targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(8_000, easing = LinearEasing)),
        label = "snow-spin",
    )

    Canvas(modifier = modifier) {
        when (kind) {
            WeatherKind.SUNNY -> drawSunny(rotation, pulse)
            WeatherKind.CLOUDY -> drawCloudy(drift, color)
            WeatherKind.RAIN -> drawRain(rainPhase, color)
            WeatherKind.SNOW -> drawSnow(snowPhase, spin, color)
            WeatherKind.OTHER -> Unit
        }
    }
}

private enum class WeatherKind { SUNNY, CLOUDY, RAIN, SNOW, OTHER }

/** 把 WMO 码归并为首批支持的 4 类动画;雷暴暂归雨(D5:其余后续补)。 */
private fun weatherKind(code: Int): WeatherKind = when (code) {
    0 -> WeatherKind.SUNNY
    1, 2, 3, 45, 48 -> WeatherKind.CLOUDY
    51, 53, 55, 56, 57, 61, 63, 65, 66, 67, 80, 81, 82, 95, 96, 99 -> WeatherKind.RAIN
    71, 73, 75, 77, 85, 86 -> WeatherKind.SNOW
    else -> WeatherKind.OTHER
}

private val SunCoreColor = Color(0xFFFFB300)
private val SunGlowColor = Color(0xFFFFC107)

/** 晴:光晕呼吸 + 8 道光线缓慢自转 + 实心日盘。 */
private fun DrawScope.drawSunny(rotation: Float, pulse: Float) {
    val c = center
    val s = minOf(size.width, size.height)
    val sunR = s * 0.18f
    // 光晕(半径与透明度随 pulse 呼吸)
    val glowR = sunR * (1.7f + 0.35f * pulse)
    drawCircle(
        color = SunGlowColor.copy(alpha = 0.16f + 0.14f * pulse),
        radius = glowR,
        center = c,
    )
    // 光线
    val rayInner = sunR * 1.5f
    val rayOuter = sunR * (1.5f + 0.30f)
    val rayCount = 8
    rotate(degrees = rotation, pivot = c) {
        for (i in 0 until rayCount) {
            val a = (2.0 * PI / rayCount) * i
            val dx = cos(a).toFloat()
            val dy = sin(a).toFloat()
            drawLine(
                color = SunCoreColor,
                start = Offset(c.x + dx * rayInner, c.y + dy * rayInner),
                end = Offset(c.x + dx * rayOuter, c.y + dy * rayOuter),
                strokeWidth = s * 0.035f,
                cap = StrokeCap.Round,
            )
        }
    }
    // 日盘
    drawCircle(SunCoreColor, radius = sunR, center = c)
}

/** 多云:单朵云水平飘动(drift 0->1->0 来回)。 */
private fun DrawScope.drawCloudy(drift: Float, tint: Color) {
    val s = minOf(size.width, size.height)
    val sway = (drift - 0.5f) * s * 0.10f
    drawCloudCluster(center.x + sway, center.y, s * 0.20f, tint)
}

/** 雨:顶部云 + 4 条错相下落的雨线。 */
private fun DrawScope.drawRain(rainPhase: Float, tint: Color) {
    val s = minOf(size.width, size.height)
    drawCloudCluster(center.x, s * 0.30f, s * 0.14f, tint)
    val topY = s * 0.50f
    val fallDist = s * 0.45f
    val dropLen = s * 0.12f
    val dropXs = floatArrayOf(-0.18f, -0.06f, 0.06f, 0.18f)
    val count = dropXs.size
    for (i in 0 until count) {
        // 各雨线相位错开,避免整齐同步下落
        val phase = (rainPhase + i.toFloat() / count) % 1f
        val y = topY + phase * fallDist
        val x = center.x + dropXs[i] * s
        drawLine(
            color = tint,
            start = Offset(x, y),
            end = Offset(x, y + dropLen),
            strokeWidth = s * 0.025f,
            cap = StrokeCap.Round,
        )
    }
}

/** 雪:顶部云 + 3 朵错相下落并自旋的六瓣雪花。 */
private fun DrawScope.drawSnow(snowPhase: Float, spin: Float, tint: Color) {
    val s = minOf(size.width, size.height)
    drawCloudCluster(center.x, s * 0.30f, s * 0.14f, tint)
    val topY = s * 0.50f
    val fallDist = s * 0.42f
    val flakeXs = floatArrayOf(-0.12f, 0.05f, 0.16f)
    val count = flakeXs.size
    for (i in 0 until count) {
        val phase = (snowPhase + i.toFloat() / count) % 1f
        val y = topY + phase * fallDist
        val x = center.x + flakeXs[i] * s
        drawSnowflake(Offset(x, y), s * 0.065f, spin + i * 35f, tint)
    }
}

/** 六瓣雪花:3 条过圆心直线(夹角 60°)合成 6 臂。 */
private fun DrawScope.drawSnowflake(center: Offset, r: Float, rotation: Float, color: Color) {
    rotate(degrees = rotation, pivot = center) {
        for (i in 0 until 3) {
            val a = (PI.toFloat() / 3f) * i
            val dx = cos(a) * r
            val dy = sin(a) * r
            drawLine(
                color = color,
                start = Offset(center.x - dx, center.y - dy),
                end = Offset(center.x + dx, center.y + dy),
                strokeWidth = r * 0.20f,
                cap = StrokeCap.Round,
            )
        }
    }
}

/** 云朵簇:几个重叠圆构成的云形(雨/雪/多云共用)。 */
private fun DrawScope.drawCloudCluster(cx: Float, cy: Float, r: Float, color: Color) {
    drawCircle(color, r, Offset(cx - r * 0.9f, cy + r * 0.20f))
    drawCircle(color, r * 1.2f, Offset(cx, cy - r * 0.30f))
    drawCircle(color, r * 0.9f, Offset(cx + r * 0.9f, cy + r * 0.25f))
    drawCircle(color, r * 0.7f, Offset(cx, cy + r * 0.35f))
}

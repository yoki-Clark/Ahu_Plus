package com.ahu_plus.ui.theme

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.SpringSpec
import androidx.compose.animation.core.TweenSpec
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.ui.graphics.Color

/**
 * 动效 token - 统一全 App 弹性(spring)与时长(tween)参数。
 *
 * 背景:批次一盘点发现全 App 动效参数分散--仅 1 处 `spring`(TodayFloatingButton),
 * 散落 `tween` 时长 140/260/420/2000ms 且 easing 不统一。本对象集中托管,新动效一律引用
 * 此处,逐步替换散落硬编码(批次二项26)。
 *
 * Material Expressive 的 `MotionScheme` 在 BOM 2026.02.01 下为 internal 不可用,本 token
 * 是其手写替代--用几档语义化 `SpringSpec` 表达"按压/常规/柔和/灵敏"四种动效气质。
 *
 * 用法:
 * ```
 * val scale by animateFloatAsState(targetValue = if (pressed) 0.98f else 1f, animationSpec = AhuMotion.PressSpring)
 * animateColorAsState(targetValue = color, animationSpec = AhuMotion.ColorSpring)
 * ```
 */
object AhuMotion {

    // ── Spring 预设(Float 动画:缩放 / 位移 / 进度) ──

    /** 按压回弹 - 轻微过冲后归位,卡片/按钮按下用。蓝本来自 TodayFloatingButton 的 FAB 缩放。 */
    val PressSpring: SpringSpec<Float> = spring(
        dampingRatio = Spring.DampingRatioMediumBouncy,
        stiffness = Spring.StiffnessMedium,
    )

    /** 常规 - 无过冲,状态切换 / 通用平滑。 */
    val StandardSpring: SpringSpec<Float> = spring(
        dampingRatio = Spring.DampingRatioNoBouncy,
        stiffness = Spring.StiffnessMedium,
    )

    /** 柔和进场 - 轻微过冲,列表项进场 / 进度丝滑推进用。 */
    val GentleSpring: SpringSpec<Float> = spring(
        dampingRatio = Spring.DampingRatioLowBouncy,
        stiffness = Spring.StiffnessLow,
    )

    /** 灵敏 - 无过冲快速响应,指示器滑动用。 */
    val SnappySpring: SpringSpec<Float> = spring(
        dampingRatio = Spring.DampingRatioNoBouncy,
        stiffness = Spring.StiffnessHigh,
    )

    // ── Spring 预设(Color 动画) ──

    /** 颜色平滑过渡 - 倒计时颜色随紧急度渐变等用。无过冲避免颜色越界闪烁。 */
    val ColorSpring: SpringSpec<Color> = spring(
        dampingRatio = Spring.DampingRatioNoBouncy,
        stiffness = Spring.StiffnessMedium,
    )

    // ── Tween 时长(收口散落 140/260/420ms 硬编码) ──
    // 默认 FastOutSlowInEasing(标准 Material 缓动);需要线性等特殊 easing 的调用方自行指定。

    /** 短 - 140ms,微交互收尾(抖动收回等)。 */
    val ShortTween: TweenSpec<Float> = tween(durationMillis = 140, easing = FastOutSlowInEasing)

    /** 中 - 260ms,进场 / 切换默认。 */
    val MediumTween: TweenSpec<Float> = tween(durationMillis = 260, easing = FastOutSlowInEasing)

    /** 长 - 420ms,大位移 / 复杂过渡。 */
    val LongTween: TweenSpec<Float> = tween(durationMillis = 420, easing = FastOutSlowInEasing)
}

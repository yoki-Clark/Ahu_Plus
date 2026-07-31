package com.ahu_plus.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import com.ahu_plus.ui.theme.AhuMotion

/**
 * 按压缩放反馈修饰符 - 按下时缩到 [pressedScale],松手 spring 回弹(批次二项17)。
 *
 * 替代不可用的 Material Expressive MotionScheme 按压反馈,统一全 App 卡片/按钮手感。
 *
 * 需要与可点击组件共享同一个 [InteractionSource] 才能感知按压态:
 * ```
 * val interactionSource = remember { MutableInteractionSource() }
 * Modifier
 *     .clickable(interactionSource = interactionSource, indication = ripple(), onClick = onClick)
 *     .pressableScale(interactionSource)
 * ```
 *
 * 用 [AhuMotion.PressSpring] 驱动,带轻微过冲,像按软按键。
 */
@Composable
fun Modifier.pressableScale(
    interactionSource: InteractionSource,
    pressedScale: Float = 0.98f,
): Modifier {
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) pressedScale else 1f,
        animationSpec = AhuMotion.PressSpring,
        label = "pressable-scale",
    )
    return this.scale(scale)
}

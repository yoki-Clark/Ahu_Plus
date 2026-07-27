package com.ahu_plus.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.luminance
import androidx.compose.foundation.border
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * 三档卡片层次 token（承接 docs/13 卡片三档层次约定，用更明确的纵深梯度）。
 *
 * - [Low]    普通信息卡，轻投影，与内容分离感弱
 * - [Medium] 中间层（列表焦点项、设置卡、AppHub 磁贴）
 * - [High]   Hero 焦点卡（渐变 Hero），强投影拉开层次
 *
 * 配合 [ahuShadow] 使用。Material 的 [shadow] 本身已是「环境光漫射 + 关键方向投影」双阴影
 * 实现，这里只做三档语义化分层；深色主题下阴影不可见，[ahuShadow] 自动降低 elevation 避免脏糊。
 */
object AhuElevation {
    val Low: Dp = 1.dp
    val Medium: Dp = 3.dp
    val High: Dp = 6.dp
}

/**
 * 语义化阴影修饰符：按 [AhuElevation] 三档投射，并按当前主题明暗自动衰减。
 *
 * 深色主题（background luminance < 0.5）下阴影本就不可见，elevation 降至 30% 避免在
 * 深色 surface 上糊出脏边。同时 [clip] 到 [shape] 保证圆角内容不溢出阴影边界。
 *
 * 用法：`Modifier.ahuShadow(AhuElevation.Medium, AhuShapes.Card)`
 */
@Composable
fun Modifier.ahuShadow(
    elevation: Dp,
    shape: Shape,
    clip: Boolean = true,
): Modifier {
    val isDark = MaterialTheme.colorScheme.surface.luminance() < 0.5f
    val effective = if (isDark) (elevation.value * 0.3f).dp else elevation
    return this
        .shadow(elevation = effective, shape = shape, clip = false)
        .then(if (clip) Modifier.clip(shape) else Modifier)
}

/**
 * 渐变描边修饰符：沿 [shape] 轮廓画一道 [brush] 渐变描边，用于强调卡片的"光晕勾勒"
 * （批次一项16）。如成绩选中柱、应用中心选中磁贴、课表当前节。
 *
 * 委托 [border] 的 brush 重载，原生支持任意 Shape 圆角描边。
 *
 * 用法：`Modifier.gradientBorder(AhuGradient.Blue.brush, 2.dp, AhuShapes.Card)`
 */
fun Modifier.gradientBorder(
    brush: Brush,
    width: Dp,
    shape: Shape = RectangleShape,
): Modifier = this.border(width = width, brush = brush, shape = shape)

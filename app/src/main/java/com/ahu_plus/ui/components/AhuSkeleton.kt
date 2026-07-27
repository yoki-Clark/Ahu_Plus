package com.ahu_plus.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.ahu_plus.ui.theme.AhuShapes
import com.ahu_plus.ui.theme.AhuSpacing

/**
 * 骨架屏 - 加载时显示灰色占位结构 + shimmer 微光扫动,替代孤零零的转圈(批次二项24)。
 *
 * 体感"内容在成形",比 CircularProgressIndicator 更稳更快。用 [rememberInfiniteTransition]
 * + 移动高光渐变实现 shimmer,纯 Compose 动画,零依赖。
 *
 * 用法:
 * ```
 * if (uiState.isLoading && data.isEmpty()) { AhuSkeletonCard() }
 * ```
 */
@Composable
fun AhuSkeletonLine(
    modifier: Modifier = Modifier,
    height: Dp = 14.dp,
    cornerRadius: Dp = 7.dp,
) {
    Box(
        modifier = modifier
            .height(height)
            .clip(RoundedCornerShape(cornerRadius))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .ahuShimmer(),
    )
}

/**
 * 骨架卡片 - 模拟"图标 + 双行文本"列表行的占位轮廓,首屏加载时铺几张。
 *
 * @param lineCount 主区灰条数量(不含标题行)。
 */
@Composable
fun AhuSkeletonCard(
    modifier: Modifier = Modifier,
    lineCount: Int = 2,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(AhuShapes.Card)
            .background(MaterialTheme.colorScheme.surface)
            .padding(AhuSpacing.Card),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            // 标题行(宽)
            AhuSkeletonLine(modifier = Modifier.fillMaxWidth(0.7f), height = 16.dp)
            // 副行(窄)
            AhuSkeletonLine(modifier = Modifier.fillMaxWidth(0.4f), height = 12.dp)
            repeat((lineCount - 1).coerceAtLeast(0)) {
                Spacer(modifier = Modifier.height(2.dp))
                AhuSkeletonLine(modifier = Modifier.fillMaxWidth(0.85f))
            }
        }
    }
}

/**
 * 骨架列表 - 首屏加载时铺一组骨架卡片,模拟列表"正在成形"。
 */
@Composable
fun AhuSkeletonList(
    modifier: Modifier = Modifier,
    itemCount: Int = 4,
) {
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(AhuSpacing.CardGap)) {
        repeat(itemCount) {
            AhuSkeletonCard()
        }
    }
}

/**
 * Shimmer 微光扫动修饰符 - 在底色上叠一道从左向右移动的高光渐变。
 *
 * 底色取 surfaceVariant,高光取 surface(深浅主题下都更亮),宽度为控件 1.5 倍循环扫过。
 */
fun Modifier.ahuShimmer(
    durationMillis: Int = 1300,
): Modifier = composed {
    val transition = rememberInfiniteTransition(label = "shimmer")
    val progress by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = durationMillis, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "shimmer-progress",
    )
    val base = MaterialTheme.colorScheme.surfaceVariant
    val highlight = MaterialTheme.colorScheme.surface
    drawWithCache {
        val width = size.width
        // 高光带宽度 = 控件宽,从左外 1 倍宽扫到右外 1 倍宽
        val sweep = width * 1f
        val start = -sweep + progress * (width + 2 * sweep)
        val brush = Brush.linearGradient(
            colors = listOf(Color.Transparent, highlight, Color.Transparent),
            start = Offset(start, 0f),
            end = Offset(start + sweep, size.height),
        )
        onDrawBehind {
            drawRect(color = base)
            drawRect(brush = brush)
        }
    }
}

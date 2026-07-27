package com.ahu_plus.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Login
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.filled.School
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

import com.ahu_plus.ui.theme.AhuPlusTheme
import com.ahu_plus.ui.theme.AhuShapes
import com.ahu_plus.ui.theme.AhuSpacing
import com.ahu_plus.ui.theme.AhuElevation
import com.ahu_plus.ui.theme.ahuShadow

@Composable
fun AhuTopAppBar(
    title: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    navigationIcon: (@Composable () -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {}
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.background,
        shadowElevation = 1.dp
    ) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .height(56.dp)
                    .padding(horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (navigationIcon != null) {
                    Box(
                        modifier = Modifier.size(48.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        navigationIcon()
                    }
                } else {
                    Box(modifier = Modifier.size(4.dp))
                }
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .padding(start = if (navigationIcon == null) 12.dp else 0.dp),
                    contentAlignment = Alignment.CenterStart
                ) {
                    title()
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    content = actions
                )
            }
        }
    }
}

@Composable
fun AhuCard(
    modifier: Modifier = Modifier,
    containerColor: Color = MaterialTheme.colorScheme.surface,
    content: @Composable () -> Unit
) {
    Card(
        shape = AhuShapes.Card,
        colors = CardDefaults.cardColors(containerColor = containerColor),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        modifier = modifier
            .fillMaxWidth()
            .ahuShadow(AhuElevation.Low, AhuShapes.Card)
    ) {
        content()
    }
}

@Composable
fun AhuIconBox(
    imageVector: ImageVector,
    tint: Color,
    modifier: Modifier = Modifier,
    size: Dp = 42.dp,
    contentDescription: String? = null
) {
    Box(
        modifier = modifier
            .size(size)
            .clip(AhuShapes.IconBox)
            .background(tint.copy(alpha = 0.14f)),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = imageVector,
            contentDescription = contentDescription,
            tint = tint,
            modifier = Modifier.size(size * 0.52f)
        )
    }
}

@Composable
fun AhuSectionTitle(
    text: String,
    modifier: Modifier = Modifier,
    trailing: (@Composable () -> Unit)? = null
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.weight(1f)
        )
        trailing?.invoke()
    }
}

@Composable
fun AhuSectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    trailing: (@Composable () -> Unit)? = null
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 2.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            if (!subtitle.isNullOrBlank()) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        trailing?.invoke()
    }
}

@Composable
fun AhuStatusCard(
    text: String,
    modifier: Modifier = Modifier,
    tone: Color = MaterialTheme.colorScheme.primary,
    loading: Boolean = false,
    actionText: String? = null,
    onAction: (() -> Unit)? = null
) {
    Card(
        shape = AhuShapes.Card,
        colors = CardDefaults.cardColors(containerColor = tone.copy(alpha = 0.10f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        modifier = modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (loading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(18.dp),
                    strokeWidth = 2.dp,
                    color = tone
                )
                Spacer(modifier = Modifier.width(10.dp))
            }
            Text(
                text = text,
                style = MaterialTheme.typography.bodyMedium,
                color = tone,
                modifier = Modifier.weight(1f)
            )
            if (actionText != null && onAction != null) {
                TextButton(
                    onClick = onAction,
                    colors = ButtonDefaults.textButtonColors(contentColor = tone)
                ) {
                    Text(actionText)
                }
            }
        }
    }
}

@Composable
fun AhuTag(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.primary
) {
    Surface(
        modifier = modifier,
        shape = AhuShapes.Pill,
        color = color.copy(alpha = 0.10f),
        contentColor = color
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
fun AhuInfoRow(
    label: String,
    value: String,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            modifier = Modifier.weight(0.36f),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            text = value,
            modifier = Modifier
                .weight(0.64f)
                .padding(start = 12.dp),
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

/**
 * 统一横向列表行 - 图标盒 + 主副标题 + 尾部槽，收口之前 GradeRow/NoticeRow/SettingsRow
 * 各自实现的"图标+文字+尾部"卡片行（批次一项73）。
 *
 * - [icon] + [iconTint]：可选前置图标盒（40dp，AhuShapes.IconBox，tint 0.14 底），统一之前
 *   34dp 裸图标 / 38dp / 42dp 三种写法。
 * - [title] / [subtitle]：主副标题，副标题空则不占位。
 * - [trailing]：尾部槽（箭头/数值/Switch 等）。
 * - [accentColor]：可选左侧 4dp 色条（分类标识，批次一项21 复用）。
 * - [onClick]：非 null 则整行 clickable。
 */
@Composable
fun AhuListRow(
    title: String,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    iconTint: Color = MaterialTheme.colorScheme.primary,
    subtitle: String? = null,
    accentColor: Color? = null,
    onClick: (() -> Unit)? = null,
    trailing: @Composable (RowScope.() -> Unit)? = null,
) {
    Card(
        shape = AhuShapes.Card,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        modifier = modifier
            .fillMaxWidth()
            .ahuShadow(AhuElevation.Low, AhuShapes.Card)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (accentColor != null) {
                Box(
                    modifier = Modifier
                        .size(width = 4.dp, height = 28.dp)
                        .clip(AhuShapes.Pill)
                        .background(accentColor)
                        .padding(end = 10.dp),
                )
                Spacer(modifier = Modifier.width(10.dp))
            }
            if (icon != null) {
                AhuIconBox(
                    imageVector = icon,
                    tint = iconTint,
                    size = 40.dp,
                    modifier = Modifier.padding(end = 12.dp),
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                if (!subtitle.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            if (trailing != null) {
                Spacer(modifier = Modifier.width(8.dp))
                trailing()
            }
        }
    }
}

@Composable
fun AhuMetricStrip(
    metrics: List<Pair<String, String>>,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        metrics.forEach { (label, value) ->
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = value,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.ExtraBold
                )
                Text(
                    text = label,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
fun ProvideIconColor(
    color: Color,
    content: @Composable () -> Unit
) {
    CompositionLocalProvider(androidx.compose.material3.LocalContentColor provides color) {
        content()
    }
}

/**
 * 渐变背景的 Hero 卡片 — 用于首页今日课程、余额等核心信息展示。
 * 内容区文字颜色自动设为白色。
 */
@Composable
fun AhuHeroCard(
    gradient: Brush,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    Card(
        shape = AhuShapes.HeroCard,
        colors = CardDefaults.cardColors(containerColor = Color.Transparent),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        modifier = modifier
            .fillMaxWidth()
            .ahuShadow(AhuElevation.High, AhuShapes.HeroCard)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(gradient)
        ) {
            // 渐变底色较深,统一提供白色内容色:内部 Text/Icon 未显式指定 color 时自动取白,
            // 避免浅色主题下默认 onSurface 深色字在渐变上不可见。
            CompositionLocalProvider(LocalContentColor provides Color.White) {
                content()
            }
        }
    }
}

/**
 * 统一空状态组件 — 图标 + 标题 + 可选描述 + 可选操作按钮。
 *
 * @param centered 是否居中铺满父容器(占位屏状态)。true 时外层套 fillMaxSize Box。
 */
@Composable
fun AhuEmptyState(
    icon: ImageVector,
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    actionText: String? = null,
    onAction: (() -> Unit)? = null,
    centered: Boolean = false,
) {
    val content: @Composable () -> Unit = {
        Column(
            modifier = modifier
                .fillMaxWidth()
                .padding(vertical = 32.dp, horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(AhuSpacing.sm)
        ) {
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(AhuShapes.IconBox)
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    modifier = Modifier.size(28.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                )
            }
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center
            )
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
            }
            if (actionText != null && onAction != null) {
                Spacer(modifier = Modifier.height(AhuSpacing.xs))
                TextButton(onClick = onAction) {
                    Text(actionText)
                }
            }
        }
    }
    if (centered) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { content() }
    } else {
        content()
    }
}

/**
 * 统一错误状态组件 — 红色警告图标 + 错误信息 + 重试按钮。
 *
 * @param centered 是否居中铺满父容器(占位屏状态)。true 时外层套 fillMaxSize Box。
 */
@Composable
fun AhuErrorState(
    message: String,
    modifier: Modifier = Modifier,
    onRetry: (() -> Unit)? = null,
    actionLabel: String = "重试",
    centered: Boolean = false,
) {
    val content: @Composable () -> Unit = {
        Column(
            modifier = modifier
                .fillMaxWidth()
                .padding(vertical = 32.dp, horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(AhuSpacing.sm)
        ) {
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(AhuShapes.IconBox)
                    .background(MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Filled.Refresh,
                    contentDescription = null,
                    modifier = Modifier.size(28.dp),
                    tint = MaterialTheme.colorScheme.error.copy(alpha = 0.7f)
                )
            }
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
                textAlign = TextAlign.Center
            )
            if (onRetry != null) {
                Spacer(modifier = Modifier.height(AhuSpacing.xs))
                TextButton(onClick = onRetry) {
                    Text(actionLabel)
                }
            }
        }
    }
    if (centered) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { content() }
    } else {
        content()
    }
}

/**
 * 居中加载指示器。用于页面初始加载状态。
 */
@Composable
fun CenteredLoader(modifier: Modifier = Modifier) {
    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator()
    }
}

/**
 * 居中错误提示 + 重试按钮。统一委托 [AhuErrorState](centered=true),保证两套样式一致。
 */
@Composable
fun CenteredError(
    message: String,
    onRetry: () -> Unit,
    actionLabel: String = "重试",
    modifier: Modifier = Modifier
) {
    AhuErrorState(
        message = message,
        onRetry = onRetry,
        actionLabel = actionLabel,
        modifier = modifier,
        centered = true,
    )
}

/** 在可匿名浏览的页面内提示统一身份认证，不触发自动跳转。 */
@Composable
fun LoginRequiredCard(
    onLogin: () -> Unit,
    modifier: Modifier = Modifier,
    title: String = "登录后同步校园数据",
    description: String = "课表、成绩、校园卡等账户数据需要统一身份认证",
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = AhuShapes.Card,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.45f)
        ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = AhuSpacing.md, vertical = AhuSpacing.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.Login,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
            )
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = AhuSpacing.sm),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                Text(
                    description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            TextButton(onClick = onLogin) {
                Text("去登录")
            }
        }
    }
}

/**
 * 居中文字提示。用于空数据等中间状态。
 */
@Composable
fun CenteredMessage(text: String, modifier: Modifier = Modifier) {
    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Preview(name = "Ahu Icon Box", showBackground = true)
@Composable
private fun PreviewAhuIconBox() {
    AhuPlusTheme {
        AhuIconBox(
            imageVector = Icons.Filled.School,
            tint = Color(0xFF2196F3),
            modifier = Modifier.padding(16.dp)
        )
    }
}

@Preview(name = "Ahu Tag", showBackground = true)
@Composable
private fun PreviewAhuTag() {
    AhuPlusTheme {
        Row(modifier = Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            AhuTag(text = "必修", color = Color(0xFF4CAF50))
            AhuTag(text = "选修", color = Color(0xFFFF9800))
        }
    }
}

@Preview(name = "Ahu Status Card - Loading", showBackground = true)
@Composable
private fun PreviewAhuStatusCardLoading() {
    AhuPlusTheme {
        AhuStatusCard(
            text = "正在刷新课程数据...",
            loading = true,
            modifier = Modifier.padding(16.dp)
        )
    }
}

@Preview(name = "Ahu Status Card - Action", showBackground = true)
@Composable
private fun PreviewAhuStatusCardAction() {
    AhuPlusTheme {
        AhuStatusCard(
            text = "认证已过期",
            tone = MaterialTheme.colorScheme.error,
            actionText = "重新登录",
            onAction = {},
            modifier = Modifier.padding(16.dp)
        )
    }
}

@Preview(name = "Ahu Empty State", showBackground = true)
@Composable
private fun PreviewAhuEmptyState() {
    AhuPlusTheme {
        AhuEmptyState(
            icon = Icons.Filled.School,
            title = "暂无课程",
            subtitle = "当前学期还未录入课表",
            actionText = "刷新",
            onAction = {},
            centered = false,
            modifier = Modifier.padding(16.dp)
        )
    }
}

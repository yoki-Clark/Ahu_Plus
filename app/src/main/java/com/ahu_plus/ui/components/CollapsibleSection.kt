package com.ahu_plus.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/**
 * 通用可折叠 section。
 *
 * 头部始终停留在所在位置的顶部 (类似 sticky header)。
 * 内容过长时限制最大高度并在内部滚动 —— 保证头部不会随长内容滚出屏外。
 *
 * **手风琴模式**：传入 [expanded] / [onToggle] 可实现同一时间只展开一个。
 *
 * @param expanded 外部控制展开状态 (null = 内部自管理)
 * @param onToggle 点击头部的回调 (仅 expanded 不为 null 时使用, 内部传 !expanded)
 */
@Composable
fun CollapsibleSection(
    title: String,
    modifier: Modifier = Modifier,
    defaultExpanded: Boolean = false,
    badge: String? = null,
    maxContentHeight: androidx.compose.ui.unit.Dp = 360.dp,
    expanded: Boolean? = null,
    onToggle: ((Boolean) -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    // 外部未控制 → 内部自管理
    var internalExpanded by rememberSaveable { mutableStateOf(defaultExpanded) }
    val isExpanded = expanded ?: internalExpanded

    fun toggle() {
        if (onToggle != null) onToggle(!isExpanded)
        else internalExpanded = !isExpanded
    }

    Column(modifier = modifier.fillMaxWidth()) {
        // 头部 (点击切换)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { toggle() }
                .padding(vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f),
            )
            if (!badge.isNullOrBlank()) {
                Surface(
                    shape = MaterialTheme.shapes.small,
                    color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.65f),
                ) {
                    Text(
                        text = badge,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                    )
                }
            }
            Icon(
                imageVector = if (isExpanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                contentDescription = if (isExpanded) "收起" else "展开",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        // 内容区 (展开时) —— 限制最大高度, 超出内部滚动, 头部始终可见
        AnimatedVisibility(
            visible = isExpanded,
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut(),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp, bottom = 12.dp)
                    .heightIn(max = maxContentHeight)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                content()
            }
        }
    }
}

package com.ahu_plus.ui.screen.home

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.ahu_plus.data.home.AppRegistry
import com.ahu_plus.data.home.AppSpec
import com.ahu_plus.ui.theme.AhuShapes

/**
 * 首页"我的收藏"选择面板。
 *
 * 设计要点:
 *  - 实时同步:每次勾选/取消立即调 onConfirm(newList) 通知上层,
 *    不再依赖底部"保存"按钮(原 bug:用户点 + 后关掉 picker,"保存"按钮没触发或被遗漏,
 *    DataStore 未写入)。关掉 sheet 视为"完成选择",无需额外确认。
 *  - 严格 6 项上限:已达 6 个时未选项 Checkbox enabled=false,视觉 disabled。
 *  - Grid 卡片预览:每个 AppSpec 用 icon+title 卡片展示,与 Dashboard 收藏栏视觉一致,
 *    用户选的时候直接看到'加进去长什么样',所见即所得。
 *
 * 2026-07-06 v3:Grid 卡片版(原 checkbox 列表 + group header)。
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun FavoritesPickerSheet(
    favoriteIds: List<String>,
    onConfirm: (List<String>) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    // 用 List 保留用户依次勾选的顺序(而非 Set),即点即写,UI 反馈立即生效。
    var pending by remember { mutableStateOf(favoriteIds) }
    val maxFavorites = 6

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
        ) {
            // Header:总数计数 + 右上"完成"按钮
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp, bottom = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "选择常用应用 (${pending.size}/$maxFavorites)",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = onDismiss) { Text("完成") }
            }

            // 按分组渲染:header + FlowRow 卡片网格
            AppRegistry.grouped().forEach { (group, specs) ->
                Text(
                    text = group,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(top = 8.dp, bottom = 8.dp),
                )
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    specs.forEach { spec ->
                        val checked = spec.key in pending
                        val disabled = !checked && pending.size >= maxFavorites
                        AppSpecTile(
                            spec = spec,
                            checked = checked,
                            disabled = disabled,
                            onToggle = {
                                if (!disabled) {
                                    val next = if (checked) pending - spec.key else pending + spec.key
                                    pending = next
                                    onConfirm(next)
                                }
                            },
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

/**
 * Picker 内单个 app 卡片。
 *
 * 视觉规范:
 *  - 普通:surface 背景 + IconBox 图标 + 标题
 *  - 选中:2dp 主色边框 + 右上角对勾标记 + 浅主色背景
 *  - 禁用:整体 0.38 alpha + 不响应点击(由调用方控制)
 */
@Composable
private fun AppSpecTile(
    spec: AppSpec,
    checked: Boolean,
    disabled: Boolean,
    onToggle: () -> Unit,
) {
    val alpha = if (disabled) 0.38f else 1f
    val border = if (checked) {
        BorderStroke(2.dp, MaterialTheme.colorScheme.primary)
    } else {
        BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    }
    val containerColor = if (checked) {
        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
    } else {
        MaterialTheme.colorScheme.surface
    }

    Surface(
        modifier = Modifier
            .width(96.dp)
            .clip(AhuShapes.Card)
            .clickable(enabled = !disabled, onClick = onToggle),
        shape = AhuShapes.Card,
        color = containerColor.copy(alpha = containerColor.alpha * alpha),
        border = border,
        tonalElevation = if (checked) 1.dp else 0.dp,
    ) {
        Box(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 12.dp, horizontal = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(AhuShapes.IconBox)
                        .background(spec.tint.copy(alpha = 0.14f * alpha)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = spec.icon,
                        contentDescription = null,
                        tint = spec.tint.copy(alpha = alpha),
                        modifier = Modifier.size(22.dp),
                    )
                }
                Text(
                    text = spec.title,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = alpha),
                    maxLines = 1,
                )
            }
            if (checked) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(4.dp)
                        .size(18.dp)
                        .clip(androidx.compose.foundation.shape.CircleShape)
                        .background(MaterialTheme.colorScheme.primary),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Filled.Check,
                        contentDescription = "已选",
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(12.dp),
                    )
                }
            }
        }
    }
}
package com.ahu_plus.ui.screen.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.AddAPhoto
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ahu_plus.data.local.AvatarMode
import com.ahu_plus.ui.theme.AhuShapes

/**
 * 头像来源选择面板。
 *
 * 三选一:默认头像 / 校园真实相片 / 自定义头像。
 * - 选中项用整行淡色背景标识(不用右侧勾,避免与按钮重合)。
 * - 真实相片:点击行即设为头像;选中时右侧出现"查看大图"按钮(查看/下载/刷新)。
 * - 自定义头像:已有自定义头像时点击行恢复上次头像;右侧"更换"按钮重新选图裁剪。
 *   切换模式不清自定义头像文件,仅退登清(见 [com.ahu_plus.data.repository.AvatarStore])。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AvatarPickerSheet(
    currentMode: AvatarMode,
    customAvatarReady: Boolean,
    onSelectDefault: () -> Unit,
    onSelectReal: () -> Unit,
    onSelectCustom: () -> Unit,
    onPickCustom: () -> Unit,
    onOpenRealViewer: () -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(vertical = 8.dp),
        ) {
            Text(
                text = "更换头像",
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
            )
            AvatarOptionRow(
                title = "默认头像",
                description = "使用应用默认图标",
                icon = Icons.Filled.Person,
                selected = currentMode == AvatarMode.DEFAULT,
                onClick = { onSelectDefault() },
            )
            HorizontalDivider()
            AvatarOptionRow(
                title = "校园真实相片",
                description = "点击设为头像",
                icon = Icons.Filled.AccountCircle,
                selected = currentMode == AvatarMode.REAL,
                onClick = { onSelectReal() },
                trailing = {
                    if (currentMode == AvatarMode.REAL) {
                        IconButton(onClick = { onDismiss(); onOpenRealViewer() }) {
                            Icon(
                                Icons.Filled.Image,
                                contentDescription = "查看大图",
                                tint = MaterialTheme.colorScheme.primary,
                            )
                        }
                    }
                },
            )
            HorizontalDivider()
            AvatarOptionRow(
                title = "自定义头像",
                description = if (customAvatarReady) "点击恢复上次自定义头像" else "从相册选择并裁剪",
                icon = Icons.Filled.AddAPhoto,
                selected = currentMode == AvatarMode.CUSTOM,
                onClick = {
                    if (customAvatarReady) {
                        onSelectCustom()
                    } else {
                        onDismiss(); onPickCustom()
                    }
                },
                trailing = {
                    if (currentMode == AvatarMode.CUSTOM) {
                        IconButton(onClick = { onDismiss(); onPickCustom() }) {
                            Icon(
                                Icons.Filled.AddAPhoto,
                                contentDescription = "更换头像",
                                tint = MaterialTheme.colorScheme.primary,
                            )
                        }
                    }
                },
            )
            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
private fun AvatarOptionRow(
    title: String,
    description: String,
    icon: ImageVector,
    selected: Boolean,
    onClick: () -> Unit,
    trailing: @Composable (() -> Unit)? = null,
) {
    // 选中项整行淡色背景 + 标题加粗,替代右侧勾选标记。
    val rowBg = if (selected) {
        MaterialTheme.colorScheme.primary.copy(alpha = 0.08f)
    } else {
        Color.Transparent
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(rowBg)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(AhuShapes.IconBox)
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        }
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(start = 12.dp),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
            )
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        trailing?.invoke()
    }
}

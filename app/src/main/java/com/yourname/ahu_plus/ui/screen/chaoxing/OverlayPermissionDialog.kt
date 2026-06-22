package com.yourname.ahu_plus.ui.screen.chaoxing

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.yourname.ahu_plus.util.OverlayWindow

/**
 * 学习通后台学习悬浮窗权限引导对话框 (2026-06-22 新增)。
 *
 * 在用户点击"开始学习"但未授予 SYSTEM_ALERT_WINDOW 时弹出。
 * 用户可选择"去开启"跳转系统设置,或"跳过"仅用通知模式。
 */
@Composable
fun OverlayPermissionDialog(
    onDismiss: () -> Unit,
    onGranted: () -> Unit,
    onSkipped: () -> Unit,
) {
    val context = LocalContext.current
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "开启悬浮窗权限?",
                fontWeight = FontWeight.Bold,
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "开启后,学习通刷课将在桌面显示一个小型悬浮窗,实时显示进度。",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text(
                    text = "不开启也可正常学习,仅在通知栏显示进度(无悬浮窗)。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = "路径: 系统设置 → 应用 → 安大 Plus → 显示在其他应用上层",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                OverlayWindow.openPermissionSettings(context)
                onGranted()
            }) {
                Text("去开启")
            }
        },
        dismissButton = {
            TextButton(onClick = {
                onSkipped()
            }) {
                Text("跳过,只用通知")
            }
        },
    )
}

package com.yourname.ahu_plus.ui.components

import android.util.Log
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.yourname.ahu_plus.data.model.UpdateInfo

/**
 * 应用更新弹窗。
 *
 * 初始状态 → 两个按钮：立即更新 / 关闭（可选"忽略此次版本更新"复选框）
 * 下载中状态 → 进度条 + 百分比数字
 */
@Composable
fun UpdateDialog(
    info: UpdateInfo,
    downloading: Boolean = false,
    downloadProgress: Int = 0,
    onUpdate: () -> Unit,
    onLater: () -> Unit,
    onIgnore: () -> Unit,
    onDismiss: () -> Unit
) {
    Log.d("UpdateDialog", "rendering: downloading=$downloading progress=$downloadProgress")
    var ignoreThisVersion by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = {
            if (!downloading) {
                if (ignoreThisVersion) onIgnore() else onDismiss()
            }
        },
        title = {
            Text(
                text = if (downloading) "正在下载 ${info.latestVersion}" else "发现新版本 ${info.latestVersion}",
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            if (downloading) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    LinearProgressIndicator(
                        progress = { downloadProgress / 100f },
                        modifier = Modifier.fillMaxWidth().height(8.dp),
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "$downloadProgress%",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "下载完成后将自动安装",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            } else {
                val notes = info.releaseNotesText()
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = notes.ifBlank { "新版本已发布，建议立即更新以获得更好的体验。" },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Checkbox(
                            checked = ignoreThisVersion,
                            onCheckedChange = { ignoreThisVersion = it }
                        )
                        Text(
                            text = "忽略此次版本更新",
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            }
        },
        confirmButton = {
            if (downloading) {
                Button(
                    onClick = {},
                    enabled = false,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("下载中…")
                }
            } else {
                Button(
                    onClick = {
                        Log.d("UpdateDialog", "立即更新 clicked")
                        onUpdate()
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("立即更新")
                }
            }
        },
        dismissButton = {
            if (!downloading) {
                TextButton(onClick = {
                    if (ignoreThisVersion) {
                        Log.d("UpdateDialog", "关闭 (ignore=true)")
                        onIgnore()
                    } else {
                        Log.d("UpdateDialog", "关闭 (ignore=false)")
                        onLater()
                    }
                }) {
                    Text("关闭", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    )
}

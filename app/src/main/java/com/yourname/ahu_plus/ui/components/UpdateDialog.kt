package com.yourname.ahu_plus.ui.components

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
import com.yourname.ahu_plus.data.update.UpdateUiState

/**
 * 自动更新弹窗。直接消费 [UpdateUiState],按状态切换 4 种形态:
 *  - UpdateReady     → 更新日志 + 立即更新 / 关闭(可勾选忽略)
 *  - Downloading     → 进度条 + 字节数 + 取消(强制更新时禁用取消)
 *  - DownloadFailed  → 错误信息 + 重试 / 关闭
 *  - ReadyToInstall  → 提示已下载完成 + 安装 / 取消
 *
 * 强制更新场景: 隐藏关闭按钮、不可取消下载、AlertDialog 不响应 onDismissRequest。
 */
@Composable
fun UpdateDialog(
    state: UpdateUiState,
    onUpdate: (UpdateInfo, Boolean) -> Unit,
    onCancelDownload: () -> Unit,
    onRetryInstall: () -> Unit,
    onIgnore: () -> Unit,
    onDismiss: () -> Unit
) {
    if (state is UpdateUiState.Idle) return

    val info: UpdateInfo = when (state) {
        is UpdateUiState.UpdateReady -> state.info
        is UpdateUiState.Downloading -> state.info
        is UpdateUiState.DownloadFailed -> state.info
        is UpdateUiState.ReadyToInstall -> state.info
        UpdateUiState.Idle -> return
    }
    val forceUpdate: Boolean = when (state) {
        is UpdateUiState.UpdateReady -> state.forceUpdate
        is UpdateUiState.Downloading -> state.forceUpdate
        is UpdateUiState.DownloadFailed -> state.forceUpdate
        is UpdateUiState.ReadyToInstall -> state.forceUpdate
        UpdateUiState.Idle -> false
    }

    var ignoreThisVersion by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = {
            if (forceUpdate) return@AlertDialog
            when (state) {
                is UpdateUiState.Downloading -> Unit
                is UpdateUiState.UpdateReady,
                is UpdateUiState.DownloadFailed,
                is UpdateUiState.ReadyToInstall -> {
                    if (ignoreThisVersion && state is UpdateUiState.UpdateReady) onIgnore() else onDismiss()
                }
                UpdateUiState.Idle -> Unit
            }
        },
        title = {
            Text(
                text = dialogTitle(state, info),
                fontWeight = FontWeight.Bold
            )
        },
        text = { DialogBody(state, info, forceUpdate, ignoreThisVersion) { ignoreThisVersion = it } },
        confirmButton = {
            DialogConfirmButton(
                state = state,
                forceUpdate = forceUpdate,
                onUpdate = { onUpdate(info, forceUpdate) },
                onRetryInstall = onRetryInstall
            )
        },
        dismissButton = {
            DialogDismissButton(
                state = state,
                forceUpdate = forceUpdate,
                ignoreThisVersion = ignoreThisVersion,
                onCancelDownload = onCancelDownload,
                onIgnore = onIgnore,
                onDismiss = onDismiss
            )
        }
    )
}

private fun dialogTitle(state: UpdateUiState, info: UpdateInfo): String = when (state) {
    is UpdateUiState.UpdateReady ->
        if (state.forceUpdate) "需要更新到 ${info.latestVersion}" else "发现新版本 ${info.latestVersion}"
    is UpdateUiState.Downloading -> "正在下载 ${info.latestVersion}"
    is UpdateUiState.DownloadFailed -> "下载失败"
    is UpdateUiState.ReadyToInstall -> "已下载完成"
    UpdateUiState.Idle -> ""
}

@Composable
private fun DialogBody(
    state: UpdateUiState,
    info: UpdateInfo,
    forceUpdate: Boolean,
    ignoreThisVersion: Boolean,
    onIgnoreChange: (Boolean) -> Unit
) {
    when (state) {
        is UpdateUiState.UpdateReady -> {
            Column(modifier = Modifier.fillMaxWidth()) {
                if (forceUpdate) {
                    Text(
                        text = "当前版本过旧,必须更新后才能继续使用。",
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                }
                val notes = info.releaseNotesText()
                Text(
                    text = notes.ifBlank { "新版本已发布,建议立即更新以获得更好的体验。" },
                    modifier = Modifier.fillMaxWidth()
                )
                if (!forceUpdate) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Checkbox(
                            checked = ignoreThisVersion,
                            onCheckedChange = onIgnoreChange
                        )
                        Text(
                            text = "忽略此次版本更新",
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            }
        }
        is UpdateUiState.Downloading -> {
            Column(modifier = Modifier.fillMaxWidth()) {
                if (state.progress >= 0) {
                    LinearProgressIndicator(
                        progress = { state.progress / 100f },
                        modifier = Modifier.fillMaxWidth().height(8.dp)
                    )
                } else {
                    LinearProgressIndicator(
                        modifier = Modifier.fillMaxWidth().height(8.dp)
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = if (state.progress >= 0) "${state.progress}%" else "正在连接…",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.fillMaxWidth()
                )
                if (state.totalBytes > 0) {
                    Text(
                        text = "${formatBytes(state.downloadedBytes)} / ${formatBytes(state.totalBytes)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "下载完成后将自动安装",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        is UpdateUiState.DownloadFailed -> {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = state.message,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "请检查网络后重试。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        is UpdateUiState.ReadyToInstall -> {
            Text(
                text = "${info.latestVersion} 已下载完成。如果系统未弹出安装界面,请点击 \"安装\" 重新触发,并在系统提示中授予安装权限。",
                modifier = Modifier.fillMaxWidth()
            )
        }
        UpdateUiState.Idle -> Unit
    }
}

@Composable
private fun DialogConfirmButton(
    state: UpdateUiState,
    forceUpdate: Boolean,
    onUpdate: () -> Unit,
    onRetryInstall: () -> Unit
) {
    when (state) {
        is UpdateUiState.UpdateReady -> {
            Button(onClick = onUpdate, modifier = Modifier.fillMaxWidth()) {
                Text(if (forceUpdate) "立即更新" else "立即更新")
            }
        }
        is UpdateUiState.Downloading -> {
            Button(onClick = {}, enabled = false, modifier = Modifier.fillMaxWidth()) {
                Text("下载中…")
            }
        }
        is UpdateUiState.DownloadFailed -> {
            Button(onClick = onUpdate, modifier = Modifier.fillMaxWidth()) {
                Text("重试")
            }
        }
        is UpdateUiState.ReadyToInstall -> {
            Button(onClick = onRetryInstall, modifier = Modifier.fillMaxWidth()) {
                Text("安装")
            }
        }
        UpdateUiState.Idle -> Unit
    }
}

@Composable
private fun DialogDismissButton(
    state: UpdateUiState,
    forceUpdate: Boolean,
    ignoreThisVersion: Boolean,
    onCancelDownload: () -> Unit,
    onIgnore: () -> Unit,
    onDismiss: () -> Unit
) {
    when (state) {
        is UpdateUiState.UpdateReady -> {
            if (forceUpdate) return
            TextButton(onClick = {
                if (ignoreThisVersion) onIgnore() else onDismiss()
            }) {
                Text("关闭", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        is UpdateUiState.Downloading -> {
            if (forceUpdate) return
            TextButton(onClick = onCancelDownload) {
                Text("取消", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        is UpdateUiState.DownloadFailed -> {
            if (forceUpdate) return
            TextButton(onClick = onDismiss) {
                Text("关闭", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        is UpdateUiState.ReadyToInstall -> {
            if (forceUpdate) return
            TextButton(onClick = onDismiss) {
                Text("稍后", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        UpdateUiState.Idle -> Unit
    }
}

private fun formatBytes(bytes: Long): String = when {
    bytes >= 1024 * 1024 -> "%.1f MB".format(bytes / 1024.0 / 1024.0)
    bytes >= 1024 -> "%.1f KB".format(bytes / 1024.0)
    else -> "$bytes B"
}

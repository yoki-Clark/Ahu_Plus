package com.ahu_plus.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
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
import com.ahu_plus.data.model.Announcement

/**
 * 开发者公告弹窗。直接消费当前 [Announcement](null 时不显示)。
 *
 * 形态:
 *  - 标题 + 可滚动正文
 *  - 有 actionUrl 时显示动作按钮(打开外链,并视为已读关闭)
 *  - dismissible 时显示"不再提示"勾选框 + 可点遮罩关闭;
 *    不可忽略(紧急)公告隐藏勾选框且遮罩不响应,只能点"我知道了"
 *
 * @param onDismiss     关闭(参数: 是否勾选了"不再提示")
 * @param onAction      点击动作按钮(参数: 要打开的 url)
 */
@Composable
fun AnnouncementDialog(
    announcement: Announcement?,
    onDismiss: (dontShowAgain: Boolean) -> Unit,
    onAction: (url: String) -> Unit
) {
    if (announcement == null) return

    var dontShowAgain by remember(announcement.id) { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = {
            // 不可忽略公告:遮罩点击不关闭,强制阅读
            if (announcement.dismissible) onDismiss(dontShowAgain)
        },
        title = {
            Text(
                text = announcement.title.ifBlank { "公告" },
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = announcement.content,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 320.dp)
                        .verticalScroll(rememberScrollState())
                )
                if (announcement.dismissible) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Checkbox(
                            checked = dontShowAgain,
                            onCheckedChange = { dontShowAgain = it }
                        )
                        Text(
                            text = "不再提示",
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            }
        },
        confirmButton = {
            if (announcement.hasAction()) {
                Button(
                    onClick = {
                        onAction(announcement.actionUrl)
                        onDismiss(dontShowAgain)
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(announcement.actionLabel)
                }
            } else {
                Button(
                    onClick = { onDismiss(dontShowAgain) },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("我知道了")
                }
            }
        },
        dismissButton = if (announcement.hasAction()) {
            {
                TextButton(onClick = { onDismiss(dontShowAgain) }) {
                    Text("我知道了", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        } else null
    )
}

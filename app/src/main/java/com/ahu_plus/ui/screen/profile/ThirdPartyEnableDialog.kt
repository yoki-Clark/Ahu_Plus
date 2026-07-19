package com.ahu_plus.ui.screen.profile

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/**
 * 「启用第三方服务」风险声明确认弹窗。
 *
 * - 列出本次将启用的三个第三方服务 (校园集市 / 超星学习通 / WeLearn)
 * - 明示平台边界、账号风险和按服务单独配置的原则
 */
@Composable
fun ThirdPartyEnableDialog(
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                imageVector = Icons.Filled.WarningAmber,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error
            )
        },
        title = { Text("启用第三方服务") },
        text = {
            Column {
                Text(
                    "您即将启用以下第三方服务：",
                    style = MaterialTheme.typography.bodyMedium
                )
                Spacer(Modifier.height(8.dp))
                Text("• 校园集市（api.zxs-bbs.cn）", fontWeight = FontWeight.Medium)
                Text(
                    "  浏览校园二手交易与服务帖子",
                    style = MaterialTheme.typography.bodySmall
                )
                Spacer(Modifier.height(4.dp))
                Text("• 超星学习通（chaoxing.com）", fontWeight = FontWeight.Medium)
                Text(
                    "  课程作业查看与自动学习引擎",
                    style = MaterialTheme.typography.bodySmall
                )
                Spacer(Modifier.height(4.dp))
                Text("• WeLearn 随行课堂（welearn.sflep.com）", fontWeight = FontWeight.Medium)
                Text(
                    "  外语课程学习、作业查看与学习进度同步",
                    style = MaterialTheme.typography.bodySmall
                )
                Spacer(Modifier.height(16.dp))
                HorizontalDivider()
                Spacer(Modifier.height(12.dp))
                Text(
                    "使用边界",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.error
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    "上述平台使用独立账号、身份或会话，并受各自规则约束。" +
                        "总开关开启后仍需分别启用和登录具体服务；自动学习、交易信息和第三方内容应在对应平台复核。" +
                        "应用不会以本说明免除依法应承担的个人信息保护和软件安全责任。",
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
            ) {
                Text("继续启用")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        }
    )
}

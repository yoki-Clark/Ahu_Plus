package com.yourname.ahu_plus.ui.screen.profile

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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

/**
 * 「启用第三方服务」风险声明确认弹窗。
 *
 * - 列出本次将启用的两个第三方服务 (校园集市 / 超星学习通)
 * - 明示风险:账号封禁、信息真伪争议、交易纠纷、数据泄露等均由用户承担
 * - 确认按钮带 5 秒倒计时,倒计时归零前 disabled,
 *   强制用户停留 5 秒阅读风险声明,这是设计意图而非 bug
 */
@Composable
fun ThirdPartyEnableDialog(
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    // 5 秒倒计时:倒计时期间确认按钮 disabled,文案显示剩余秒数,
    // 强制让用户停留足够长的时间阅读风险声明
    var secondsLeft by remember { mutableIntStateOf(5) }
    LaunchedEffect(Unit) {
        while (secondsLeft > 0) {
            delay(1000)
            secondsLeft--
        }
    }

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
                Spacer(Modifier.height(16.dp))
                HorizontalDivider()
                Spacer(Modifier.height(12.dp))
                Text(
                    "⚠ 风险声明",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.error
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    "上述两个平台均非安徽大学官方系统。" +
                        "本应用仅提供技术接入，与第三方平台无任何合作关系。" +
                        "使用过程中可能产生的账号封禁、信息真伪争议、交易纠纷、" +
                        "数据泄露等一切后果均由您本人承担，与应用开发者无关。",
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                enabled = secondsLeft == 0
            ) {
                Text(
                    if (secondsLeft > 0)
                        "请阅读 ($secondsLeft)"
                    else
                        "我已了解并承担风险"
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        }
    )
}

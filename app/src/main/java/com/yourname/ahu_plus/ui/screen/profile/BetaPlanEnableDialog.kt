package com.yourname.ahu_plus.ui.screen.profile

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Science
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.yourname.ahu_plus.util.QqGroupOpener

/** 内测群号常量。点击跳 mqqapi 深链,失败回退到剪贴板 + Toast。 */
private const val BETA_QQ_GROUP_UIN = "1039158498"

/**
 * 加入内测计划确认弹窗。仿 ThirdPartyEnableDialog 的 AlertDialog 风格,无 5s 倒计时。
 *
 * - step 1:说明 + 「参加内测计划 / 不参加」两按钮
 * - step 2:感谢 + 可点击的群号(跳 QQ) + 「完成」按钮
 *
 * 父级契约:
 * - onDecline:用户拒绝(step 1 「不参加」按钮 / step 1 外部点击)→ 父级回退开关 OFF
 * - onClose:用户确认完成(step 2 「完成」按钮 / step 2 外部点击)→ 父级只关闭弹窗,开关保持 ON
 */
@Composable
fun BetaPlanEnableDialog(
    onDecline: () -> Unit,
    onClose: () -> Unit
) {
    var step by remember { mutableIntStateOf(1) }
    val isStep1 = step == 1

    AlertDialog(
        onDismissRequest = if (isStep1) onDecline else onClose,
        icon = {
            Icon(
                imageVector = if (isStep1) Icons.Filled.Science else Icons.Filled.Group,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )
        },
        title = {
            Text(
                if (isStep1) "加入内测计划" else "感谢您的信任和支持",
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            if (isStep1) {
                Step1Text()
            } else {
                Step2GroupText()
            }
        },
        confirmButton = {
            if (isStep1) {
                Button(onClick = { step = 2 }) {
                    Text("参加内测计划", fontWeight = FontWeight.Medium)
                }
            } else {
                Button(onClick = onClose) {
                    Text("完成", fontWeight = FontWeight.Medium)
                }
            }
        },
        dismissButton = {
            // step 1 显示「不参加」,step 2 隐藏(只有「完成」一个出口)
            if (isStep1) {
                TextButton(onClick = onDecline) {
                    Text("不参加")
                }
            }
        }
    )
}

@Composable
private fun Step1Text() {
    Column {
        Text(
            "打开此功能后,会加入此软件的内测计划,会给你推送一些正在测试中的功能更新。" +
                "这些版本不一定稳定,可能会有一些 bug。",
            style = MaterialTheme.typography.bodyMedium
        )
        Spacer(Modifier.height(8.dp))
        Text(
            "如果你愿意相信开发者,可以参加此计划;如果你不愿意,可以不参加。" +
                "软件默认是不参加内测的,不参加只会在此功能成熟之后,才会收到更新信息," +
                "不会影响你的正常使用。",
            style = MaterialTheme.typography.bodyMedium
        )
    }
}

@Composable
private fun Step2GroupText() {
    val context = LocalContext.current
    Column {
        Text(
            "请加内测群,我们会在此群通知最新的内测情况。",
            style = MaterialTheme.typography.bodyMedium
        )
        Spacer(Modifier.height(12.dp))
        // 群号可点击: 优先 mqqapi 深链,失败复制 + Toast
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clickable {
                    val opened = QqGroupOpener.open(context, BETA_QQ_GROUP_UIN)
                    if (!opened) {
                        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE)
                            as ClipboardManager
                        cm.setPrimaryClip(ClipData.newPlainText("QQ群号", BETA_QQ_GROUP_UIN))
                        Toast.makeText(
                            context,
                            "未安装手机 QQ,群号已复制到剪贴板",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
                .padding(vertical = 8.dp)
        ) {
            Column {
                Text(
                    "内测 QQ 群号",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    BETA_QQ_GROUP_UIN,
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    "点击自动跳转 QQ 加群",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

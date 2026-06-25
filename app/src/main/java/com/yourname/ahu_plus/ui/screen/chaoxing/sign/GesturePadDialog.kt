package com.yourname.ahu_plus.ui.screen.chaoxing.sign

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/**
 * 九宫格手势绘制弹窗(2026-06-24)。
 *
 * 独立弹窗承载 [GesturePad],解决设置页内联画板"清除后图案残留"的问题:
 * 「清除」通过递增 [resetKey] 让 GesturePad 整体重建(remember 状态重置),
 * 内部 path 真正清空。绘制完成且合法(4-9 个不重复点)才允许保存。
 */
@Composable
fun GesturePadDialog(
    initial: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var current by remember { mutableStateOf("") }
    var resetKey by remember { mutableIntStateOf(0) }
    val valid = current.length in 4..9 && current.all { it in '1'..'9' } && current.toSet().size == current.length

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("绘制手势", fontWeight = FontWeight.Bold) },
        text = {
            Column {
                Text(
                    "在九宫格上滑动绘制手势(与老师设定一致),抬手后可保存。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(8.dp))
                // key(resetKey) 变化时 GesturePad 重建 → 内部 path 清空
                key(resetKey) {
                    GesturePad(onPathChange = { current = it })
                }
                Text(
                    if (current.isBlank()) "尚未绘制" else "当前:$current",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (valid || current.isBlank()) MaterialTheme.colorScheme.onSurfaceVariant
                    else MaterialTheme.colorScheme.error,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(current) }, enabled = valid) { Text("保存") }
        },
        dismissButton = {
            TextButton(onClick = { current = ""; resetKey++ }) { Text("清除") }
        },
    )
}

package com.yourname.ahu_plus.ui.screen.schedule.components

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.compose.runtime.DisposableEffect
import com.yourname.ahu_plus.notification.ReminderPermissions
import com.yourname.ahu_plus.ui.theme.AhuShapes

/**
 * 课程提醒权限引导卡片。
 *
 * 仅当 [enabled](课程提醒已开启)且通知/精确闹钟权限缺失时,在课表页顶部展示。
 * 提醒关闭时不打扰(用户没开提醒就无所谓权限)。
 *
 * 用户从系统设置返回时(ON_RESUME)自动复查权限,授权完成后卡片消失。
 * "暂不"仅在本次进入页面期间隐藏(dismissed),不持久化。
 */
@Composable
fun ReminderPermissionBanner(enabled: Boolean, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    var notificationGranted by remember { mutableStateOf(ReminderPermissions.hasNotificationPermission(context)) }
    var exactAlarmGranted by remember { mutableStateOf(ReminderPermissions.canScheduleExactAlarms(context)) }
    var dismissed by remember { mutableStateOf(false) }

    val notificationLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) { granted ->
        notificationGranted = granted
    }

    // 从系统设置返回时复查两项权限
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                notificationGranted = ReminderPermissions.hasNotificationPermission(context)
                exactAlarmGranted = ReminderPermissions.canScheduleExactAlarms(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val visible = enabled && !dismissed && (!notificationGranted || !exactAlarmGranted)

    AnimatedVisibility(visible = visible) {
        Card(
            modifier = modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 6.dp),
            shape = AhuShapes.Card,
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.tertiaryContainer,
            ),
            elevation = CardDefaults.cardElevation(0.dp),
        ) {
            Column(modifier = Modifier.padding(start = 16.dp, end = 8.dp, top = 12.dp, bottom = 8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Filled.NotificationsActive,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onTertiaryContainer,
                        modifier = Modifier.size(20.dp),
                    )
                    Spacer(Modifier.size(8.dp))
                    Text(
                        "课程提醒可能无法正常推送",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onTertiaryContainer,
                        modifier = Modifier.weight(1f),
                    )
                    IconButton(onClick = { dismissed = true }, modifier = Modifier.size(28.dp)) {
                        Icon(
                            Icons.Filled.Close,
                            contentDescription = "暂不",
                            tint = MaterialTheme.colorScheme.onTertiaryContainer,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                }

                val detail = when {
                    !notificationGranted && !exactAlarmGranted ->
                        "需开启「通知」与「闹钟和提醒」权限,否则上课提醒不会按时弹出。"
                    !notificationGranted ->
                        "需开启通知权限,否则上课提醒不会显示。"
                    else ->
                        "需开启「闹钟和提醒」权限,否则提醒时间可能不准。"
                }
                Text(
                    detail,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onTertiaryContainer,
                    modifier = Modifier.padding(top = 2.dp, end = 8.dp),
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                ) {
                    if (!notificationGranted) {
                        TextButton(onClick = {
                            // Android 13+ 走运行时弹窗;低版本不会进入(granted 恒 true)
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                            } else {
                                ReminderPermissions.openNotificationSettings(context)
                            }
                        }) { Text("开启通知") }
                    }
                    if (!exactAlarmGranted) {
                        TextButton(onClick = {
                            ReminderPermissions.openExactAlarmSettings(context)
                        }) { Text("开启精确提醒") }
                    }
                }
            }
        }
    }
}

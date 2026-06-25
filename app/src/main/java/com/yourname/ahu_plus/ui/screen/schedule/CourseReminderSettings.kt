package com.yourname.ahu_plus.ui.screen.schedule

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.yourname.ahu_plus.data.local.SessionManager
import com.yourname.ahu_plus.notification.CourseReminderScheduler
import com.yourname.ahu_plus.notification.ReminderPermissions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 课程提醒设置(2026-06-24)。自包含:直接读写 [SessionManager] + 申请权限 + 调度。
 *
 * - 总开关:开启时申请通知权限(13+)、引导精确闹钟(12+),并重排提醒;关闭时取消所有闹钟。
 * - 提前分钟:1-30 分钟可调,改后重排。
 *
 * 权限申请**仅在用户主动开启时触发**,不再启动即弹。
 */
@Composable
fun CourseReminderSettings(sessionManager: SessionManager) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var enabled by remember { mutableStateOf(sessionManager.getCourseReminderEnabled()) }
    var leadMinutes by remember { mutableIntStateOf(sessionManager.getCourseReminderLeadMinutes()) }
    var notifMissing by remember { mutableStateOf(!ReminderPermissions.hasNotificationPermission(context)) }
    var alarmMissing by remember { mutableStateOf(!ReminderPermissions.canScheduleExactAlarms(context)) }

    val notifLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted -> notifMissing = !granted }

    // 重排提醒(开关/分钟变更后调用)
    fun reschedule() {
        scope.launch { withContext(Dispatchers.IO) { CourseReminderScheduler.scheduleAll(context) } }
    }

    Column {
        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text("课程提醒", style = MaterialTheme.typography.bodyLarge)
                Text(
                    "每节课前提醒上课时间和教室",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.padding(horizontal = 4.dp))
            Switch(
                checked = enabled,
                onCheckedChange = { on ->
                    enabled = on
                    scope.launch {
                        sessionManager.saveCourseReminderEnabled(on)
                        if (on) {
                            // 开启:申请权限 + 重排
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && notifMissing) {
                                notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                            }
                            reschedule()
                        } else {
                            // 关闭:取消所有已注册闹钟
                            withContext(Dispatchers.IO) {
                                CourseReminderScheduler.cancelAll(context, sessionManager)
                            }
                        }
                    }
                },
            )
        }

        AnimatedVisibility(visible = enabled) {
            Column {
                // 提前分钟
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = androidx.compose.foundation.layout.Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("提前提醒", style = MaterialTheme.typography.bodyMedium)
                    Text(
                        "$leadMinutes 分钟",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
                Slider(
                    value = leadMinutes.toFloat(),
                    onValueChange = { leadMinutes = it.toInt() },
                    valueRange = 1f..30f,
                    steps = 28,
                    onValueChangeFinished = {
                        scope.launch {
                            sessionManager.saveCourseReminderLeadMinutes(leadMinutes)
                            reschedule()
                        }
                    },
                )

                // 权限缺失引导(仅开启提醒后展示)
                if (notifMissing) {
                    PermissionHintRow(
                        text = "通知权限未授予,提醒不会显示",
                        actionLabel = "去开启",
                        onAction = {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                            } else {
                                ReminderPermissions.openNotificationSettings(context)
                            }
                        },
                    )
                }
                if (alarmMissing) {
                    PermissionHintRow(
                        text = "「闹钟和提醒」未授权,提醒时间可能不准",
                        actionLabel = "去开启",
                        onAction = {
                            ReminderPermissions.openExactAlarmSettings(context)
                            // 用户从设置返回后刷新状态
                            alarmMissing = !ReminderPermissions.canScheduleExactAlarms(context)
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun PermissionHintRow(text: String, actionLabel: String, onAction: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error,
            modifier = Modifier.weight(1f),
        )
        TextButton(onClick = onAction) { Text(actionLabel) }
    }
}

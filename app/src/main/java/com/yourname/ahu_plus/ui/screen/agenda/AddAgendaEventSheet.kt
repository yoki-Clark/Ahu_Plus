package com.yourname.ahu_plus.ui.screen.agenda

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.yourname.ahu_plus.data.debug.DebugClock
import com.yourname.ahu_plus.data.model.task.UserTask
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone
import java.util.UUID

/** 提醒选项:label → 提前分钟数(null = 不提醒)。 */
private val REMINDER_OPTIONS = listOf(
    "不提醒" to null,
    "准点" to 0,
    "提前5分钟" to 5,
    "提前15分钟" to 15,
    "提前30分钟" to 30,
    "提前1小时" to 60,
)

/**
 * 新增 / 编辑手动日程的底部弹窗。
 *
 * 复用 [com.yourname.ahu_plus.ui.screen.schedule.components.AddHomeworkDialog] 的
 * DatePicker + TimePicker 交互套路,扩充为:标题 / 日期 / 全天 / 起止时间 / 地点 /
 * 提醒 / 备注,输出一个 [UserTask]。
 *
 * @param initial 非空 = 编辑模式(预填);null = 新增。默认日期取自 [defaultDate]。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddAgendaEventSheet(
    defaultDate: LocalDate,
    initial: UserTask? = null,
    onDismiss: () -> Unit,
    onConfirm: (UserTask) -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val zone: ZoneId = ZoneId.systemDefault()

    // 预填(编辑)或默认(新增)
    var title by remember { mutableStateOf(initial?.title ?: "") }
    var location by remember { mutableStateOf(initial?.location ?: "") }
    var note by remember { mutableStateOf(initial?.subtitle ?: "") }
    var allDay by remember { mutableStateOf(initial?.allDay ?: false) }

    val initStart = initial?.dueAt?.let { Calendar.getInstance().apply { timeInMillis = it } }
    var dateMillis by remember {
        mutableStateOf(
            initial?.dueAt ?: defaultDate.atStartOfDay(zone).toInstant().toEpochMilli()
        )
    }
    var startHour by remember { mutableStateOf(initStart?.get(Calendar.HOUR_OF_DAY) ?: 8) }
    var startMinute by remember { mutableStateOf(initStart?.get(Calendar.MINUTE) ?: 0) }

    val initEnd = initial?.endAt?.let { Calendar.getInstance().apply { timeInMillis = it } }
    var endHour by remember { mutableStateOf(initEnd?.get(Calendar.HOUR_OF_DAY) ?: 9) }
    var endMinute by remember { mutableStateOf(initEnd?.get(Calendar.MINUTE) ?: 0) }
    var hasEnd by remember { mutableStateOf(initial?.endAt != null) }

    var reminderMinutes by remember { mutableStateOf(initial?.reminderMinutes) }

    var showDatePicker by remember { mutableStateOf(false) }
    var showStartTime by remember { mutableStateOf(false) }
    var showEndTime by remember { mutableStateOf(false) }

    fun composeMillis(hour: Int, minute: Int): Long =
        Calendar.getInstance(TimeZone.getDefault()).apply {
            timeInMillis = dateMillis
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis

    if (showDatePicker) {
        val current = Instant.ofEpochMilli(dateMillis).atZone(zone).toLocalDate()
        DateWheelDialog(
            initial = current,
            onConfirm = { picked ->
                dateMillis = picked.atStartOfDay(zone).toInstant().toEpochMilli()
                showDatePicker = false
            },
            onDismiss = { showDatePicker = false },
        )
    }

    if (showStartTime) {
        TimeWheelDialog(
            initialHour = startHour,
            initialMinute = startMinute,
            title = "开始时间",
            onConfirm = { h, m -> startHour = h; startMinute = m; showStartTime = false },
            onDismiss = { showStartTime = false },
        )
    }

    if (showEndTime) {
        TimeWheelDialog(
            initialHour = endHour,
            initialMinute = endMinute,
            title = "结束时间",
            onConfirm = { h, m -> endHour = h; endMinute = m; hasEnd = true; showEndTime = false },
            onDismiss = { showEndTime = false },
        )
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text(
                text = if (initial == null) "添加日程" else "编辑日程",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
            )

            OutlinedTextField(
                value = title,
                onValueChange = { title = it.take(80) },
                label = { Text("标题") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            // 日期
            OutlinedButton(onClick = { showDatePicker = true }, modifier = Modifier.fillMaxWidth()) {
                Text("日期:" + fmtDate(dateMillis, zone))
            }

            // 全天开关
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text("全天", style = MaterialTheme.typography.bodyMedium)
                Switch(checked = allDay, onCheckedChange = { allDay = it })
            }

            // 起止时间(全天时隐藏)
            if (!allDay) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { showStartTime = true }, modifier = Modifier.weight(1f)) {
                        Text(String.format(Locale.getDefault(), "开始 %02d:%02d", startHour, startMinute))
                    }
                    OutlinedButton(onClick = { showEndTime = true }, modifier = Modifier.weight(1f)) {
                        Text(
                            if (hasEnd) String.format(Locale.getDefault(), "结束 %02d:%02d", endHour, endMinute)
                            else "结束时间"
                        )
                    }
                }
            }

            OutlinedTextField(
                value = location,
                onValueChange = { location = it.take(60) },
                label = { Text("地点(选填)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            OutlinedTextField(
                value = note,
                onValueChange = { note = it.take(300) },
                label = { Text("备注(选填)") },
                maxLines = 3,
                modifier = Modifier.fillMaxWidth(),
            )

            // 提醒
            Text("提醒", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                REMINDER_OPTIONS.forEach { (label, minutes) ->
                    FilterChip(
                        selected = reminderMinutes == minutes,
                        onClick = { reminderMinutes = minutes },
                        label = { Text(label) },
                    )
                }
            }

            Button(
                onClick = {
                    val start = composeMillis(if (allDay) 0 else startHour, if (allDay) 0 else startMinute)
                    val end = if (!allDay && hasEnd) composeMillis(endHour, endMinute) else null
                    onConfirm(
                        UserTask(
                            id = initial?.id ?: UUID.randomUUID().toString(),
                            title = title.trim(),
                            subtitle = note.trim().ifBlank { null },
                            dueAt = start,
                            completed = initial?.completed ?: false,
                            completedAt = initial?.completedAt,
                            createdAt = initial?.createdAt ?: DebugClock.nowMillis(),
                            endAt = end,
                            allDay = allDay,
                            location = location.trim().ifBlank { null },
                            reminderMinutes = reminderMinutes,
                        )
                    )
                    onDismiss()
                },
                enabled = title.isNotBlank(),
                modifier = Modifier.fillMaxWidth(),
            ) { Text(if (initial == null) "添加" else "保存") }
        }
    }
}

private fun fmtDate(millis: Long, zone: ZoneId): String {
    val d = java.time.Instant.ofEpochMilli(millis).atZone(zone).toLocalDate()
    val wd = listOf("一", "二", "三", "四", "五", "六", "日")[d.dayOfWeek.value - 1]
    return "%d-%02d-%02d 周%s".format(d.year, d.monthValue, d.dayOfMonth, wd)
}

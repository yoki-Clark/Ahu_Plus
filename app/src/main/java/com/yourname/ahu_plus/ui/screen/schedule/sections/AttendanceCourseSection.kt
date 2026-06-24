package com.yourname.ahu_plus.ui.screen.schedule.sections

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.yourname.ahu_plus.data.model.KqAttendanceRecord
import com.yourname.ahu_plus.ui.components.CollapsibleSection
import com.yourname.ahu_plus.ui.theme.AhuShapes

/**
 * 课程考勤记录 section (教务考勤联动)。
 *
 * section 内只展示当前周次的记录（紧凑视图）。
 * 顶部总览卡片可点击 → [onViewAll] 打开全量考勤列表。
 */
@Composable
fun AttendanceCourseSection(
    records: List<KqAttendanceRecord>,
    currentWeek: Int,
    modifier: Modifier = Modifier,
    expanded: Boolean? = null,
    onToggle: ((Boolean) -> Unit)? = null,
    onViewAll: () -> Unit = {},
) {
    val currentWeekRecords = records.filter { it.accountBean?.week == currentWeek }

    CollapsibleSection(
        title = "考勤记录",
        defaultExpanded = false,
        badge = if (records.isNotEmpty()) "${records.size}" else null,
        modifier = modifier,
        expanded = expanded,
        onToggle = onToggle,
    ) {
        if (records.isEmpty()) {
            Text(
                text = "暂无该课程的教务考勤记录。数据来自教务考勤系统，可能需要在「我的」页面先同步一次。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            // ── 考勤总览 (可点击查看全部) ──
            AttendanceSummaryRow(records = records, onClick = onViewAll)
            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
            // ── 当前周次记录 ──
            if (currentWeekRecords.isEmpty()) {
                Text(
                    text = "本周暂无该课程考勤记录",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    currentWeekRecords
                        .sortedByDescending { it.accountBean?.checkdate ?: "" }
                        .forEach { rec ->
                            AttendanceCourseRow(record = rec, isCurrentWeek = true)
                        }
                }
            }
        }
    }
}

/**
 * 考勤总览: 出勤 / 迟到 / 缺勤 次数 + 出勤率。可点击。
 */
@Composable
private fun AttendanceSummaryRow(
    records: List<KqAttendanceRecord>,
    onClick: () -> Unit,
) {
    val total = records.size
    val normal = records.count { it.classWaterBean?.status == 1 }
    val late = records.count { it.classWaterBean?.status == 2 }
    val absent = records.count { it.classWaterBean?.status == 3 }
    val rate = if (total > 0) (normal + late) * 100 / total else 0

    val green = Color(0xFF27AE60)
    val orange = Color(0xFFE67E22)
    val red = MaterialTheme.colorScheme.error

    Surface(
        shape = AhuShapes.LargeCard,
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(12.dp)) {
            // 出勤率 + 查看全部
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "出勤率",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    Text(
                        text = "查看全部",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Icon(
                        Icons.Filled.ChevronRight,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Bottom,
            ) {
                Text(
                    text = "共 $total 次考勤",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(
                        text = "$rate",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        color = when {
                            rate >= 90 -> green
                            rate >= 70 -> orange
                            else -> red
                        },
                    )
                    Text(
                        text = "%",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 2.dp),
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
            ) {
                SummaryItem(label = "出勤", count = normal, color = green)
                SummaryItem(label = "迟到", count = late, color = orange)
                SummaryItem(label = "缺勤", count = absent, color = red)
                SummaryItem(label = "总计", count = total, color = MaterialTheme.colorScheme.onSurface)
            }
        }
    }
}

@Composable
private fun SummaryItem(label: String, count: Int, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = "$count",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = color,
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

// ══════════════════════════════════════════════════════
// 逐条记录行 (section 内 + 全量列表共用)
// ══════════════════════════════════════════════════════

@Composable
fun AttendanceCourseRow(
    record: KqAttendanceRecord,
    isCurrentWeek: Boolean,
) {
    val status = record.classWaterBean?.status
    val isAbsent = status == 3
    val isNormal = status == 1
    val isLate = status == 2
    val typeColor = when {
        isAbsent -> MaterialTheme.colorScheme.error
        isLate -> Color(0xFFE67E22)
        isNormal -> Color(0xFF27AE60)
        else -> Color(0xFF888888)
    }
    val badgeText = when { isAbsent -> "缺"; isLate -> "迟"; isNormal -> "到"; else -> "?" }
    val statusLabel = when {
        isNormal && !record.classWaterBean?.operdate.isNullOrBlank() -> "已签到"
        isNormal -> "正常"
        isAbsent -> "缺勤"
        isLate -> "迟到"
        else -> "未知"
    }

    val checkDate = record.accountBean?.checkdate?.ifBlank { null }
    val periodText = record.accountBean?.jtNo?.ifBlank { null }?.let { "第" + it + "节" }
    val classroom = listOfNotNull(
        record.buildBean?.name?.ifBlank { null },
        record.roomBean?.roomnum?.ifBlank { null }
    ).joinToString("")
    val operDate = record.classWaterBean?.operdate?.ifBlank { null }
    val teacherName = record.teachNameList?.ifBlank { null }
    val weekText = record.accountBean?.week?.let { "第" + it + "周" }

    Surface(
        shape = AhuShapes.Card,
        color = if (isCurrentWeek) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
        else MaterialTheme.colorScheme.surface,
        tonalElevation = if (isCurrentWeek) 0.dp else 1.dp,
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(typeColor.copy(alpha = 0.14f)),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = badgeText,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = typeColor,
                )
            }

            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 10.dp, end = 4.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    val dateLine = listOfNotNull(checkDate, periodText).joinToString(" · ").ifBlank { "日期未知" }
                    Text(
                        text = dateLine,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = typeColor.copy(alpha = 0.14f),
                        modifier = Modifier.padding(start = 8.dp),
                    ) {
                        Text(
                            text = statusLabel,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Medium,
                            color = typeColor,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                        )
                    }
                }

                if (classroom.isNotBlank()) {
                    Text(
                        text = "教室: $classroom",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }

                val detailLine = listOfNotNull(
                    teacherName?.let { "教师: $it" },
                    operDate?.let { "签到: $it" },
                    weekText,
                ).joinToString(" · ")
                if (detailLine.isNotBlank()) {
                    Text(
                        text = detailLine,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }

                val timeRange = record.normalLetime ?: record.lateLetime ?: record.absenceLetime
                if (!timeRange.isNullOrBlank()) {
                    Text(
                        text = "考勤时段: $timeRange",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

// ══════════════════════════════════════════════════════
// 全量考勤列表 (点击"查看全部"后展示)
// ══════════════════════════════════════════════════════

/**
 * 某门课的完整考勤记录全屏页面。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AttendanceCourseDetailScreen(
    courseName: String,
    records: List<KqAttendanceRecord>,
    currentWeek: Int,
    onBack: () -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("$courseName · 考勤记录") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                ),
            )
        },
        containerColor = MaterialTheme.colorScheme.background,
    ) { innerPadding ->
        val sorted = records.sortedByDescending { it.accountBean?.checkdate ?: "" }
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .padding(innerPadding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            // 总览 (不可点击版本)
            item {
                AttendanceSummaryRowStatic(records = records)
            }
            item { HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp)) }
            items(
                count = sorted.size,
                key = { idx -> "${sorted[idx].accountBean?.checkdate}_${sorted[idx].accountBean?.jtNo}_$idx" },
            ) { idx ->
                val rec = sorted[idx]
                AttendanceCourseRow(
                    record = rec,
                    isCurrentWeek = rec.accountBean?.week == currentWeek,
                )
            }
            item { Spacer(Modifier.height(24.dp)) }
        }
    }
}

/** 不可点击的总览 (全屏页面用) */
@Composable
private fun AttendanceSummaryRowStatic(records: List<KqAttendanceRecord>) {
    val total = records.size
    val normal = records.count { it.classWaterBean?.status == 1 }
    val late = records.count { it.classWaterBean?.status == 2 }
    val absent = records.count { it.classWaterBean?.status == 3 }
    val rate = if (total > 0) (normal + late) * 100 / total else 0

    val green = Color(0xFF27AE60)
    val orange = Color(0xFFE67E22)
    val red = MaterialTheme.colorScheme.error

    Surface(
        shape = AhuShapes.LargeCard,
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Bottom,
            ) {
                Text(
                    text = "出勤率",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(
                        text = "$rate",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        color = when {
                            rate >= 90 -> green
                            rate >= 70 -> orange
                            else -> red
                        },
                    )
                    Text(
                        text = "%",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 2.dp),
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
            ) {
                SummaryItem(label = "出勤", count = normal, color = green)
                SummaryItem(label = "迟到", count = late, color = orange)
                SummaryItem(label = "缺勤", count = absent, color = red)
                SummaryItem(label = "总计", count = total, color = MaterialTheme.colorScheme.onSurface)
            }
        }
    }
}

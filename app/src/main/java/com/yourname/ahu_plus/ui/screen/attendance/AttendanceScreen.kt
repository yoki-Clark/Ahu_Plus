package com.yourname.ahu_plus.ui.screen.attendance

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.EventBusy
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
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
import com.yourname.ahu_plus.ui.screen.profile.EmptyBlock
import com.yourname.ahu_plus.ui.screen.profile.ErrorBlock
import com.yourname.ahu_plus.ui.screen.profile.LoadingBlock
import com.yourname.ahu_plus.ui.screen.profile.ProfileSection
import com.yourname.ahu_plus.ui.screen.profile.StudentInfoFooter
import com.yourname.ahu_plus.ui.screen.profile.AttendanceUiState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AttendanceScreen(
    uiState: AttendanceUiState,
    onBack: () -> Unit,
    onRefresh: () -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("考勤记录") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    IconButton(onClick = onRefresh) {
                        Icon(Icons.Filled.Refresh, contentDescription = "刷新")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            when {
                uiState.isLoading && uiState.summary == null -> {
                    item { LoadingBlock("正在加载考勤记录...") }
                }
                uiState.error != null && uiState.summary == null -> {
                    item { ErrorBlock(error = uiState.error, onRefresh = onRefresh) }
                }
                uiState.summary == null -> {
                    item {
                        ProfileSection {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 36.dp, horizontal = 20.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(14.dp)
                            ) {
                                Icon(
                                    Icons.Filled.EventBusy,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = "本地暂无考勤记录",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Medium
                                )
                                Text(
                                    text = "点击下方按钮从教务系统同步",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Button(
                                    onClick = onRefresh,
                                    enabled = !uiState.isLoading,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text("更新数据")
                                }
                            }
                        }
                    }
                }
                else -> {
                    val records = uiState.summary!!.records
                    if (records.isEmpty()) {
                        item { EmptyBlock("暂未查到缺勤记录") }
                    } else {
                        itemsIndexed(items = records, key = { idx, _ -> idx }) { _, rec ->
                            AttendanceRow(record = rec)
                        }
                    }
                    item {
                        StudentInfoFooter(
                            lastUpdatedAt = uiState.lastUpdatedAt,
                            isLoading = uiState.isLoading,
                            error = uiState.error,
                            onRefresh = onRefresh
                        )
                    }
                }
            }
            item { Spacer(modifier = Modifier.height(80.dp)) }
        }
    }
}

@Composable
private fun AttendanceRow(record: KqAttendanceRecord) {
    val status = record.classWaterBean?.status
    val isAbsent = status == 3
    val isNormal = status == 1
    val typeColor = when {
        isAbsent -> MaterialTheme.colorScheme.error
        isNormal -> Color(0xFF27AE60)
        else -> Color(0xFF888888)
    }
    val badgeText = when { isAbsent -> "缺"; isNormal -> "到"; else -> "?" }
    val statusLabel = when {
        isNormal && !record.classWaterBean?.operdate.isNullOrBlank() -> "已签到"
        isNormal -> "正常"
        isAbsent -> "缺勤"
        else -> "未知"
    }
    val courseName = record.subjectBean?.sName?.ifBlank { null }
        ?: record.subjectBean?.sSimple?.ifBlank { null } ?: "未命名课程"
    val teacherName = record.teachNameList?.ifBlank { null }
    val checkDate = record.accountBean?.checkdate?.ifBlank { null }
    val periodText = record.accountBean?.jtNo?.ifBlank { null }?.let { "第" + it + "节" }
    val classroom = listOfNotNull(
        record.buildBean?.name?.ifBlank { null },
        record.roomBean?.roomnum?.ifBlank { null }
    ).joinToString("")
    val operDate = record.classWaterBean?.operdate?.ifBlank { null }

    Card(
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier.size(40.dp).clip(CircleShape)
                    .background(typeColor.copy(alpha = 0.14f)),
                contentAlignment = Alignment.Center
            ) {
                Text(badgeText, style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold, color = typeColor)
            }
            Column(
                modifier = Modifier.weight(1f).padding(start = 12.dp),
                verticalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                Text(courseName, style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                val dateLine = listOfNotNull(checkDate, periodText, classroom.ifBlank { null })
                    .joinToString(" · ").ifBlank { "日期未知" }
                Text(dateLine, style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
                val weekText = record.accountBean?.week?.let { "第" + it + "周" }
                val detailLine = listOfNotNull(
                    teacherName?.let { "教师:" + it },
                    operDate?.let { "签到:" + it }, weekText
                ).joinToString(" · ")
                if (detailLine.isNotBlank()) {
                    Text(detailLine, style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                val timeRange = record.normalLetime ?: record.lateLetime ?: record.absenceLetime
                if (!timeRange.isNullOrBlank()) {
                    Text("考勤时段: " + timeRange, style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            Surface(shape = RoundedCornerShape(8.dp), color = typeColor.copy(alpha = 0.14f)) {
                Text(statusLabel, style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Medium, color = typeColor,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp))
            }
        }
    }
}

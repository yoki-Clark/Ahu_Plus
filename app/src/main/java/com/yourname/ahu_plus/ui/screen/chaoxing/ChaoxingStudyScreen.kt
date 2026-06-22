package com.yourname.ahu_plus.ui.screen.chaoxing

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.yourname.ahu_plus.data.model.CxStudyUiState
import com.yourname.ahu_plus.data.model.CxTaskProgress
import com.yourname.ahu_plus.data.model.CxTaskStatus
import com.yourname.ahu_plus.ui.components.AhuShapes

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChaoxingStudyScreen(
    studyState: CxStudyUiState,
    onStop: () -> Unit,
    onBack: () -> Unit,
) {
    val logListState = rememberLazyListState()

    LaunchedEffect(studyState.logs.size) {
        if (studyState.logs.isNotEmpty()) logListState.animateScrollToItem(studyState.logs.size - 1)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("学习进度") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    if (studyState.isRunning) {
                        IconButton(onClick = onStop) {
                            Icon(Icons.Filled.Close, contentDescription = "停止")
                        }
                    }
                },
            )
        }
    ) { innerPadding ->
        LazyColumn(
            state = logListState,
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp),
        ) {
            // ── 总览 ──────────────────────────────────
            item {
                Spacer(Modifier.height(8.dp))
                OverviewCard(studyState, onStop)
                Spacer(Modifier.height(16.dp))
            }

            // ── 当前任务 ──────────────────────────────
            if (studyState.currentTask != null) {
                item {
                    Text("当前任务", style = MaterialTheme.typography.titleSmall)
                    Spacer(Modifier.height(4.dp))
                    TaskRow(task = studyState.currentTask!!, showProgress = true)
                    Spacer(Modifier.height(16.dp))
                }
            }

            // ── 已完成 ────────────────────────────────
            if (studyState.completedTasks.isNotEmpty()) {
                item {
                    Text("已完成 (${studyState.completedTasks.size})", style = MaterialTheme.typography.titleSmall)
                    Spacer(Modifier.height(4.dp))
                }
                items(studyState.completedTasks.reversed()) { task ->
                    TaskRow(task = task, showProgress = false)
                }
                item { Spacer(Modifier.height(16.dp)) }
            }

            // ── 日志 ──────────────────────────────────
            if (studyState.logs.isNotEmpty()) {
                item {
                    Text("日志", style = MaterialTheme.typography.titleSmall)
                    Spacer(Modifier.height(4.dp))
                }
                items(studyState.logs) { log ->
                    Text(
                        text = log,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 1.dp),
                    )
                }
            }

            item { Spacer(Modifier.height(24.dp)) }
        }
    }
}

// ══════════════════════════════════════════════════════════════
//  总览卡片（朴素）
// ══════════════════════════════════════════════════════════════

@Composable
private fun OverviewCard(studyState: CxStudyUiState, onStop: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = AhuShapes.Card,
        elevation = CardDefaults.cardElevation(1.dp),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = if (studyState.isRunning) "学习中..." else "已完成",
                style = MaterialTheme.typography.titleSmall,
            )

            if (studyState.error != null) {
                Spacer(Modifier.height(8.dp))
                Text(studyState.error, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }

            if (studyState.isRunning) {
                Spacer(Modifier.height(12.dp))
                Button(
                    onClick = onStop,
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                    shape = AhuShapes.Card,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("停止")
                }
            }
        }
    }
}

// ══════════════════════════════════════════════════════════════
//  任务行（朴素）
// ══════════════════════════════════════════════════════════════

@Composable
private fun TaskRow(task: CxTaskProgress, showProgress: Boolean) {
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            // 状态标记
            val statusText = when (task.status) {
                CxTaskStatus.PENDING -> "·"
                CxTaskStatus.RUNNING -> "▸"
                CxTaskStatus.SUCCESS -> "✓"
                CxTaskStatus.FAILED -> "✗"
                CxTaskStatus.SKIPPED -> "→"
            }
            val statusColor = when (task.status) {
                CxTaskStatus.SUCCESS -> MaterialTheme.colorScheme.primary
                CxTaskStatus.FAILED -> MaterialTheme.colorScheme.error
                CxTaskStatus.RUNNING -> MaterialTheme.colorScheme.primary
                else -> MaterialTheme.colorScheme.onSurfaceVariant
            }
            Text(statusText, color = statusColor, modifier = Modifier.width(20.dp))
            Spacer(Modifier.width(4.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = task.job.name.ifBlank { task.job.type },
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = "${task.courseTitle} / ${task.chapterTitle}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (task.message.isNotBlank()) {
                Text(task.message, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        if (showProgress && task.status == CxTaskStatus.RUNNING) {
            Spacer(Modifier.height(4.dp))
            LinearProgressIndicator(progress = { task.progress }, modifier = Modifier.fillMaxWidth())
        }
    }
}

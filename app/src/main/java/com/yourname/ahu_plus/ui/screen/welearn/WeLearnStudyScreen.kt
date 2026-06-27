package com.yourname.ahu_plus.ui.screen.welearn

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.yourname.ahu_plus.data.model.WeLearnCourse

/**
 * WeLearn 单课程刷课屏 (2026-06-27)。
 *
 * 极简:正确率输入 + 大按钮(开始/停止) + 进度条 + 4 计数 + 日志滚动区。
 * 仿 ChaoxingStudyScreen 的"开始→进度→停止"骨架,但 UI 体量小一个数量级。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WeLearnStudyScreen(
    course: WeLearnCourse,
    viewModel: WeLearnViewModel,
    onBack: () -> Unit,
) {
    val ctx = LocalContext.current
    val state by viewModel.studyState.collectAsState()
    var accuracy by rememberSaveable { mutableStateOf("100") }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(course.name, fontWeight = FontWeight.Bold, maxLines = 1) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
            )
        },
    ) { pad ->
        Column(
            Modifier.padding(pad).fillMaxSize().padding(20.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // 课程元信息
            Card(shape = RoundedCornerShape(12.dp)) {
                Column(Modifier.padding(16.dp)) {
                    Text("当前完成度", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(4.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("${course.per}%", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.width(12.dp))
                        LinearProgressIndicator(progress = { course.per / 100f }, modifier = Modifier.weight(1f).height(8.dp))
                    }
                }
            }

            // 正确率输入
            OutlinedTextField(
                value = accuracy,
                onValueChange = { accuracy = it },
                label = { Text("正确率 (100 或 70,100)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                enabled = !state.isRunning,
            )

            // 大按钮
            Button(
                onClick = {
                    if (state.isRunning) viewModel.stopStudying(ctx)
                    else viewModel.startStudying(ctx, course.cid, accuracy.ifBlank { "100" })
                },
                modifier = Modifier.fillMaxWidth().height(56.dp),
                shape = RoundedCornerShape(12.dp),
                colors = if (state.isRunning) ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                else ButtonDefaults.buttonColors(),
            ) {
                Icon(
                    if (state.isRunning) Icons.Filled.Stop else Icons.Filled.PlayArrow,
                    contentDescription = null,
                )
                Spacer(Modifier.width(8.dp))
                Text(if (state.isRunning) "停止刷课" else "开始刷课", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
            }

            // 进度统计
            if (state.totalCount > 0 || state.isRunning) {
                Card(shape = RoundedCornerShape(12.dp)) {
                    Column(Modifier.padding(16.dp)) {
                        val done = state.completedCount + state.partialCount
                        Text(
                            if (state.totalCount > 0) "进度 $done/${state.totalCount}"
                            else "准备中…",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                        )
                        Spacer(Modifier.height(8.dp))
                        LinearProgressIndicator(
                            progress = { state.progress },
                            modifier = Modifier.fillMaxWidth().height(8.dp),
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "✓${state.completedCount} △${state.partialCount} ✗${state.failedCount} 跳过${state.skippedCount}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        if (state.currentScoLocation.isNotBlank()) {
                            Spacer(Modifier.height(8.dp))
                            Text(
                                "当前: ${state.currentUnitName} → ${state.currentScoLocation}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }

            // 日志
            if (state.logs.isNotEmpty()) {
                Text("日志", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                    shape = RoundedCornerShape(8.dp),
                ) {
                    Column(
                        Modifier.padding(12.dp).heightIn(max = 240.dp).verticalScroll(rememberScrollState()),
                    ) {
                        state.logs.takeLast(30).forEach { line ->
                            Text(
                                line,
                                style = MaterialTheme.typography.bodySmall,
                                fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                            )
                        }
                    }
                }
            }
        }
    }
}
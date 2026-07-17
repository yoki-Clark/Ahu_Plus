package com.ahu_plus.ui.screen.chaoxing

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Assignment
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import com.ahu_plus.ui.components.AhuPullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ahu_plus.data.model.CxHomeworkDetailState
import com.ahu_plus.data.model.CxHomeworkItem
import com.ahu_plus.data.model.CxHomeworkListState
import com.ahu_plus.data.model.CxQuestion

/**
 * 课程作业 Tab 列表。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeworkTabContent(
    viewModel: ChaoxingViewModel,
    loginState: CxLoginState,
    onHomeworkClick: (CxHomeworkItem) -> Unit,
) {
    val homeworkState by viewModel.homeworkState.collectAsStateWithLifecycle()
    var isRefreshing by remember { mutableStateOf(false) }

    LaunchedEffect(loginState.isLoggedIn) {
        if (loginState.isLoggedIn && homeworkState.homework.isEmpty() && !homeworkState.isLoading) {
            viewModel.loadHomework()
        }
    }

    LaunchedEffect(homeworkState.isLoading) {
        if (!homeworkState.isLoading) isRefreshing = false
    }

    AhuPullToRefreshBox(
        isRefreshing = isRefreshing || homeworkState.isLoading,
        onRefresh = {
            isRefreshing = true
            viewModel.refreshHomework()
        },
        modifier = Modifier.fillMaxSize(),
    ) {
        when {
            !homeworkState.isLoading && homeworkState.homework.isEmpty() && homeworkState.error == null -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.AutoMirrored.Filled.Assignment, null,
                            Modifier.size(64.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f))
                        Spacer(Modifier.height(12.dp))
                        Text("暂无课程作业", style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.height(4.dp))
                        Text("下拉刷新获取最新作业", style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f))
                    }
                }
            }

            homeworkState.error != null && homeworkState.homework.isEmpty() -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Filled.ErrorOutline, null, Modifier.size(48.dp),
                            tint = MaterialTheme.colorScheme.error)
                        Spacer(Modifier.height(12.dp))
                        Text(homeworkState.error ?: "加载失败", style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.error)
                        Spacer(Modifier.height(12.dp))
                        Button(onClick = { viewModel.loadHomework() }) { Text("重试") }
                    }
                }
            }

            else -> {
                val grouped = remember(homeworkState.homework) {
                    homeworkState.homework.groupBy { it.courseName }
                }
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    grouped.forEach { (courseName, items) ->
                        item(key = "hdr_$courseName") {
                            Text(courseName, style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(top = 12.dp, bottom = 4.dp),
                                maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        items(items, key = { it.workId }) { work ->
                            HomeworkCard(work = work, onClick = { onHomeworkClick(work) })
                        }
                    }
                }
            }
        }
    }
}

// ══════════════════════════════════════════════════════════════

@Composable
private fun HomeworkCard(work: CxHomeworkItem, onClick: () -> Unit) {
    val statusColor = when {
        work.status.contains("完成") -> Color(0xFF4CAF50)
        work.status.contains("批阅") -> Color(0xFFFF9800)
        work.status.contains("未交") -> Color(0xFFF44336)
        work.status.contains("待做") -> Color(0xFFF44336)
        else -> Color(0xFF9E9E9E)
    }
    val statusIcon = when {
        work.status.contains("完成") -> Icons.Filled.CheckCircle
        work.status.contains("批阅") -> Icons.Filled.Schedule
        else -> Icons.AutoMirrored.Filled.Assignment
    }

    Card(
        onClick = onClick, modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(statusIcon, null, tint = statusColor, modifier = Modifier.size(24.dp))
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(work.name, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium,
                    maxLines = 2, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.height(2.dp))
                Text(work.status, style = MaterialTheme.typography.labelSmall,
                    color = statusColor, fontWeight = FontWeight.SemiBold)
            }
            Icon(Icons.Filled.ChevronRight, null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                modifier = Modifier.size(20.dp))
        }
    }
}

// ══════════════════════════════════════════════════════════════
//  作业详情（只读查看 + 缓存 + 下拉刷新）
// ══════════════════════════════════════════════════════════════

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeworkDetailScreen(
    viewModel: ChaoxingViewModel,
    homework: CxHomeworkItem,
    onBack: () -> Unit,
) {
    val detailState by viewModel.homeworkDetailState.collectAsStateWithLifecycle()
    var isRefreshing by remember { mutableStateOf(false) }

    LaunchedEffect(homework) {
        viewModel.loadHomeworkDetail(homework)
    }

    LaunchedEffect(detailState.isLoading) {
        if (!detailState.isLoading) isRefreshing = false
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(homework.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = {
                    IconButton(onClick = {
                        viewModel.clearHomeworkDetail()
                        onBack()
                    }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回")
                    }
                },
            )
        },
    ) { padding ->
        AhuPullToRefreshBox(
            isRefreshing = isRefreshing,
            onRefresh = {
                isRefreshing = true
                viewModel.refreshHomeworkDetail(homework)
            },
            modifier = Modifier.fillMaxSize().padding(padding),
        ) {
            Column(
                Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
            ) {
                // 状态栏
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    val statusColor = when {
                        homework.status.contains("完成") -> Color(0xFF4CAF50)
                        homework.status.contains("批阅") -> Color(0xFFFF9800)
                        else -> Color(0xFFF44336)
                    }
                    Card(
                        colors = CardDefaults.cardColors(containerColor = statusColor.copy(alpha = 0.12f)),
                        shape = RoundedCornerShape(6.dp),
                    ) {
                        Text(
                            homework.status,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                            color = statusColor,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                    Spacer(Modifier.weight(1f))
                    Text(homework.courseName, style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }

                HorizontalDivider(Modifier.padding(horizontal = 16.dp))
                Spacer(Modifier.height(8.dp))

                when {
                    detailState.isLoading && detailState.workData == null -> {
                        Box(Modifier.fillMaxWidth().height(300.dp), contentAlignment = Alignment.Center) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                CircularProgressIndicator()
                                Spacer(Modifier.height(12.dp))
                                Text("加载题目中...", style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                    }

                    detailState.error != null && detailState.workData == null -> {
                        Box(Modifier.fillMaxWidth().height(300.dp), contentAlignment = Alignment.Center) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(Icons.Filled.ErrorOutline, null, Modifier.size(40.dp),
                                    tint = MaterialTheme.colorScheme.error)
                                Spacer(Modifier.height(8.dp))
                                Text(detailState.error ?: "加载失败", style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.error)
                                Spacer(Modifier.height(8.dp))
                                Button(onClick = { viewModel.loadHomeworkDetail(homework) }) { Text("重试") }
                            }
                        }
                    }

                    detailState.workData != null -> {
                        ReadOnlyQuestionList(questions = detailState.workData!!.questions)
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "提交功能暂未开放，仅供查看",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 16.dp),
                        )
                        Spacer(Modifier.height(32.dp))
                    }
                }
            }
        }
    }
}

/** 只读题目列表（查看模式） */
@Composable
private fun ReadOnlyQuestionList(questions: List<CxQuestion>) {
    Column(Modifier.padding(horizontal = 16.dp)) {
        Text(
            "共 ${questions.size} 道题",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary,
        )
        Spacer(Modifier.height(8.dp))

        questions.forEachIndexed { index, q ->
            Card(
                Modifier.fillMaxWidth().padding(vertical = 4.dp),
                shape = RoundedCornerShape(8.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                ),
            ) {
                Column(Modifier.padding(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("${index + 1}.", style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(4.dp))
                        val typeLabel = when (q.type) {
                            "single" -> "[单选题]"
                            "multiple" -> "[多选题]"
                            "judgement" -> "[判断题]"
                            "completion" -> "[填空题]"
                            "shortanswer" -> "[简答题]"
                            else -> if (q.type == "unknown") "" else "[${q.type}]"
                        }
                        if (typeLabel.isNotBlank()) {
                            Text(typeLabel, style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary)
                        }
                    }
                    Spacer(Modifier.height(4.dp))
                    Text(q.title, style = MaterialTheme.typography.bodyMedium)

                    if (q.options.isNotBlank()) {
                        Spacer(Modifier.height(6.dp))
                        q.options.split("\n").forEach { opt ->
                            Text(opt, style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(start = 8.dp, top = 2.dp))
                        }
                    }
                }
            }
        }
    }
}

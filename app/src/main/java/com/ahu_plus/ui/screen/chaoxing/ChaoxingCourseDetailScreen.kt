package com.ahu_plus.ui.screen.chaoxing

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.PlayArrow
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ahu_plus.data.model.CxChapter
import com.ahu_plus.data.model.CxCourse
import com.ahu_plus.data.model.CxJob
import com.ahu_plus.ui.theme.AhuShapes

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChaoxingCourseDetailScreen(
    viewModel: ChaoxingViewModel,
    course: CxCourse,
    onBack: () -> Unit,
    onStartStudy: (CxCourse) -> Unit,
) {
    val detailState by viewModel.detailState.collectAsStateWithLifecycle()

    LaunchedEffect(course) { viewModel.loadCourseDetail(course) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(course.title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    IconButton(onClick = { onStartStudy(course) }) {
                        Icon(Icons.Filled.PlayArrow, contentDescription = "学习本课")
                    }
                },
            )
        }
    ) { innerPadding ->
        when {
            detailState.isLoading -> {
                Box(Modifier.fillMaxSize().padding(innerPadding), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }
            detailState.error != null -> {
                Box(Modifier.fillMaxSize().padding(innerPadding), contentAlignment = Alignment.Center) {
                    Text(detailState.error!!, color = MaterialTheme.colorScheme.error)
                }
            }
            else -> {
                val points = detailState.coursePoints?.points ?: emptyList()
                val finished = points.count { it.hasFinished }
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                        .padding(horizontal = 16.dp),
                ) {
                    item { Spacer(Modifier.height(8.dp)) }
                    // 统计行
                    item {
                        Text(
                            "${points.size} 个章节 · $finished 已完成",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.height(12.dp))
                    }
                    // 章节列表
                    items(points, key = { it.id }) { chapter ->
                        ChapterRow(
                            chapter = chapter,
                            jobs = detailState.chapterJobs[chapter.id],
                            isLoading = detailState.loadingChapters.contains(chapter.id),
                            onExpand = { viewModel.loadChapterJobs(course, chapter) },
                        )
                    }
                    item { Spacer(Modifier.height(16.dp)) }
                }
            }
        }
    }
}

// ══════════════════════════════════════════════════════════════
//  章节行（朴素设计）
// ══════════════════════════════════════════════════════════════

@Composable
private fun ChapterRow(
    chapter: CxChapter,
    jobs: List<CxJob>?,
    isLoading: Boolean,
    onExpand: () -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }

    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable {
                    if (!expanded && jobs == null) onExpand()
                    expanded = !expanded
                }
                .padding(vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // 状态圆点
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(
                        when {
                            chapter.needUnlock -> MaterialTheme.colorScheme.outlineVariant
                            chapter.hasFinished -> Color(0xFF4CAF50)
                            else -> MaterialTheme.colorScheme.primary
                        }
                    )
            )
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = chapter.title,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (chapter.hasFinished)
                        MaterialTheme.colorScheme.onSurfaceVariant
                    else MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = buildString {
                        append("${chapter.jobCount} 个任务点")
                        if (chapter.hasFinished) append(" · 已完成")
                        if (chapter.needUnlock) append(" · 需解锁")
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        // 展开的任务点
        AnimatedVisibility(visible = expanded) {
            Column(modifier = Modifier.padding(start = 20.dp, bottom = 8.dp)) {
                if (isLoading) {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                } else if (jobs != null) {
                    if (jobs.isEmpty()) {
                        Text(
                            if (chapter.hasFinished || chapter.jobCount > 0) "任务已完成" else "暂无任务点",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    jobs.forEach { job ->
                        JobRow(job)
                    }
                }
            }
        }

        HorizontalDivider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
    }
}

// ══════════════════════════════════════════════════════════════
//  任务点行（朴素设计）
// ══════════════════════════════════════════════════════════════

@Composable
private fun JobRow(job: CxJob) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // 类型标签
        val typeLabel = when (job.type) {
            "video" -> "视频"
            "document" -> "文档"
            "workid" -> "测验"
            "read" -> "阅读"
            "live" -> "直播"
            else -> job.type
        }
        Text(
            text = typeLabel,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(32.dp),
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = job.name.ifBlank { typeLabel },
            style = MaterialTheme.typography.bodySmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}
